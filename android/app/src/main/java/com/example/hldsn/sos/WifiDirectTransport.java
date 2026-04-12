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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class WifiDirectTransport {

    private static final String TAG = "WifiDirectTx";

    interface Callback {
        void onFrameReceived(byte[] frame);
        void onStatus(String status);
    }

    private static final int PORT = 47831;
    private static final int CONNECT_TIMEOUT_MS = 2500;
    private static final int MAX_FRAME_BYTES = 1024;
    private static final int MAX_PENDING_FRAMES = 24;

    private final Context appContext;
    private final Callback callback;
    private final Handler mainHandler;
    private final Object lock = new Object();
    private final Map<String, PeerConnection> connections = new ConcurrentHashMap<>();
    private final ArrayDeque<byte[]> pendingFrames = new ArrayDeque<>();
    private final AtomicBoolean connectingToOwner = new AtomicBoolean(false);

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private BroadcastReceiver receiver;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private boolean started;
    private boolean wifiP2pEnabled;

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
        startServer();
        discoverPeers();
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
    }

    boolean isReady() {
        boolean ready = started && !connections.isEmpty();
        if (!ready) {
            Log.d(TAG, "WFD_READY=false started=" + started + " connections=" + connections.size());
        }
        return ready;
    }

    void sendFrame(byte[] frame) {
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
            if (pendingFrames.size() >= MAX_PENDING_FRAMES) {
                pendingFrames.removeFirst();
            }
            pendingFrames.addLast(frame);
        }
        Log.d(TAG, "WFD_SEND buffered bytes=" + frame.length + " pending=" + pendingFrames.size());
        discoverPeers();
    }

    @SuppressLint("MissingPermission")
    void discoverPeers() {
        boolean hasPerm = hasWifiDirectPermission();
        if (!started || manager == null || channel == null || !wifiP2pEnabled || !hasPerm) {
            Log.d(TAG, "WFD_DISCOVER skip started=" + started
                    + " manager=" + (manager != null)
                    + " channel=" + (channel != null)
                    + " p2pEnabled=" + wifiP2pEnabled
                    + " permission=" + hasPerm);
            return;
        }
        try {
            Log.d(TAG, "WFD_DISCOVER start");
            manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "WFD_DISCOVER success (waiting peers changed callback)");
                    // Peers callback will arrive through receiver.
                }

                @Override
                public void onFailure(int reason) {
                    Log.w(TAG, "WFD_DISCOVER failure reason=" + reason + " label=" + p2pReasonLabel(reason));
                    callback.onStatus("Wi-Fi Direct discovery failed: " + reason);
                }
            });
        } catch (SecurityException sec) {
            Log.w(TAG, "WFD_DISCOVER security exception", sec);
            callback.onStatus("Wi-Fi Direct permission missing");
        }
    }

    private void onChannelDisconnected() {
        Log.w(TAG, "WFD_CHANNEL disconnected");
        callback.onStatus("Wi-Fi Direct channel disconnected");
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
        if (!connections.isEmpty()) {
            Log.d(TAG, "WFD_CONNECT skip: already connected peers=" + connections.size());
            flushPendingFrames();
            return;
        }

        List<WifiP2pDevice> devices = new ArrayList<>(peerList.getDeviceList());
        Log.d(TAG, "WFD_CONNECT peersFound=" + devices.size());
        if (devices.isEmpty()) {
            return;
        }

        WifiP2pDevice selected = devices.get(0);
        Log.i(TAG, "WFD_CONNECT selecting device=" + selected.deviceAddress + " status=" + selected.status);
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = selected.deviceAddress;
        config.groupOwnerIntent = 7;

        try {
            manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "WFD_CONNECT success request accepted device=" + selected.deviceAddress);
                    callback.onStatus("Wi-Fi Direct peer connected");
                }

                @Override
                public void onFailure(int reason) {
                    Log.w(TAG, "WFD_CONNECT failure device=" + selected.deviceAddress
                            + " reason=" + reason + " label=" + p2pReasonLabel(reason));
                    callback.onStatus("Wi-Fi Direct connect failed: " + reason);
                }
            });
        } catch (SecurityException sec) {
            Log.w(TAG, "WFD_CONNECT security exception", sec);
            callback.onStatus("Wi-Fi Direct connect permission missing");
        }
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
            return;
        }
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
            return;
        }
        Log.i(TAG, "WFD_INFO groupFormed owner=" + info.isGroupOwner
                + " ownerAddr=" + (info.groupOwnerAddress != null ? info.groupOwnerAddress.getHostAddress() : "null"));
        if (info.isGroupOwner) {
            startServer();
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
        PeerConnection old = connections.put(key, new PeerConnection(key, socket));
        if (old != null) {
            old.close();
        }
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
        if (connections.isEmpty() && started) {
            discoverPeers();
        }
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

