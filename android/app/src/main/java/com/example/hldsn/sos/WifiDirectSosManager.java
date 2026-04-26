package com.example.hldsn.sos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages Wi-Fi Direct peer-to-peer SOS broadcasting and discovery.
 *
 * ── SENDER MODE ──
 *   1. Registers a DNS-SD local service named "HLDSN_SOS".
 *   2. Creates an autonomous Wi-Fi Direct P2P group (this device = Group Owner).
 *   3. Starts a TCP ServerSocket on {@value #SOCKET_PORT}.
 *   4. Every device that connects receives the full SOS JSON payload.
 *
 * ── RECEIVER MODE ──
 *   1. Runs DNS-SD service discovery looking for "HLDSN_SOS".
 *   2. When found, reads lat/lng directly from the TXT record (fast path).
 *   3. Also connects via socket to receive the full payload (details path).
 *
 * REQUIRES: NEARBY_WIFI_DEVICES + CHANGE_WIFI_STATE permissions.
 */
public class WifiDirectSosManager {

    private static final String TAG           = "WifiDirectSos";
    private static final String SERVICE_TYPE  = "_hldsn._tcp";
    private static final String SERVICE_NAME  = "HLDSN_SOS";
    static final         int    SOCKET_PORT   = 8778;

    public interface SosReceivedListener {
        void onSosReceived(SosPacket packet);
    }

    private final Context            context;
    private final WifiP2pManager     manager;
    private final WifiP2pManager.Channel channel;

    private BroadcastReceiver       p2pReceiver;
    private SosReceivedListener     sosListener;
    private ServerSocket            serverSocket;
    private boolean                 receiverRegistered = false;

    public WifiDirectSosManager(Context context) {
        this.context = context;
        manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        channel = (manager != null)
                ? manager.initialize(context, context.getMainLooper(), null)
                : null;
    }

    // ── Sender mode ───────────────────────────────────────────────────────────

    /**
     * Registers an SOS service over Wi-Fi Direct and starts a socket server
     * so nearby receivers can connect and get the full SOS payload.
     */
    public void startAsSender(SosPacket packet) {
        if (manager == null || channel == null) {
            Log.w(TAG, "WifiP2pManager unavailable");
            return;
        }
        registerP2pReceiver();

        // Build DNS-SD TXT record — receivers get lat/lng immediately without a socket
        Map<String, String> record = new HashMap<>();
        record.put("lat", String.valueOf(packet.getLatMilli() / 1000d));
        record.put("lng", String.valueOf(packet.getLonMilli() / 1000d));
        record.put("name", truncate(packet.senderName, 40));

        WifiP2pDnsSdServiceInfo serviceInfo =
                WifiP2pDnsSdServiceInfo.newInstance(SERVICE_NAME, SERVICE_TYPE, record);

        manager.addLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "DNS-SD service registered");
                createGroupAndServe(packet);
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "DNS-SD registration failed (" + reason + "), trying group anyway");
                createGroupAndServe(packet);
            }
        });
    }

    private void createGroupAndServe(SosPacket packet) {
        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "P2P group created — starting socket server");
                startSocketServer(packet);
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "createGroup failed (" + reason + ") — checking for existing group");
                // Might already be a group owner; try to serve on existing group
                manager.requestGroupInfo(channel, group -> {
                    if (group != null) {
                        Log.d(TAG, "Using existing P2P group");
                        startSocketServer(packet);
                    }
                });
            }
        });
    }

    private void startSocketServer(SosPacket packet) {
        String json = toJson(packet);
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(SOCKET_PORT);
                Log.d(TAG, "Socket server listening on port " + SOCKET_PORT);
                while (!serverSocket.isClosed()) {
                    try {
                        Socket client = serverSocket.accept();
                        // Serve each client on its own thread
                        new Thread(() -> {
                            try (BufferedWriter w = new BufferedWriter(
                                    new OutputStreamWriter(client.getOutputStream()))) {
                                w.write(json);
                                w.newLine();
                                w.flush();
                                Log.d(TAG, "SOS payload delivered to " + client.getInetAddress());
                            } catch (Exception e) {
                                Log.e(TAG, "Error sending to client", e);
                            }
                        }).start();
                    } catch (Exception e) {
                        if (!serverSocket.isClosed()) Log.w(TAG, "Accept error: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Server socket error", e);
            }
        }, "SosSocketServer").start();
    }

    // ── Receiver mode ─────────────────────────────────────────────────────────

    /**
     * Starts DNS-SD service discovery.  When an "HLDSN_SOS" service is found,
     * the listener is called immediately with the TXT-record data (fast path),
     * and a socket connection is attempted for the full payload (details path).
     */
    public void startAsReceiver(SosReceivedListener listener) {
        if (manager == null || channel == null) return;
        this.sosListener = listener;
        registerP2pReceiver();
        discoverSosServices();
    }

    private void discoverSosServices() {
        // TXT record listener — fast path: lat/lng arrive without a TCP connection
        WifiP2pManager.DnsSdTxtRecordListener txtListener = (fullDomain, record, srcDevice) -> {
            if (record.containsKey("lat") && record.containsKey("lng")) {
                try {
                    double lat  = Double.parseDouble(record.get("lat"));
                    double lng  = Double.parseDouble(record.get("lng"));
                    String name = record.getOrDefault("name", "Unknown");
                    Log.d(TAG, "SOS TXT record received from: " + name);
                    if (sosListener != null) {
                        int nowEpoch = (int) (System.currentTimeMillis() / 1000L);
                        sosListener.onSosReceived(SosPacket.createSos(lat, lng, nowEpoch, name));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing TXT record", e);
                }
            }
        };

        // Service instance listener — connects via socket for full payload
        WifiP2pManager.DnsSdServiceResponseListener svcListener =
                (instanceName, registrationType, srcDevice) -> {
                    if (SERVICE_NAME.equals(instanceName)) {
                        Log.d(TAG, "HLDSN_SOS service found — connecting to " + srcDevice.deviceName);
                        connectAndFetch(srcDevice);
                    }
                };

        manager.setDnsSdResponseListeners(channel, svcListener, txtListener);

        manager.addServiceRequest(channel,
                WifiP2pDnsSdServiceRequest.newInstance(),
                new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        manager.discoverServices(channel, new WifiP2pManager.ActionListener() {
                            @Override public void onSuccess() {
                                Log.d(TAG, "Wi-Fi Direct service discovery started");
                            }
                            @Override public void onFailure(int reason) {
                                Log.e(TAG, "discoverServices failed: " + reason);
                            }
                        });
                    }
                    @Override
                    public void onFailure(int reason) {
                        Log.e(TAG, "addServiceRequest failed: " + reason);
                    }
                });
    }

    private void connectAndFetch(WifiP2pDevice device) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() {
                Log.d(TAG, "P2P connection requested — waiting for connection info");
            }
            @Override public void onFailure(int reason) {
                Log.e(TAG, "P2P connect failed: " + reason);
            }
        });
        // Connection result handled in p2pReceiver via WIFI_P2P_CONNECTION_CHANGED_ACTION
    }

    private void fetchFromSocket(String groupOwnerIp) {
        new Thread(() -> {
            try {
                Socket socket = new Socket(groupOwnerIp, SOCKET_PORT);
                socket.setSoTimeout(5_000);
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()))) {
                    String json = r.readLine();
                    if (json != null) {
                        SosPacket packet = fromJson(json);
                        if (packet != null && sosListener != null) {
                            Log.d(TAG, "Full SOS payload received via socket: " + packet.senderName);
                            sosListener.onSosReceived(packet);
                        }
                    }
                }
                socket.close();
            } catch (Exception e) {
                Log.e(TAG, "Socket fetch error: " + e.getMessage());
            }
        }, "SosSocketClient").start();
    }

    // ── BroadcastReceiver ─────────────────────────────────────────────────────

    private void registerP2pReceiver() {
        if (receiverRegistered) return;
        p2pReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
                        .equals(intent.getAction())) {
                    manager.requestConnectionInfo(channel, info -> {
                        if (info != null && info.groupFormed && !info.isGroupOwner) {
                            // We are the client — connect socket to the group owner
                            String ownerIp = info.groupOwnerAddress.getHostAddress();
                            Log.d(TAG, "Connected to P2P group owner @ " + ownerIp);
                            fetchFromSocket(ownerIp);
                        }
                    });
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        context.registerReceiver(p2pReceiver, filter);
        receiverRegistered = true;
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    /** Call from Service.onDestroy() to release all resources. */
    public void stop() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try { serverSocket.close(); } catch (Exception ignored) {}
        }
        if (manager != null && channel != null) {
            manager.clearLocalServices(channel, null);
            manager.clearServiceRequests(channel, null);
            manager.removeGroup(channel, null);
        }
        if (p2pReceiver != null && receiverRegistered) {
            try { context.unregisterReceiver(p2pReceiver); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private String toJson(SosPacket packet) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "SOS");
            obj.put("name", packet.senderName);
            obj.put("lat", packet.getLatMilli() / 1000d);
            obj.put("lng", packet.getLonMilli() / 1000d);
            return obj.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private SosPacket fromJson(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            int nowEpoch = (int) (System.currentTimeMillis() / 1000L);
            return SosPacket.createSos(
                    obj.getDouble("lat"),
                    obj.getDouble("lng"),
                    nowEpoch,
                    obj.getString("name"));
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String s, int maxLen) {
        return (s != null && s.length() > maxLen) ? s.substring(0, maxLen) : s;
    }
}
