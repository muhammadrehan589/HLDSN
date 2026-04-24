package com.example.hldsn.sos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class WifiDirectTransport {

    private static final String TAG = "WifiDirectTx";

    interface Callback {
        void onFrameReceived(byte[] frame);
        void onStatus(String status);

        default void onPeerLinksChanged(int activeConnections) {
            // Optional hook for callers that want topology updates.
        }
    }

    private static final int PORT = 47831;
    private static final int CONNECT_TIMEOUT_MS = 2500;
    private static final long CONNECT_RESULT_TIMEOUT_MS = 12000L;
    private static final long CONNECT_REQUEST_COOLDOWN_MS = 7000L;
    private static final long REDISCOVER_DELAY_MS = 3000L;
    private static final long REDISCOVER_DISABLED_DELAY_MS = 15000L;
    private static final long PEER_POLL_DELAY_MS = 900L;
    private static final long IDLE_DISCOVER_LOOP_MS = 8000L;
    private static final long STATE_SNAPSHOT_INTERVAL_MS = 5000L;
    private static final int MAX_FRAME_BYTES = 1024;
    private static final int MAX_PENDING_FRAMES = 96;

    private final Context appContext;
    private final Callback callback;
    private final Handler mainHandler;
    private final Object lock = new Object();
    private final Map<String, PeerConnection> connections = new ConcurrentHashMap<>();
    private final ArrayDeque<byte[]> pendingFrames = new ArrayDeque<>();
    // Single-threaded writer prevents socket I/O from running on the main thread.
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean connectingToOwner = new AtomicBoolean(false);
    private final AtomicBoolean p2pConnectInFlight = new AtomicBoolean(false);
    private final Set<String> pendingConnectAddresses = ConcurrentHashMap.newKeySet();
    private final Set<String> recentlyConnectedAddresses = ConcurrentHashMap.newKeySet();

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private BroadcastReceiver receiver;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private boolean started;
    private boolean wifiP2pEnabled;
    private boolean discoveryInProgress;
    private boolean groupFormed;
    private boolean groupOwner;
    private String groupOwnerAddress;
    private long lastP2pDisabledStatusAtMs;
    private long lastConnectAttemptMs;
    private String lastConnectAttemptDevice;
    private Runnable rediscoverRunnable;
    private Runnable connectWatchdogRunnable;
    private final Runnable stateSnapshotRunnable = new Runnable() {
        @Override
        public void run() {
            if (!started) {
                return;
            }
            logStateSnapshot("periodic");
            mainHandler.postDelayed(this, STATE_SNAPSHOT_INTERVAL_MS);
        }
    };

    WifiDirectTransport(Context context, Callback callback) {
        this.appContext = context.getApplicationContext();
        this.callback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    void start() {
        if (started) {
            Log.d(TAG, "WFD_START skipped: already started");
            return;
        }
        started = true;
        // Some devices don't emit an immediate WIFI_P2P_STATE_CHANGED broadcast after startup.
        // Start optimistic and let discover/connect failures correct this state.
        wifiP2pEnabled = true;
        Log.i(TAG, "WFD_START begin");
        manager = (WifiP2pManager) appContext.getSystemService(Context.WIFI_P2P_SERVICE);
        if (manager == null) {
            Log.w(TAG, "WFD_START failed: manager null (unsupported)");
            callback.onStatus("Wi-Fi Direct unsupported");
            return;
        }
        channel = manager.initialize(appContext, Looper.getMainLooper(), this::onChannelDisconnected);
        Log.i(TAG, "WFD_START initialized channel=" + (channel != null));
        registerReceiver();
        discoverPeers();
        // Proactively check for existing group if we restarted with P2P already linked
        if (manager != null && channel != null) {
            manager.requestConnectionInfo(channel, this::onConnectionInfoAvailable);
        }
        scheduleRediscovery("start-watchdog", REDISCOVER_DELAY_MS);
        mainHandler.removeCallbacks(stateSnapshotRunnable);
        logStateSnapshot("start");
        mainHandler.postDelayed(stateSnapshotRunnable, STATE_SNAPSHOT_INTERVAL_MS);
    }

    void stop() {
        Log.i(TAG, "WFD_STOP begin connections=" + connections.size());
        started = false;
        unregisterReceiver();
        closeServer();
        for (PeerConnection connection : connections.values()) {
            connection.close();
        }
        connections.clear();
        synchronized (lock) {
            pendingFrames.clear();
        }
        if (rediscoverRunnable != null) {
            mainHandler.removeCallbacks(rediscoverRunnable);
            rediscoverRunnable = null;
        }
        p2pConnectInFlight.set(false);
        pendingConnectAddresses.clear();
        recentlyConnectedAddresses.clear();
        discoveryInProgress = false;
        groupFormed = false;
        groupOwner = false;
        groupOwnerAddress = null;
        if (connectWatchdogRunnable != null) {
            mainHandler.removeCallbacks(connectWatchdogRunnable);
            connectWatchdogRunnable = null;
        }
        mainHandler.removeCallbacks(stateSnapshotRunnable);
    }

    boolean isReady() {
        boolean ready = started && !connections.isEmpty();
        if (!ready) {
            Log.d(TAG, "WFD_READY=false started=" + started + " connections=" + connections.size());
        }
        return ready;
    }

    void sendFrame(byte[] frame) {
        ioExecutor.execute(() -> sendFrameInternal(frame));
    }

    private void sendFrameInternal(byte[] frame) {
        if (frame == null || frame.length == 0) {
            Log.d(TAG, "WFD_SEND skip empty frame");
            return;
        }

        boolean sent = false;
        for (PeerConnection connection : connections.values()) {
            if (connection.send(frame)) {
                sent = true;
            }
        }

        if (sent) {
            Log.d(TAG, "WFD_SEND delivered bytes=" + frame.length + " peers=" + connections.size());
            return;
        }

        synchronized (lock) {
            byte[] last = pendingFrames.peekLast();
            if (last != null && java.util.Arrays.equals(last, frame)) {
                Log.d(TAG, "WFD_SEND coalesced duplicate buffered frame bytes=" + frame.length);
                return;
            }
            if (pendingFrames.size() >= MAX_PENDING_FRAMES) {
                pendingFrames.removeFirst();
            }
            pendingFrames.addLast(frame);
        }
        Log.d(TAG, "WFD_SEND buffered bytes=" + frame.length + " pending=" + pendingFrames.size());
        scheduleRediscovery("buffered-send", 800L);
    }

    @SuppressLint("MissingPermission")
    void discoverPeers() {
        if (discoveryInProgress || p2pConnectInFlight.get()) {
            return;
        }
        // Keep client sessions stable; clients can relay through their GO without reconnect churn.
        if (groupFormed && !groupOwner && !connections.isEmpty()) {
            scheduleRediscovery("client-stable-scan", IDLE_DISCOVER_LOOP_MS);
            return;
        }
        boolean hasPerm = hasWifiDirectPermission();
        if (!started || manager == null || channel == null || !wifiP2pEnabled || !hasPerm) {
            Log.d(TAG, "WFD_DISCOVER skip started=" + started
                    + " manager=" + (manager != null)
                    + " channel=" + (channel != null)
                    + " p2pEnabled=" + wifiP2pEnabled
                    + " permission=" + hasPerm);
            if (!wifiP2pEnabled) {
                long now = System.currentTimeMillis();
                if (now - lastP2pDisabledStatusAtMs > 20_000L) {
                    lastP2pDisabledStatusAtMs = now;
                    callback.onStatus("Wi-Fi Direct disabled, waiting for system enable");
                }
                scheduleRediscovery("discover-skip-disabled", REDISCOVER_DISABLED_DELAY_MS);
            } else {
                scheduleRediscovery("discover-skip", REDISCOVER_DELAY_MS);
            }
            return;
        }
        try {
            Log.d(TAG, "WFD_DISCOVER start");
            discoveryInProgress = true;
            manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "WFD_DISCOVER success (waiting peers changed callback)");
                    discoveryInProgress = false;
                    // Some OEM stacks skip PEERS_CHANGED broadcasts intermittently; request peers directly.
                    mainHandler.postDelayed(() -> {
                        if (!started || p2pConnectInFlight.get() || !connections.isEmpty()) {
                            return;
                        }
                        requestPeers();
                    }, PEER_POLL_DELAY_MS);

                    // Keep a low-frequency discovery loop when idle to recover from stale peer state.
                    if (connections.isEmpty()) {
                        scheduleRediscovery("discover-idle-loop", IDLE_DISCOVER_LOOP_MS);
                    }
                }

                @Override
                public void onFailure(int reason) {
                    Log.w(TAG, "WFD_DISCOVER failure reason=" + reason + " label=" + p2pReasonLabel(reason));
                    discoveryInProgress = false;
                    callback.onStatus("Wi-Fi Direct discovery failed: " + reason);
                    scheduleRediscovery("discover-fail", REDISCOVER_DELAY_MS);
                }
            });
        } catch (SecurityException sec) {
            discoveryInProgress = false;
            Log.w(TAG, "WFD_DISCOVER security exception", sec);
            callback.onStatus("Wi-Fi Direct permission missing");
        }
    }

    private void onChannelDisconnected() {
        Log.w(TAG, "WFD_CHANNEL disconnected");
        callback.onStatus("Wi-Fi Direct channel disconnected");
        scheduleRediscovery("channel-disconnected", REDISCOVER_DELAY_MS);
    }

    private void registerReceiver() {
        if (receiver != null) {
            Log.d(TAG, "WFD_RX already registered");
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                    int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, WifiP2pManager.WIFI_P2P_STATE_DISABLED);
                    wifiP2pEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED;
                    Log.i(TAG, "WFD_STATE p2pEnabled=" + wifiP2pEnabled + " rawState=" + state);
                    if (wifiP2pEnabled) {
                        discoverPeers();
                    } else {
                        p2pConnectInFlight.set(false);
                        discoveryInProgress = false;
                        if (connectWatchdogRunnable != null) {
                            mainHandler.removeCallbacks(connectWatchdogRunnable);
                            connectWatchdogRunnable = null;
                        }
                        scheduleRediscovery("p2p-disabled", REDISCOVER_DISABLED_DELAY_MS);
                    }
                } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                    Log.d(TAG, "WFD_PEERS_CHANGED");
                    requestPeers();
                } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                    Log.d(TAG, "WFD_CONN_CHANGED");
                    handleConnectionChanged(intent);
                }
            }
        };
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        Log.d(TAG, "WFD_RX registered");
    }

    private void unregisterReceiver() {
        if (receiver == null) {
            return;
        }
        try {
            appContext.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver may already be unregistered.
        }
        receiver = null;
        Log.d(TAG, "WFD_RX unregistered");
    }

    @SuppressLint("MissingPermission")
    private void requestPeers() {
        boolean hasPerm = hasWifiDirectPermission();
        if (manager == null || channel == null || !hasPerm) {
            Log.d(TAG, "WFD_REQUEST_PEERS skip manager=" + (manager != null)
                    + " channel=" + (channel != null)
                    + " permission=" + hasPerm);
            return;
        }
        if (p2pConnectInFlight.get()) {
            return;
        }
        try {
            Log.d(TAG, "WFD_REQUEST_PEERS start");
            manager.requestPeers(channel, this::connectToBestPeer);
        } catch (SecurityException sec) {
            Log.w(TAG, "WFD_REQUEST_PEERS security exception", sec);
            callback.onStatus("Wi-Fi Direct peer permission missing");
        }
    }

    @SuppressLint("MissingPermission")
    private void connectToBestPeer(WifiP2pDeviceList peerList) {
        boolean hasPerm = hasWifiDirectPermission();
        if (!started || manager == null || channel == null || !hasPerm) {
            Log.d(TAG, "WFD_CONNECT skip started=" + started
                    + " manager=" + (manager != null)
                    + " channel=" + (channel != null)
                    + " permission=" + hasPerm);
            return;
        }
        if (groupFormed && !groupOwner) {
            // Client role should keep the existing link instead of trying to reshuffle topology.
            Log.d(TAG, "WFD_CONNECT skip: client role active peers=" + connections.size());
            flushPendingFrames();
            scheduleRediscovery("client-role-idle", IDLE_DISCOVER_LOOP_MS);
            return;
        }
        if (p2pConnectInFlight.get()) {
            return;
        }

        List<WifiP2pDevice> devices = new ArrayList<>(peerList.getDeviceList());
        Log.d(TAG, "WFD_CONNECT peersFound=" + devices.size());
        if (devices.isEmpty()) {
            scheduleRediscovery("no-peers", REDISCOVER_DELAY_MS);
            return;
        }

        WifiP2pDevice selected = selectBestPeer(devices);
        if (selected == null) {
            // Check if any peer is already CONNECTED but we haven't formed our transport group yet.
            // This happens on app restarts while the system P2P link is still active.
            for (WifiP2pDevice device : devices) {
                if (device.status == WifiP2pDevice.CONNECTED && !groupFormed) {
                    Log.i(TAG, "WFD_CONNECT: Peer " + device.deviceAddress + " already connected at P2P level, requesting info...");
                    manager.requestConnectionInfo(channel, this::onConnectionInfoAvailable);
                    return;
                }
            }
            Log.d(TAG, "WFD_CONNECT skip: no AVAILABLE peer (likely already INVITED/CONNECTED)");
            scheduleRediscovery("no-available-peer", REDISCOVER_DELAY_MS);
            return;
        }
        if (selected.deviceAddress == null || selected.deviceAddress.trim().isEmpty()) {
            scheduleRediscovery("peer-address-missing", REDISCOVER_DELAY_MS);
            return;
        }
        if (pendingConnectAddresses.contains(selected.deviceAddress)) {
            Log.d(TAG, "WFD_CONNECT skip: already connecting to " + selected.deviceAddress);
            scheduleRediscovery("already-connecting", REDISCOVER_DELAY_MS);
            return;
        }
        if (recentlyConnectedAddresses.contains(selected.deviceAddress)) {
            Log.d(TAG, "WFD_CONNECT skip: already connected/recently-connected " + selected.deviceAddress);
            scheduleRediscovery("already-connected-peer", IDLE_DISCOVER_LOOP_MS);
            return;
        }
        long now = System.currentTimeMillis();
        if (selected.deviceAddress != null
                && selected.deviceAddress.equals(lastConnectAttemptDevice)
                && (now - lastConnectAttemptMs) < CONNECT_REQUEST_COOLDOWN_MS) {
            Log.d(TAG, "WFD_CONNECT cooldown active for " + selected.deviceAddress);
            return;
        }
        Log.i(TAG, "WFD_CONNECT selecting device=" + selected.deviceAddress + " status=" + selected.status);
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = selected.deviceAddress;
        config.groupOwnerIntent = 7;
        lastConnectAttemptDevice = selected.deviceAddress;
        lastConnectAttemptMs = now;
        p2pConnectInFlight.set(true);
        pendingConnectAddresses.add(selected.deviceAddress);

        try {
            manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "WFD_CONNECT success request accepted device=" + selected.deviceAddress);
                    callback.onStatus("Wi-Fi Direct peer connected");
                    armConnectWatchdog(selected.deviceAddress);
                    scheduleRediscovery("connect-accepted-watchdog", 8000L);
                }

                @Override
                public void onFailure(int reason) {
                    clearConnectWatchdog();
                    p2pConnectInFlight.set(false);
                    pendingConnectAddresses.remove(selected.deviceAddress);
                    Log.w(TAG, "WFD_CONNECT failure device=" + selected.deviceAddress
                            + " reason=" + reason + " label=" + p2pReasonLabel(reason));
                    callback.onStatus("Wi-Fi Direct connect failed: " + reason);
                    scheduleRediscovery("connect-fail", REDISCOVER_DELAY_MS);
                }
            });
        } catch (SecurityException sec) {
            clearConnectWatchdog();
            p2pConnectInFlight.set(false);
            pendingConnectAddresses.remove(selected.deviceAddress);
            Log.w(TAG, "WFD_CONNECT security exception", sec);
            callback.onStatus("Wi-Fi Direct connect permission missing");
        }
    }

    private WifiP2pDevice selectBestPeer(List<WifiP2pDevice> devices) {
        if (devices == null || devices.isEmpty()) {
            return null;
        }
        for (WifiP2pDevice device : devices) {
            if (device == null || device.deviceAddress == null) {
                continue;
            }
            if (device.status == WifiP2pDevice.CONNECTED) {
                recentlyConnectedAddresses.add(device.deviceAddress);
                continue;
            }
            if (device.status != WifiP2pDevice.AVAILABLE) {
                continue;
            }
            if (recentlyConnectedAddresses.contains(device.deviceAddress)
                    || pendingConnectAddresses.contains(device.deviceAddress)) {
                continue;
            }
            return device;
        }
        return null;
    }

    @SuppressLint("MissingPermission")
    private void handleConnectionChanged(Intent intent) {
        boolean hasPerm = hasWifiDirectPermission();
        if (manager == null || channel == null || !hasPerm) {
            Log.d(TAG, "WFD_CONN_INFO skip manager=" + (manager != null)
                    + " channel=" + (channel != null)
                    + " permission=" + hasPerm);
            return;
        }
        NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
        if (networkInfo == null || !networkInfo.isConnected()) {
            Log.d(TAG, "WFD_CONN_INFO network not connected");
            clearConnectWatchdog();
            p2pConnectInFlight.set(false);
            pendingConnectAddresses.clear();
            groupFormed = false;
            groupOwner = false;
            groupOwnerAddress = null;
            if (connections.isEmpty()) {
                recentlyConnectedAddresses.clear();
            }
            scheduleRediscovery("network-not-connected", REDISCOVER_DELAY_MS);
            return;
        }
        clearConnectWatchdog();
        p2pConnectInFlight.set(false);
        Log.d(TAG, "WFD_CONN_INFO request connection info");
        try {
            manager.requestConnectionInfo(channel, this::onConnectionInfoAvailable);
        } catch (SecurityException sec) {
            Log.w(TAG, "WFD_CONN_INFO security exception", sec);
            callback.onStatus("Wi-Fi Direct connection info permission missing");
        }
    }

    private void onConnectionInfoAvailable(WifiP2pInfo info) {
        if (info == null || !info.groupFormed) {
            Log.d(TAG, "WFD_INFO group not formed");
            groupFormed = false;
            groupOwner = false;
            groupOwnerAddress = null;
            scheduleRediscovery("group-not-formed", REDISCOVER_DELAY_MS);
            return;
        }
        groupFormed = true;
        groupOwner = info.isGroupOwner;
        groupOwnerAddress = info.groupOwnerAddress != null ? info.groupOwnerAddress.getHostAddress() : null;
        pendingConnectAddresses.clear();
        Log.i(TAG, "WFD_INFO groupFormed owner=" + info.isGroupOwner
                + " ownerAddr=" + (info.groupOwnerAddress != null ? info.groupOwnerAddress.getHostAddress() : "null"));
        if (info.isGroupOwner) {
            startServer();
            // Group owner keeps discovering to add more clients without dropping current sockets.
            scheduleRediscovery("owner-expand-group", IDLE_DISCOVER_LOOP_MS);
            return;
        }
        if (info.groupOwnerAddress != null) {
            connectToGroupOwner(info.groupOwnerAddress.getHostAddress());
        }
    }

    private void startServer() {
        if (acceptThread != null && acceptThread.isAlive()) {
            Log.d(TAG, "WFD_SERVER already running");
            return;
        }
        acceptThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                Log.i(TAG, "WFD_SERVER listening port=" + PORT);
                while (started && !Thread.currentThread().isInterrupted()) {
                    Socket socket = serverSocket.accept();
                    Log.i(TAG, "WFD_SERVER accepted from=" + socket.getInetAddress().getHostAddress());
                    attachSocket(socket, "peer-" + socket.getInetAddress().getHostAddress());
                }
            } catch (IOException ioException) {
                Log.w(TAG, "WFD_SERVER stopped: " + ioException.getMessage());
            }
        }, "wifi-direct-accept");
        acceptThread.start();
    }

    private void closeServer() {
        if (acceptThread != null) {
            acceptThread.interrupt();
            acceptThread = null;
        }
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // Ignore close errors.
            }
            serverSocket = null;
        }
        Log.d(TAG, "WFD_SERVER closed");
    }

    private void connectToGroupOwner(String hostAddress) {
        if (!started || hostAddress == null || hostAddress.isEmpty()) {
            Log.d(TAG, "WFD_OWNER_CONNECT skip started=" + started + " host=" + hostAddress);
            return;
        }
        if (!connectingToOwner.compareAndSet(false, true)) {
            Log.d(TAG, "WFD_OWNER_CONNECT skip: already connecting");
            return;
        }

        new Thread(() -> {
            Socket socket = new Socket();
            try {
                Log.i(TAG, "WFD_OWNER_CONNECT start host=" + hostAddress + " timeoutMs=" + CONNECT_TIMEOUT_MS);
                socket.connect(new InetSocketAddress(hostAddress, PORT), CONNECT_TIMEOUT_MS);
                attachSocket(socket, "owner-" + hostAddress);
                Log.i(TAG, "WFD_OWNER_CONNECT success host=" + hostAddress);
                if (groupOwnerAddress != null) {
                    recentlyConnectedAddresses.add(groupOwnerAddress);
                }
            } catch (IOException ioException) {
                Log.w(TAG, "WFD_OWNER_CONNECT fail host=" + hostAddress + " err=" + ioException.getMessage());
                safeClose(socket);
            } finally {
                connectingToOwner.set(false);
            }
        }, "wifi-direct-connect").start();
    }

    private void attachSocket(Socket socket, String key) {
        Log.i(TAG, "WFD_ATTACH key=" + key + " remote=" + socket.getInetAddress().getHostAddress());
        recentlyConnectedAddresses.add(socket.getInetAddress().getHostAddress());
        PeerConnection old = connections.put(key, new PeerConnection(key, socket));
        if (old != null) {
            old.close();
        }
        callback.onPeerLinksChanged(connections.size());
        PeerConnection connection = connections.get(key);
        if (connection != null) {
            connection.startReadLoop();
            flushPendingFrames();
        }
    }

    private void flushPendingFrames() {
        if (connections.isEmpty()) {
            return;
        }
        List<byte[]> buffered = new ArrayList<>();
        synchronized (lock) {
            while (!pendingFrames.isEmpty()) {
                buffered.add(pendingFrames.removeFirst());
            }
        }
        if (!buffered.isEmpty()) {
            Log.d(TAG, "WFD_FLUSH pending=" + buffered.size() + " peers=" + connections.size());
        }
        for (byte[] frame : buffered) {
            sendFrame(frame);
        }
    }

    private boolean hasWifiDirectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(appContext, Manifest.permission.NEARBY_WIFI_DEVICES)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private void removeConnection(String key) {
        PeerConnection removed = connections.remove(key);
        if (removed != null) {
            removed.close();
            Log.i(TAG, "WFD_REMOVE key=" + key + " remaining=" + connections.size());
        }
        callback.onPeerLinksChanged(connections.size());
        if (connections.isEmpty() && started) {
            clearConnectWatchdog();
            p2pConnectInFlight.set(false);
            scheduleRediscovery("all-sockets-closed", REDISCOVER_DELAY_MS);
        }
    }

    private void armConnectWatchdog(String deviceAddress) {
        clearConnectWatchdog();
        connectWatchdogRunnable = () -> {
            connectWatchdogRunnable = null;
            if (!p2pConnectInFlight.get() || !connections.isEmpty()) {
                return;
            }
            Log.w(TAG, "WFD_CONNECT watchdog timeout device=" + deviceAddress + " - resetting inFlight");
            p2pConnectInFlight.set(false);
            pendingConnectAddresses.remove(deviceAddress);
            scheduleRediscovery("connect-watchdog-timeout", REDISCOVER_DELAY_MS);
        };
        mainHandler.postDelayed(connectWatchdogRunnable, CONNECT_RESULT_TIMEOUT_MS);
    }

    private void clearConnectWatchdog() {
        if (connectWatchdogRunnable != null) {
            mainHandler.removeCallbacks(connectWatchdogRunnable);
            connectWatchdogRunnable = null;
        }
        if (!p2pConnectInFlight.get()) {
            pendingConnectAddresses.clear();
        }
    }

    private void scheduleRediscovery(String reason, long delayMs) {
        if (!started) {
            return;
        }
        if (rediscoverRunnable != null) {
            mainHandler.removeCallbacks(rediscoverRunnable);
        }
        rediscoverRunnable = () -> {
            rediscoverRunnable = null;
            Log.d(TAG, "WFD_REDISCOVER fire reason=" + reason);
            discoverPeers();
        };
        mainHandler.postDelayed(rediscoverRunnable, Math.max(800L, delayMs));
    }

    private void logStateSnapshot(String reason) {
        int pendingCount;
        synchronized (lock) {
            pendingCount = pendingFrames.size();
        }
        Log.d(TAG, "WFD_SNAPSHOT reason=" + reason
                + " started=" + started
                + " p2pEnabled=" + wifiP2pEnabled
                + " discoveryInProgress=" + discoveryInProgress
                + " inFlight=" + p2pConnectInFlight.get()
                + " ownerConnecting=" + connectingToOwner.get()
                + " connections=" + connections.size()
                + " pending=" + pendingCount
                + " manager=" + (manager != null)
                + " channel=" + (channel != null)
                + " receiver=" + (receiver != null)
                + " acceptThreadAlive=" + (acceptThread != null && acceptThread.isAlive())
                + " serverSocket=" + (serverSocket != null)
                + " groupFormed=" + groupFormed
                + " groupOwner=" + groupOwner
                + " pendingConnects=" + pendingConnectAddresses.size()
                + " knownConnected=" + recentlyConnectedAddresses.size()
                + " lastConnectDevice=" + (lastConnectAttemptDevice == null ? "none" : lastConnectAttemptDevice));
    }

    private void safeClose(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ioException) {
            Log.w(TAG, "WFD_SOCKET_CLOSE fail err=" + ioException.getMessage());
        }
    }

    private String p2pReasonLabel(int reason) {
        switch (reason) {
            case WifiP2pManager.P2P_UNSUPPORTED:
                return "P2P_UNSUPPORTED";
            case WifiP2pManager.BUSY:
                return "BUSY";
            case WifiP2pManager.ERROR:
                return "ERROR";
            default:
                return "UNKNOWN(" + reason + ")";
        }
    }

    private final class PeerConnection {
        private final String key;
        private final Socket socket;
        private final DataInputStream input;
        private final DataOutputStream output;

        PeerConnection(String key, Socket socket) {
            this.key = key;
            this.socket = socket;
            try {
                this.input = new DataInputStream(socket.getInputStream());
                this.output = new DataOutputStream(socket.getOutputStream());
            } catch (IOException e) {
                Log.e(TAG, "WFD_CONN_INIT fail key=" + key + " err=" + e.getMessage());
                throw new IllegalStateException("Socket stream init failed", e);
            }
        }

        void startReadLoop() {
            Thread thread = new Thread(() -> {
                try {
                    while (started && !socket.isClosed()) {
                        int length = input.readInt();
                        if (length <= 0 || length > MAX_FRAME_BYTES) {
                            Log.w(TAG, "WFD_READ invalid frame key=" + key + " length=" + length);
                            break;
                        }
                        byte[] frame = new byte[length];
                        input.readFully(frame);
                        Log.d(TAG, "WFD_READ key=" + key + " bytes=" + length);
                        mainHandler.post(() -> callback.onFrameReceived(frame));
                    }
                } catch (IOException ioException) {
                    Log.w(TAG, "WFD_READ ended key=" + key + " err=" + ioException.getMessage());
                } finally {
                    removeConnection(key);
                }
            }, "wifi-direct-read-" + key);
            thread.start();
        }

        boolean send(byte[] frame) {
            if (socket.isClosed()) {
                Log.d(TAG, "WFD_WRITE skip closed key=" + key);
                return false;
            }
            try {
                synchronized (output) {
                    output.writeInt(frame.length);
                    output.write(frame);
                    output.flush();
                }
                Log.d(TAG, "WFD_WRITE ok key=" + key + " bytes=" + frame.length);
                return true;
            } catch (IOException ioException) {
                Log.w(TAG, "WFD_WRITE fail key=" + key + " err=" + ioException.getMessage());
                removeConnection(key);
                return false;
            }
        }

        void close() {
            safeClose(socket);
        }
    }
}

