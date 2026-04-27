package com.example.hldsn.sos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.hldsn.NetworkUtils;
import com.example.hldsn.mesh.MeshMessagingCoordinator;
import com.example.hldsn.mesh.model.MeshIdentity;
import com.example.hldsn.mesh.model.MeshMessage;
import com.example.hldsn.mesh.model.RelayDecision;
import com.example.hldsn.R;
import com.example.hldsn.firestore.MessageTtlHelper;
import com.example.hldsn.home.HomePageActivity;
import com.example.hldsn.notification_module.SosAlertRecord;
import com.example.hldsn.notification_module.SosAlertStore;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SosForegroundService extends Service {

    public static final String ACTION_START_MESH = "com.example.hldsn.sos.ACTION_START_MESH";
    public static final String ACTION_TRIGGER_SOS = "com.example.hldsn.sos.ACTION_TRIGGER_SOS";
    public static final String ACTION_SOS_STATUS = "com.example.hldsn.sos.ACTION_SOS_STATUS";
    public static final String ACTION_SOS_ALERTS_UPDATED = "com.example.hldsn.sos.ACTION_SOS_ALERTS_UPDATED";
    public static final String ACTION_OPEN_NOTIFICATIONS = "com.example.hldsn.sos.ACTION_OPEN_NOTIFICATIONS";
    public static final String ACTION_SEND_MESH_MESSAGE = "com.example.hldsn.sos.ACTION_SEND_MESH_MESSAGE";
    public static final String ACTION_MESH_MESSAGE_RECEIVED = "com.example.hldsn.sos.ACTION_MESH_MESSAGE_RECEIVED";
    public static final String EXTRA_SENDER_NAME = "extra_sender_name";
    public static final String EXTRA_STATUS = "extra_status";
    public static final String EXTRA_MESH_DESTINATION_ID = "extra_mesh_destination_id";
    public static final String EXTRA_MESH_TEXT = "extra_mesh_text";
    public static final String EXTRA_MESH_TTL = "extra_mesh_ttl";
    public static final String EXTRA_MESH_SOURCE_ID = "extra_mesh_source_id";
    public static final String EXTRA_MESH_MESSAGE_ID = "extra_mesh_message_id";
    public static final String EXTRA_MESH_PUBLIC_KEY = "extra_mesh_public_key";
    public static final String EXTRA_MESH_DISPLAY_NAME = "extra_mesh_display_name";
    public static final String EXTRA_MESH_DEVICE_NAME = "extra_mesh_device_name";

    private static final String TAG = "SosFgService";
    private static final String SOS_TRACE_TAG = "SOS_TRACE";
    private static final String MESH_TAG = "MeshMessaging";  // Dedicated tag for mesh offline messaging
    private static final String MESSAGE_LOG_TAG = "MessageWriteAudit";
    private static final String CHANNEL_ID = "sos_mesh_channel";
    private static final String ALERT_CHANNEL_ID = "sos_received_alerts";
    private static final String MESH_ALERT_CHANNEL_ID = "mesh_received_alerts";
    private static final int NOTIFICATION_ID = 3101;
    private static final int MESH_NOTIFICATION_BASE_ID = 6400;

    private static final long TICK_MS = 800L;
    private static final long ADVERTISE_WINDOW_MS = 650L;
    private static final long MAX_SEEN_AGE_MS = 2 * 60_000L;
    private static final long BLE_RETRY_COOLDOWN_MS = 10_000L;
    private static final long BLE_REINIT_MIN_INTERVAL_MS = 1_500L;
    private static final long IDENTITY_BROADCAST_INTERVAL_MS = 15_000L;
    private static final long GPS_MAX_STALE_MS = 600_000L; // 10 minutes
    private static final float GPS_MAX_ACCURACY_METERS = 500f; 
    private static final long GPS_SINGLE_FIX_TIMEOUT_MS = 10_000L;
    private static final long PRESENCE_HEARTBEAT_MS = 60_000L;
    private static final long ACTIVE_WINDOW_MS = 10 * 60_000L;
    private static final double ONLINE_RADIUS_METERS = 3_000d;
    private static final int MAX_NEARBY_VOLUNTEERS = 3;
    private static final int MAX_NEARBY_NGO = 1;
    private static final String ROLE_USER = "user";
    private static final String ROLE_VOLUNTEER = "volunteer";
    private static final String ROLE_NGO_ADMIN = "ngo_admin";
    private static final String SOS_INBOX_COLLECTION = "sos_alert_inbox";
    private static final String USERS_COLLECTION = "users";
    private static final String SOS_PREFS = "sos_sender_profile";
    private static final String KEY_CACHED_SENDER_NAME = "cached_sender_name";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final ArrayDeque<QueuedPacket> outbound = new ArrayDeque<>();
    private final Map<Long, Long> seenSosAt = new HashMap<>();
    private final Map<Long, OriginState> originStates = new HashMap<>();
    private final Map<Long, Long> relayedFullAt = new HashMap<>();

    private final TransportSelector transportSelector = new TransportSelector();

    private BluetoothLeAdvertiser advertiser;
    private BluetoothLeScanner scanner;
    private AdvertiseCallback activeAdvertiseCallback;
    private WifiDirectTransport wifiDirectTransport;
    private boolean meshStarted;
    private volatile boolean forceLocationManagerFallback;
    private volatile long bleUnavailableUntilMs;
    private volatile long bleLastInitAttemptMs;
    private volatile boolean bleScanRunning;

    private FusedLocationProviderClient locationClient;
    private MeshMessagingCoordinator meshCoordinator;
    private long nextIdentityBroadcastAtMs;
    private boolean identityBroadcastPending;
    private LocationManager activeSingleFixManager;
    private LocationListener activeSingleFixListener;
    private Runnable activeSingleFixTimeout;
    private ListenerRegistration remoteSosInboxListener;
    private final Set<String> processedRemoteInboxDocs = new HashSet<>();

    private final Runnable presenceHeartbeat = new Runnable() {
        @Override
        public void run() {
            publishPresenceBestEffort();
            handler.postDelayed(this, PRESENCE_HEARTBEAT_MS);
        }
    };

    private final Runnable meshTick = new Runnable() {
        @Override
        public void run() {
            try {
                dispatchNextFrame();
                pruneCaches();
            } finally {
                handler.postDelayed(this, TICK_MS);
            }
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            ScanRecord record = result.getScanRecord();
            if (record == null) {
                return;
            }
            byte[] frame = record.getManufacturerSpecificData(SosCodec.BLE_MANUFACTURER_ID);
            SosPacket packet = SosCodec.decodeBleBeacon(frame);
            if (packet == null) {
                return;
            }
            Log.i(TAG, "BLE beacon received: " + beaconSummary(packet));
            onPacketReceived(packet, false);
        }
    };

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                return;
            }
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            Log.i(TAG, "BLE_STATE_CHANGE state=" + bluetoothStateLabel(state));
            if (state == BluetoothAdapter.STATE_ON) {
                refreshBluetoothIfNeeded("bt_state_on");
                if (meshStarted) {
                    startScanning();
                }
            } else if (state == BluetoothAdapter.STATE_OFF) {
                bleScanRunning = false;
                advertiser = null;
                scanner = null;
                stopActiveAdvertiser();
                sendStatus("Bluetooth turned off");
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        meshCoordinator = new MeshMessagingCoordinator(this, resolveSenderName(null));
        Log.i(TAG, "Service created. Initializing mesh transports.");
        wifiDirectTransport = new WifiDirectTransport(this, new WifiDirectTransport.Callback() {
            @Override
            public void onFrameReceived(byte[] frame) {
                SosPacket packet = SosCodec.decodeFullFrame(frame);
                if (packet != null) {
                    Log.i(TAG, "Wi-Fi Direct full frame received: " + packetSummary(packet));
                    onPacketReceived(packet, true);
                    return;
                }
                onMeshFrameReceived(frame);
            }

            @Override
            public void onStatus(String status) {
                sendStatus(status);
            }

            @Override
            public void onPeerLinksChanged(int activeConnections) {
                Log.d(MESH_TAG, "topology: active_links=" + activeConnections);
                identityBroadcastPending = true;
                if (activeConnections > 0) {
                    broadcastIdentityIfDue(true);
                }
            }
        });
        registerBluetoothStateReceiver();
        initBluetooth();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Mesh standby"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        Log.i(TAG, "onStartCommand action=" + action + " startId=" + startId);
        if (ACTION_TRIGGER_SOS.equals(action)) {
            startMesh();
            triggerSos(intent);
        } else if (ACTION_SEND_MESH_MESSAGE.equals(action)) {
            startMesh();
            sendMeshMessage(intent);
        } else {
            startMesh();
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service destroy requested. Stopping mesh transports.");
        handler.removeCallbacksAndMessages(null);
        handler.removeCallbacks(presenceHeartbeat);
        if (remoteSosInboxListener != null) {
            remoteSosInboxListener.remove();
            remoteSosInboxListener = null;
        }
        markPresenceInactive();
        clearSingleFixRequest();
        stopScanning();
        stopActiveAdvertiser();
        unregisterBluetoothStateReceiver();
        if (wifiDirectTransport != null) {
            wifiDirectTransport.stop();
        }
        super.onDestroy();
    }

    private void initBluetooth() {
        BluetoothAdapter adapter = getBluetoothAdapter();
        if (adapter == null) {
            advertiser = null;
            scanner = null;
            Log.w(TAG, "BluetoothAdapter unavailable.");
            return;
        }
        if (!adapter.isEnabled()) {
            advertiser = null;
            scanner = null;
            Log.w(TAG, "Bluetooth adapter present but disabled.");
            return;
        }
        advertiser = adapter.getBluetoothLeAdvertiser();
        scanner = adapter.getBluetoothLeScanner();
        Log.i(TAG, "Bluetooth initialized. enabled=true advertiser=" + (advertiser != null) + " scanner=" + (scanner != null));
    }

    private BluetoothAdapter getBluetoothAdapter() {
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) {
            return null;
        }
        return manager.getAdapter();
    }

    private void refreshBluetoothIfNeeded(String reason) {
        long now = SystemClock.elapsedRealtime();
        if (now - bleLastInitAttemptMs < BLE_REINIT_MIN_INTERVAL_MS) {
            return;
        }

        BluetoothAdapter adapter = getBluetoothAdapter();
        boolean adapterEnabled = adapter != null && adapter.isEnabled();
        boolean needsRefresh = advertiser == null || scanner == null;
        if (!needsRefresh && adapterEnabled) {
            return;
        }

        bleLastInitAttemptMs = now;
        Log.d(TAG, "BLE_REFRESH reason=" + reason
                + " adapterPresent=" + (adapter != null)
                + " adapterEnabled=" + adapterEnabled
                + " advertiser=" + (advertiser != null)
                + " scanner=" + (scanner != null)
                + " canAdvertisePerm=" + canAdvertise()
                + " canScanPerm=" + canScan());

        initBluetooth();
    }

    private void registerBluetoothStateReceiver() {
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothStateReceiver, filter);
    }

    private void unregisterBluetoothStateReceiver() {
        try {
            unregisterReceiver(bluetoothStateReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver may already be unregistered.
        }
    }

    private void startMesh() {
        if (meshStarted) {
            Log.d(TAG, "Mesh already started.");
            return;
        }
        meshStarted = true;
        Log.i(TAG, "Starting SOS mesh loop.");
        refreshBluetoothIfNeeded("start_mesh");
        startScanning();
        if (wifiDirectTransport != null) {
            wifiDirectTransport.start();
        }
        nextIdentityBroadcastAtMs = 0L;
        identityBroadcastPending = true;
        handler.post(meshTick);
        startPresenceHeartbeat();
        startRemoteSosInboxListener();
        sendStatus("HLDSN SOS mesh ready");
    }

    private void sendMeshMessage(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        String destinationId = safeTrim(intent.getStringExtra(EXTRA_MESH_DESTINATION_ID));
        String clearText = safeTrim(intent.getStringExtra(EXTRA_MESH_TEXT));
        int ttl = Math.max(1, intent.getIntExtra(EXTRA_MESH_TTL, 5));
        String destinationPublicKey = safeTrim(intent.getStringExtra(EXTRA_MESH_PUBLIC_KEY));
        String destinationDisplayName = safeTrim(intent.getStringExtra(EXTRA_MESH_DISPLAY_NAME));
        String destinationDeviceName = safeTrim(intent.getStringExtra(EXTRA_MESH_DEVICE_NAME));

        Log.d(MESH_TAG, "sendMeshMessage: START dest=" + destinationId + " text_len=" + clearText.length()
                + " ttl=" + ttl + " has_pubkey=" + !destinationPublicKey.isEmpty());

        if (destinationId.isEmpty() || clearText.isEmpty()) {
            Log.e(MESH_TAG, "sendMeshMessage: FAILED - destination or message empty");
            sendStatus("Mesh send failed: destination or message missing");
            return;
        }
        if (meshCoordinator == null || wifiDirectTransport == null) {
            Log.e(MESH_TAG, "sendMeshMessage: FAILED - transport unavailable coordinator=" + (meshCoordinator != null)
                    + " wfd=" + (wifiDirectTransport != null));
            sendStatus("Mesh send failed: transport unavailable");
            return;
        }

        if (!destinationPublicKey.isEmpty()) {
            String display = destinationDisplayName.isEmpty() ? "Peer" : destinationDisplayName;
            String device = destinationDeviceName.isEmpty() ? "Unknown device" : destinationDeviceName;
            Log.d(MESH_TAG, "sendMeshMessage: saving peer dest=" + destinationId + " display=" + display + " device=" + device);
            meshCoordinator.saveDiscoveredPeer(new MeshIdentity(destinationId, display, device, destinationPublicKey));
        }

        try {
            Log.d(MESH_TAG, "sendMeshMessage: building encrypted message");
            MeshMessage message = meshCoordinator.buildEncryptedMessage(destinationId, clearText, ttl);
            Log.d(MESH_TAG, "sendMeshMessage: message built id=" + message.getMessageId() + " payload_len=" + message.getEncryptedPayload().length());

            byte[] encoded = meshCoordinator.encodeMessage(message);
            Log.d(MESH_TAG, "sendMeshMessage: message encoded frame_len=" + encoded.length);

            wifiDirectTransport.sendFrame(encoded);
            Log.i(MESH_TAG, "sendMeshMessage: SUCCESS queued for relay msg_id=" + message.getMessageId());

            sendStatus("Mesh message queued for relay");
            identityBroadcastPending = true;
            broadcastIdentityIfDue(true);
        } catch (IllegalArgumentException e) {
            Log.e(MESH_TAG, "sendMeshMessage: FAILED - peer identity not discovered dest=" + destinationId, e);
            sendStatus("Mesh send failed: peer identity not discovered");
        } catch (Exception e) {
            Log.e(MESH_TAG, "sendMeshMessage: FAILED - exception", e);
            sendStatus("Mesh send failed");
        }
    }

    private void onMeshFrameReceived(byte[] frame) {
        if (meshCoordinator == null || frame == null || frame.length == 0) {
            Log.d(MESH_TAG, "onMeshFrameReceived: skip - invalid input");
            return;
        }

        Log.d(MESH_TAG, "onMeshFrameReceived: START frame_len=" + frame.length);

        RelayDecision decision = meshCoordinator.onIncomingFrame(frame);
        if (decision == null) {
            Log.d(MESH_TAG, "onMeshFrameReceived: decision=null (likely identity frame)");
            return;
        }

        Log.d(MESH_TAG, "onMeshFrameReceived: decision=" + decision.getAction() + " has_msg=" + (decision.getMessage() != null));

        if (decision.getAction() == RelayDecision.Action.FORWARD && decision.getMessage() != null) {
            if (wifiDirectTransport != null) {
                Log.d(MESH_TAG, "onMeshFrameReceived: RELAYING message msg_id=" + decision.getMessage().getMessageId());
                wifiDirectTransport.sendFrame(meshCoordinator.encodeMessage(decision.getMessage()));
            }
            sendStatus("Mesh relay forwarding message");
            return;
        }

        if (decision.getAction() == RelayDecision.Action.DELIVER && decision.getMessage() != null) {
            MeshMessage delivered = decision.getMessage();
            String senderLabel = resolveMeshSenderLabel(delivered.getSourceId());
            String clearText = decision.getClearText();
            
            Log.i(MESH_TAG, "onMeshFrameReceived: DELIVERING message msg_id=" + delivered.getMessageId() 
                    + " from=" + delivered.getSourceId() + " text_len=" + (clearText != null ? clearText.length() : 0));

            persistIncomingMeshMessage(delivered, senderLabel, clearText);

            showReceivedMeshNotification(delivered.getMessageId(), senderLabel, clearText);

            Intent deliveredIntent = new Intent(ACTION_MESH_MESSAGE_RECEIVED);
            deliveredIntent.setPackage(getPackageName());
            deliveredIntent.putExtra(EXTRA_MESH_MESSAGE_ID, delivered.getMessageId());
            deliveredIntent.putExtra(EXTRA_MESH_SOURCE_ID, delivered.getSourceId());
            deliveredIntent.putExtra(EXTRA_MESH_DESTINATION_ID, delivered.getDestinationId());

            if (clearText != null && !clearText.isEmpty()) {
                deliveredIntent.putExtra(EXTRA_MESH_TEXT, clearText);
            }
            try {
                sendBroadcast(deliveredIntent);
                Log.d(MESH_TAG, "onMeshFrameReceived: broadcast sent msg_id=" + delivered.getMessageId());
            } catch (Exception e) {
                Log.w(MESH_TAG, "onMeshFrameReceived: broadcast send failed", e);
            }
            return;
        }

        Log.d(MESH_TAG, "onMeshFrameReceived: decision=" + decision.getAction() + " - no action taken");
    }

    private void persistIncomingMeshMessage(MeshMessage delivered, String senderLabel, @Nullable String clearText) {
        if (delivered == null || clearText == null || clearText.trim().isEmpty()) {
            return;
        }
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) {
            Log.w(MESH_TAG, "persistIncomingMeshMessage: skipped user not authenticated");
            return;
        }

        String currentUid = me.getUid();
        String sourceMeshId = delivered.getSourceId() != null ? delivered.getSourceId().trim() : "";
        if (sourceMeshId.isEmpty()) {
            Log.w(MESH_TAG, "persistIncomingMeshMessage: skipped missing source mesh id");
            return;
        }

        String[] ids = {currentUid, sourceMeshId};
        Arrays.sort(ids);
        String chatId = ids[0] + "_" + ids[1];

        Timestamp now = Timestamp.now();
        Timestamp expireAt = MessageTtlHelper.calculateExpireAt(now);
        Map<String, Object> msgMap = new HashMap<>();
        msgMap.put("senderId", sourceMeshId);
        msgMap.put("senderName", senderLabel != null && !senderLabel.trim().isEmpty() ? senderLabel.trim() : "Mesh peer");
        msgMap.put("text", clearText);
        msgMap.put("timestamp", now);
        msgMap.put("expireAt", expireAt);
        msgMap.put("read", false);

        String messageId = delivered.getMessageId() != null && !delivered.getMessageId().trim().isEmpty()
                ? delivered.getMessageId().trim()
                : String.valueOf(System.currentTimeMillis());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String messageDocPath = "chats/" + chatId + "/messages/" + messageId;
        Log.i(MESSAGE_LOG_TAG, "WRITE_REQUEST docPath=" + messageDocPath + " payload=" + msgMap);
        db.collection("chats")
                .document(chatId)
                .collection("messages")
                .document(messageId)
                .set(msgMap, SetOptions.merge())
                .addOnSuccessListener(v -> {
                    Log.i(MESSAGE_LOG_TAG, "WRITE_SUCCESS docPath=" + messageDocPath + " expireAt_present=" + msgMap.containsKey("expireAt"));
                    db.collection("chats")
                            .document(chatId)
                            .collection("messages")
                            .document(messageId)
                            .get()
                            .addOnSuccessListener(snapshot -> Log.i(MESSAGE_LOG_TAG,
                                    "WRITE_READBACK docPath=" + messageDocPath + " data=" + snapshot.getData()))
                            .addOnFailureListener(e -> Log.w(MESSAGE_LOG_TAG,
                                    "WRITE_READBACK_FAILED docPath=" + messageDocPath, e));
                    Log.d(MESH_TAG, "persistIncomingMeshMessage: stored msg_id=" + messageId + " chatId=" + chatId);
                })
                .addOnFailureListener(e -> Log.w(MESH_TAG, "persistIncomingMeshMessage: message write failed", e));

        Map<String, Object> chatMeta = new HashMap<>();
        chatMeta.put("participants", Arrays.asList(currentUid, sourceMeshId));
        chatMeta.put("lastMessage", clearText);
        chatMeta.put("lastMessageTime", now);
        chatMeta.put("unreadCounts." + currentUid, FieldValue.increment(1));

        db.collection("chats")
                .document(chatId)
                .set(chatMeta, SetOptions.merge())
                .addOnFailureListener(e -> Log.w(MESH_TAG, "persistIncomingMeshMessage: chat meta write failed", e));
    }

    private String resolveMeshSenderLabel(String sourceId) {
        if (meshCoordinator == null) {
            return "Mesh peer";
        }
        MeshIdentity peer = meshCoordinator.getPeerByUserId(sourceId);
        return peer == null ? "Mesh peer" : peer.getDisplayLabel();
    }

    @SuppressLint("MissingPermission")
    private void startScanning() {
        refreshBluetoothIfNeeded("start_scanning");
        BluetoothAdapter adapter = getBluetoothAdapter();
        boolean adapterEnabled = adapter != null && adapter.isEnabled();
        if (scanner == null || !canScan() || !adapterEnabled) {
            bleScanRunning = false;
            Log.w(TAG, "BLE scan unavailable. scanner=" + (scanner != null)
                    + " canScan=" + canScan()
                    + " adapterEnabled=" + adapterEnabled);
            sendStatus("Nearby scan unavailable");
            return;
        }
        if (bleScanRunning) {
            return;
        }
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        scanner.startScan(null, settings, scanCallback);
        bleScanRunning = true;
        Log.i(TAG, "BLE scan started in low latency mode using manufacturer data id=" + SosCodec.BLE_MANUFACTURER_ID);
    }

    @SuppressLint("MissingPermission")
    private void stopScanning() {
        if (scanner == null || !canScan()) {
            bleScanRunning = false;
            return;
        }
        try {
            scanner.stopScan(scanCallback);
            Log.i(TAG, "BLE scan stopped.");
        } catch (Exception ignored) {
            // Scanner may already be stopped.
        }
        bleScanRunning = false;
    }

    private void triggerSos(@Nullable Intent triggerIntent) {
        Log.i(TAG, "SOS trigger requested.");
        final String senderHint = triggerIntent != null ? triggerIntent.getStringExtra(EXTRA_SENDER_NAME) : null;
        Log.d(TAG, "SOS trigger senderHint=" + (senderHint == null || senderHint.trim().isEmpty() ? "none" : senderHint));

        // --- STEP A: IMMEDIATE BEST-EFFORT DISPATCH ---
        // Don't wait for fresh GPS. Send with whatever we have immediately.
        sendStatus("Sending SOS alert...");
        long immediatePacketId = -1L;
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location lastNet = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location bestAvailable = chooseBetterLocation(lastGps, lastNet);

            if (bestAvailable != null) {
                Log.i(TAG, "Immediate SOS dispatch using cached location: " + bestAvailable.getProvider());
                immediatePacketId = queueOriginSos(bestAvailable.getLatitude(), bestAvailable.getLongitude(), senderHint);
            } else {
                Log.i(TAG, "No cached location. Sending Blind SOS (0,0).");
                immediatePacketId = queueOriginSos(0, 0, senderHint);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Permission error during immediate dispatch", e);
            immediatePacketId = queueOriginSos(0, 0, senderHint);
        }

        final long existingPacketId = immediatePacketId;

        // --- STEP B: BACKGROUND HIGH-ACCURACY REFINEMENT ---
        clearSingleFixRequest();
        if (!hasFineLocationPermission()) {
            Log.w(TAG, "Background GPS refinement skipped: permission missing.");
            return;
        }

        if (forceLocationManagerFallback) {
            Log.i(TAG, "Using forced LocationManager fallback (FLP disabled for session).");
            fetchLocationManagerFallback(senderHint, existingPacketId);
            return;
        }

        if (!isPlayServicesAvailable()) {
            Log.w(TAG, "Google Play services unavailable. Using LocationManager fallback.");
            sendStatus("Using device GPS fallback");
            forceLocationManagerFallback = true;
            fetchLocationManagerFallback(senderHint, existingPacketId);
            return;
        }

        CancellationTokenSource tokenSource = new CancellationTokenSource();
        handler.postDelayed(tokenSource::cancel, 3000L);

        try {
            @SuppressLint("MissingPermission")
            com.google.android.gms.tasks.Task<android.location.Location> task =
                    locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.getToken());
            task.addOnSuccessListener(location -> {
                if (isValidSosLocation(location)) {
                    Log.i(TAG, "FLP current location success lat=" + location.getLatitude() + " lon=" + location.getLongitude());
                    sendSosWithResolvedLocation(location, senderHint, existingPacketId);
                } else {
                    Log.w(TAG, "FLP current location unavailable or invalid. Trying fallback chain.");
                    fetchLastLocationFallback(senderHint, existingPacketId);
                }
            }).addOnFailureListener(e -> {
                Log.w(TAG, "getCurrentLocation failed", e);
                if (isBrokerPackageFailure(e)) {
                    forceLocationManagerFallback = true;
                    sendStatus("Google location service unstable, using device GPS fallback");
                    Log.w(TAG, "Detected broker package failure. Forcing LocationManager fallback for this session.");
                }
                fetchLastLocationFallback(senderHint, existingPacketId);
            });
        } catch (SecurityException sec) {
            if (isBrokerPackageFailure(sec)) {
                forceLocationManagerFallback = true;
                sendStatus("Google location service unstable, using device GPS fallback");
                Log.w(TAG, "SecurityException from FLP broker. Switching to LocationManager fallback.", sec);
                fetchLocationManagerFallback(senderHint, existingPacketId);

                return;
            }
            Log.w(TAG, "SecurityException while requesting FLP location.", sec);
            sendStatus("Location permission missing");
        } catch (RuntimeException runtimeException) {
            Log.w(TAG, "FLP runtime failure. Falling back to LocationManager.", runtimeException);
            forceLocationManagerFallback = true;
            fetchLocationManagerFallback(senderHint, existingPacketId);
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchLastLocationFallback(@Nullable String senderHint, long existingPacketId) {
        if (!hasFineLocationPermission()) {
            Log.w(TAG, "No precise location permission during fallback.");
            sendStatus("Unable to fetch precise GPS location");
            return;
        }
        if (forceLocationManagerFallback) {
            Log.i(TAG, "Skipping FLP lastLocation due to forced fallback flag.");
            fetchLocationManagerFallback(senderHint, existingPacketId);
            return;
        }
        locationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (isValidSosLocation(location)) {
                        Log.i(TAG, "FLP lastLocation success lat=" + location.getLatitude() + " lon=" + location.getLongitude());
                        sendSosWithResolvedLocation(location, senderHint, existingPacketId);
                    } else {
                        Log.w(TAG, "FLP lastLocation unavailable/invalid. Using LocationManager fallback.");
                        fetchLocationManagerFallback(senderHint, existingPacketId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Fused lastLocation failed. Using LocationManager fallback.", e);
                    if (isBrokerPackageFailure(e)) {
                        forceLocationManagerFallback = true;
                        Log.w(TAG, "Broker failure confirmed during lastLocation. Keeping forced fallback enabled.");
                    }
                    fetchLocationManagerFallback(senderHint, existingPacketId);
                });
    }

    private boolean isBrokerPackageFailure(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        String message = throwable.getMessage();
        return message != null && message.contains("Unknown calling package name 'com.google.android.gms'");
    }

    private void fetchLocationManagerFallback(@Nullable String senderHint, long existingPacketId) {
        if (!hasFineLocationPermission()) {
            Log.w(TAG, "LocationManager fallback blocked: no precise location permission.");
            sendStatus("Enable precise location to send SOS with GPS");
            return;
        }

        try {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) {
                Log.w(TAG, "LocationManager unavailable.");
                sendStatus("Unable to access device GPS");
                return;
            }

            Location gps = null;
            Location network = null;
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            Log.i(TAG, "LocationManager fallback providers gps=" + (gps != null) + " network=" + (network != null));

            Location best = chooseBetterLocation(gps, network);
            if (isValidSosLocation(best)) {
                Log.i(TAG, "LocationManager fallback selected lat=" + best.getLatitude() + " lon=" + best.getLongitude());
                sendSosWithResolvedLocation(best, senderHint, existingPacketId);
            } else {
                Log.w(TAG, "No valid last known location from providers. Requesting single GPS fix.");
                requestSingleGpsFix(locationManager, senderHint, existingPacketId);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "LocationManager fallback permission error", e);
            sendStatus("Unable to get GPS location permission");
        }
    }

    @SuppressLint("MissingPermission")
    private void requestSingleGpsFix(LocationManager locationManager, @Nullable String senderHint, long existingPacketId) {
        clearSingleFixRequest();
        String provider = null;
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            provider = LocationManager.GPS_PROVIDER;
        } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            provider = LocationManager.NETWORK_PROVIDER;
        }
        if (provider == null) {
            sendStatus("Enable GPS/location services, then retry SOS");
            return;
        }

        activeSingleFixManager = locationManager;
        activeSingleFixListener = new LocationListener() {
            @Override
            public void onLocationChanged(@Nullable Location location) {
                clearSingleFixRequest();
                if (isValidSosLocation(location)) {
                    Log.i(TAG, "Single GPS fix received lat=" + location.getLatitude() + " lon=" + location.getLongitude());
                    sendSosWithResolvedLocation(location, senderHint, existingPacketId);
                } else {
                    sendStatus("Unable to get valid GPS fix. Retry SOS.");
                }
            }
        };
        activeSingleFixTimeout = () -> {
            clearSingleFixRequest();
            sendStatus("GPS timeout. Move outdoors and retry SOS");
        };

        locationManager.requestSingleUpdate(provider, activeSingleFixListener, Looper.getMainLooper());
        handler.postDelayed(activeSingleFixTimeout, GPS_SINGLE_FIX_TIMEOUT_MS);
    }

    private void clearSingleFixRequest() {
        if (activeSingleFixManager != null && activeSingleFixListener != null) {
            try {
                activeSingleFixManager.removeUpdates(activeSingleFixListener);
            } catch (Exception ignored) {
                // Listener may already be removed.
            }
        }
        if (activeSingleFixTimeout != null) {
            handler.removeCallbacks(activeSingleFixTimeout);
        }
        activeSingleFixManager = null;
        activeSingleFixListener = null;
        activeSingleFixTimeout = null;
    }

    private void sendSosWithResolvedLocation(Location location, @Nullable String senderHint, long existingPacketId) {
        if (!isValidSosLocation(location)) {
            sendStatus("Unable to get valid GPS location");
            return;
        }
        queueOriginSos(location.getLatitude(), location.getLongitude(), senderHint, existingPacketId);
    }

    private boolean isValidSosLocation(@Nullable Location location) {
        if (location == null) {
            Log.d(TAG, "Location rejected: null");
            return false;
        }
        if (!isValidSosCoordinates(location.getLatitude(), location.getLongitude())) {
            Log.d(TAG, "Location rejected: invalid coordinates (" + location.getLatitude() + "," + location.getLongitude() + ")");
            return false;
        }
        long ageMs = Math.max(0L, System.currentTimeMillis() - location.getTime());
        if (ageMs > GPS_MAX_STALE_MS) {
            Log.d(TAG, "Location rejected: too stale (" + (ageMs / 1000) + "s > " + (GPS_MAX_STALE_MS / 1000) + "s)");
            return false;
        }
        if (location.hasAccuracy() && location.getAccuracy() > GPS_MAX_ACCURACY_METERS) {
            Log.d(TAG, "Location rejected: low accuracy (" + location.getAccuracy() + "m > " + GPS_MAX_ACCURACY_METERS + "m)");
            return false;
        }
        return true;
    }

    private boolean isValidSosCoordinates(double lat, double lon) {
        // Allow (0,0) specifically for Blind SOS (no GPS available)
        if (Math.abs(lat) < 0.000001d && Math.abs(lon) < 0.000001d) {
            return true;
        }
        return lat >= -90d && lat <= 90d && lon >= -180d && lon <= 180d;
    }

    private Location chooseBetterLocation(Location first, Location second) {
        if (first == null) return second;
        if (second == null) return first;

        long firstAge = System.currentTimeMillis() - first.getTime();
        long secondAge = System.currentTimeMillis() - second.getTime();
        if (Math.abs(firstAge - secondAge) > 30_000L) {
            return firstAge < secondAge ? first : second;
        }

        return first.getAccuracy() <= second.getAccuracy() ? first : second;
    }

    private boolean isPlayServicesAvailable() {
        int status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        return status == ConnectionResult.SUCCESS;
    }

    private long queueOriginSos(double lat, double lon, @Nullable String senderHint) {
        return queueOriginSos(lat, lon, senderHint, -1L);
    }

    private long queueOriginSos(double lat, double lon, @Nullable String senderHint, long existingPacketId) {
        int nowEpoch = (int) Instant.now().getEpochSecond();
        String senderName = resolveSenderName(senderHint);

        SosPacket packet;
        if (existingPacketId != -1L) {
            packet = SosPacket.createSos(existingPacketId, lat, lon, nowEpoch, senderName);
        } else {
            packet = SosPacket.createSos(lat, lon, nowEpoch, senderName);
        }

        synchronized (lock) {
            seenSosAt.put(packet.messageId, SystemClock.elapsedRealtime());
            originStates.put(packet.messageId, new OriginState(SystemClock.elapsedRealtime()));
            outbound.addFirst(new QueuedPacket(packet, SystemClock.elapsedRealtime() + 10_000L, 12, true));
        }
        Log.i(TAG, "Queued origin SOS: " + packetSummary(packet) + " lat=" + lat + " lon=" + lon + " sender=" + senderName + " refined=" + (existingPacketId != -1L));
        Log.i(SOS_TRACE_TAG, "ORIGIN_SOS id=" + packet.shortId()
                + " sender=" + senderName
                + " lat=" + lat
                + " lon=" + lon
                + " epoch=" + nowEpoch
                + " refined=" + (existingPacketId != -1L));

        if (existingPacketId == -1L && NetworkUtils.isOnline(this)) {
            dispatchOnlineSosRecipients(packet);
        }

        updateNotification("Broadcasting SOS " + packet.shortId());
        sendStatus(existingPacketId != -1L ? "SOS location refined" : "SOS broadcast started");
        return packet.messageId;
    }

    private void onPacketReceived(SosPacket packet, boolean fullFrameReceived) {
        Log.i(TAG, "Packet received transport=" + (fullFrameReceived ? "WIFI_DIRECT" : "BLE_BEACON")
                + " data=" + (fullFrameReceived ? packetSummary(packet) : beaconSummary(packet)));
        if (packet.type == SosPacket.TYPE_ACK) {
            if (fullFrameReceived) {
                onAckReceived(packet.messageId);
            } else {
                Log.d(TAG, "Ignoring BLE ACK beacon for delivery accounting: id=" + packet.shortId());
            }
            return;
        }

        boolean isNew;
        boolean shouldRelay;
        synchronized (lock) {
            Long seenAt = seenSosAt.get(packet.messageId);
            isNew = seenAt == null;
            seenSosAt.put(packet.messageId, SystemClock.elapsedRealtime());
            if (fullFrameReceived) {
                shouldRelay = relayedFullAt.putIfAbsent(packet.messageId, SystemClock.elapsedRealtime()) == null;
            } else {
                shouldRelay = isNew;
            }
        }

        if (fullFrameReceived) {
            queueAck(packet.messageId);
        }

        if (isNew || fullFrameReceived) {
            SosAlertRecord alertRecord = SosAlertStore.saveOrUpdateAlert(this, packet, fullFrameReceived);
            Log.i(SOS_TRACE_TAG, "ALERT_STORE_WRITE id=" + packet.shortId()
                    + " transport=" + (fullFrameReceived ? "WIFI_FULL" : "BLE_BEACON")
                    + " packet_latMilli=" + packet.getLatMilli()
                    + " packet_lonMilli=" + packet.getLonMilli()
                    + " packet_sender=" + packet.getSenderName()
                    + " stored_hasLocation=" + alertRecord.hasLocation()
                    + " stored_latMilli=" + alertRecord.getLatMilli()
                    + " stored_lonMilli=" + alertRecord.getLonMilli()
                    + " stored_sender=" + alertRecord.getSenderName());
            notifyAlertListUpdated();
            if (isNew) {
                showReceivedSosNotification(packet, alertRecord);
            }
        }

        if (!shouldRelay) {
            Log.d(TAG, "Duplicate packet ignored after ACK queue: id=" + packet.shortId());
            return;
        }

        sendStatus(fullFrameReceived
                ? "Received nearby SOS, relaying..."
                : "Detected SOS beacon, relaying mesh alert...");

        if (packet.canRelay()) {
            SosPacket relay = packet.asRelay();
            synchronized (lock) {
                outbound.addLast(new QueuedPacket(relay, SystemClock.elapsedRealtime() + 15_000L, 8, fullFrameReceived));
            }
            Log.i(TAG, "Queued relay packet transportHint=" + (fullFrameReceived ? "FULL" : "BEACON")
                    + " data=" + (fullFrameReceived ? packetSummary(relay) : beaconSummary(relay)));
        } else {
            Log.d(TAG, "Packet not relayed due to TTL exhaustion: id=" + packet.shortId());
        }
    }

    private void onAckReceived(long messageId) {
        synchronized (lock) {
            OriginState state = originStates.get(messageId);
            if (state == null) {
                return;
            }
            state.ackCount++;
            Log.i(TAG, "ACK received for id=" + String.format(java.util.Locale.US, "%012X", messageId) + " count=" + state.ackCount);
            if (!state.firstAckReported) {
                state.firstAckReported = true;
                sendStatus("SOS delivered to nearby device");
                updateNotification("SOS delivered - mesh relaying");
            }
        }
    }

    private void queueAck(long messageId) {
        SosPacket ack = SosPacket.createAck(messageId);
        synchronized (lock) {
            outbound.addFirst(new QueuedPacket(ack, SystemClock.elapsedRealtime() + 6_000L, 4, true));
        }
        Log.i(TAG, "Queued ACK for id=" + String.format(java.util.Locale.US, "%012X", messageId));
    }

    private QueuedPacket pickNextPacketLocked() {
        long now = SystemClock.elapsedRealtime();
        Iterator<QueuedPacket> iterator = outbound.iterator();
        while (iterator.hasNext()) {
            QueuedPacket candidate = iterator.next();
            if (candidate.expireAtMs < now || candidate.remaining <= 0) {
                iterator.remove();
                continue;
            }
            iterator.remove();
            candidate.remaining--;
            if (candidate.remaining > 0) {
                outbound.addLast(candidate);
            }
            return candidate;
        }
        return null;
    }

    private void dispatchNextFrame() {
        QueuedPacket queued;
        synchronized (lock) {
            queued = pickNextPacketLocked();
        }
        if (queued == null) {
            if (wifiDirectTransport != null && wifiDirectTransport.isReady()) {
                broadcastIdentityIfDue(false);
            }
            return;
        }

        refreshBluetoothIfNeeded("dispatch");

        int battery = getBatteryPercent();
        BluetoothAdapter adapter = getBluetoothAdapter();
        boolean btEnabled = adapter != null && adapter.isEnabled();
        boolean blePermission = canAdvertise();
        boolean bleReady = advertiser != null && blePermission && btEnabled && !isBleInCooldown();
        boolean wifiReady = wifiDirectTransport != null && wifiDirectTransport.isReady();
        int blePayloadBytes = SosCodec.encodeBleBeacon(queued.packet).length;
        TransportSelector.Transport chosen = transportSelector.select(
                blePayloadBytes,
                battery,
                bleReady,
                wifiReady
        );

        Log.d(TAG, "SOS_DISPATCH packet=" + packetSummary(queued.packet)
                + " chosen=" + chosen
                + " bleReady=" + bleReady
                + " wifiReady=" + wifiReady
                + " btEnabled=" + btEnabled
                + " blePermission=" + blePermission
                + " advertiserPresent=" + (advertiser != null)
                + " bleCooldownMs=" + Math.max(0L, bleUnavailableUntilMs - SystemClock.elapsedRealtime())
                + " battery=" + battery
                + " retriesLeft=" + queued.remaining);

        if (!bleReady) {
            Log.d(TAG, "SOS_BLE_BLOCKED packet=" + packetSummary(queued.packet)
                    + " reason=" + buildBleBlockedReason(btEnabled, blePermission));
        }

        byte[] fullFrame = queued.hasFullPayload ? SosCodec.encodeFullFrame(queued.packet) : null;
        boolean sentOnWifi = false;
        if (chosen == TransportSelector.Transport.WIFI_DIRECT && wifiDirectTransport != null && queued.hasFullPayload) {
            wifiDirectTransport.sendFrame(fullFrame);
            sentOnWifi = true;
            Log.d(TAG, "SOS_WIFI_SEND packet=" + packetSummary(queued.packet));
            Log.d(SOS_TRACE_TAG, "TX_WIFI id=" + queued.packet.shortId() + " reason=chosen_wifi");
        }

        if (chosen == TransportSelector.Transport.BLE && bleReady) {
            if (queued.packet.type == SosPacket.TYPE_SOS && wifiReady && wifiDirectTransport != null && queued.hasFullPayload && !sentOnWifi) {
                wifiDirectTransport.sendFrame(fullFrame);
                sentOnWifi = true;
                Log.d(SOS_TRACE_TAG, "TX_WIFI id=" + queued.packet.shortId() + " reason=parallel_with_ble");
            }
            advertiseFrame(queued);
            Log.d(SOS_TRACE_TAG, "TX_BLE id=" + queued.packet.shortId() + " reason=chosen_ble");
            return;
        }

        if (!wifiReady && bleReady) {
            Log.d(TAG, "SOS_FALLBACK using BLE because Wi-Fi Direct not ready. packet=" + packetSummary(queued.packet));
            advertiseFrame(queued);
            Log.d(SOS_TRACE_TAG, "TX_BLE id=" + queued.packet.shortId() + " reason=wifi_unavailable");
            return;
        }

        if (queued.packet.type == SosPacket.TYPE_SOS) {
            if (!sentOnWifi && wifiDirectTransport != null && queued.hasFullPayload) {
                Log.d(TAG, "SOS_WIFI_ATTEMPT packet=" + packetSummary(queued.packet));
                wifiDirectTransport.sendFrame(fullFrame);
                Log.d(SOS_TRACE_TAG, "TX_WIFI id=" + queued.packet.shortId() + " reason=final_attempt");
            }
            if (bleReady) {
                Log.d(TAG, "SOS_BLE_ATTEMPT packet=" + packetSummary(queued.packet));
                advertiseFrame(queued);
                Log.d(SOS_TRACE_TAG, "TX_BLE id=" + queued.packet.shortId() + " reason=final_attempt");
            } else if (!wifiReady) {
                Log.w(TAG, "SOS_NOT_SENT packet=" + packetSummary(queued.packet)
                        + " reason=no_transport_ready");
                Log.w(SOS_TRACE_TAG, "TX_FAIL id=" + queued.packet.shortId() + " reason=no_transport_ready");
            }
        }
    }

    private void broadcastIdentityIfDue(boolean force) {
        if (meshCoordinator == null || wifiDirectTransport == null) {
            Log.d(MESH_TAG, "identityBroadcast: skipped coordinator_or_transport_missing");
            return;
        }
        if (!wifiDirectTransport.isReady()) {
            identityBroadcastPending = true;
            Log.d(MESH_TAG, "identityBroadcast: skipped transport_not_ready pending=true");
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (!force && !identityBroadcastPending && now < nextIdentityBroadcastAtMs) {
            Log.d(MESH_TAG, "identityBroadcast: skipped cooldown remaining_ms=" + (nextIdentityBroadcastAtMs - now));
            return;
        }
        byte[] frame = meshCoordinator.buildIdentityFrame();
        Log.d(MESH_TAG, "identityBroadcast: sending frame_len=" + frame.length + " force=" + force);
        identityBroadcastPending = false;
        wifiDirectTransport.sendFrame(frame);
        nextIdentityBroadcastAtMs = now + IDENTITY_BROADCAST_INTERVAL_MS;
    }

    private String buildBleBlockedReason(boolean btEnabled, boolean blePermission) {
        if (!blePermission) {
            return "permission_missing";
        }
        if (!btEnabled) {
            return "bluetooth_off";
        }
        if (advertiser == null) {
            return "advertiser_null";
        }
        if (isBleInCooldown()) {
            return "cooldown";
        }
        return "unknown";
    }

    @SuppressLint("MissingPermission")
    private void advertiseFrame(QueuedPacket queued) {
        refreshBluetoothIfNeeded("advertise_frame");
        BluetoothAdapter adapter = getBluetoothAdapter();
        boolean btEnabled = adapter != null && adapter.isEnabled();
        if (advertiser == null) {
            Log.w(TAG, "SOS_BLE_SKIP packet=" + packetSummary(queued.packet) + " reason=advertiser_null");
            return;
        }
        if (!canAdvertise()) {
            Log.w(TAG, "SOS_BLE_SKIP packet=" + packetSummary(queued.packet) + " reason=advertise_permission_missing");
            return;
        }
        if (!btEnabled) {
            Log.w(TAG, "SOS_BLE_SKIP packet=" + packetSummary(queued.packet) + " reason=bluetooth_off");
            return;
        }
        if (activeAdvertiseCallback != null) {
            Log.d(TAG, "SOS_BLE_SKIP packet=" + packetSummary(queued.packet) + " reason=advertiser_busy");
            return;
        }

        byte[] beacon = SosCodec.encodeBleBeacon(queued.packet);
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(SosCodec.BLE_MANUFACTURER_ID, beacon)
                .build();

        activeAdvertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                bleUnavailableUntilMs = 0L;
                Log.i(TAG, "SOS_BLE_START_SUCCESS packet=" + packetSummary(queued.packet));
            }

            @Override
            public void onStartFailure(int errorCode) {
                bleUnavailableUntilMs = SystemClock.elapsedRealtime() + BLE_RETRY_COOLDOWN_MS;
                Log.w(TAG, "SOS_BLE_START_FAIL packet=" + packetSummary(queued.packet)
                        + " code=" + errorCode
                        + " label=" + advertiseFailureLabel(errorCode)
                        + " cooldownMs=" + BLE_RETRY_COOLDOWN_MS);
                if (queued.packet.type == SosPacket.TYPE_SOS && queued.hasFullPayload && wifiDirectTransport != null) {
                    Log.w(TAG, "SOS_BLE_FAIL_WIFI_FALLBACK packet=" + packetSummary(queued.packet));
                    wifiDirectTransport.sendFrame(SosCodec.encodeFullFrame(queued.packet));
                }
                stopActiveAdvertiser();
            }
        };

        advertiser.startAdvertising(settings, data, activeAdvertiseCallback);
        Log.d(TAG, "SOS_BLE_BEACON_SENT packet=" + beaconSummary(queued.packet));
        handler.postDelayed(this::stopActiveAdvertiser, ADVERTISE_WINDOW_MS);
    }

    @SuppressLint("MissingPermission")
    private void stopActiveAdvertiser() {
        if (advertiser == null || activeAdvertiseCallback == null || !canAdvertise()) {
            activeAdvertiseCallback = null;
            return;
        }
        try {
            advertiser.stopAdvertising(activeAdvertiseCallback);
        } catch (Exception ignored) {
            // Advertiser might already be stopped.
        }
        activeAdvertiseCallback = null;
    }

    private String bluetoothStateLabel(int state) {
        switch (state) {
            case BluetoothAdapter.STATE_ON:
                return "ON";
            case BluetoothAdapter.STATE_OFF:
                return "OFF";
            case BluetoothAdapter.STATE_TURNING_ON:
                return "TURNING_ON";
            case BluetoothAdapter.STATE_TURNING_OFF:
                return "TURNING_OFF";
            default:
                return "UNKNOWN(" + state + ")";
        }
    }

    private void pruneCaches() {
        long now = SystemClock.elapsedRealtime();
        synchronized (lock) {
            Iterator<Map.Entry<Long, Long>> seenIt = seenSosAt.entrySet().iterator();
            while (seenIt.hasNext()) {
                Map.Entry<Long, Long> entry = seenIt.next();
                if (now - entry.getValue() > MAX_SEEN_AGE_MS) {
                    seenIt.remove();
                }
            }

            Iterator<Map.Entry<Long, OriginState>> originIt = originStates.entrySet().iterator();
            while (originIt.hasNext()) {
                Map.Entry<Long, OriginState> entry = originIt.next();
                if (now - entry.getValue().startedAtMs > MAX_SEEN_AGE_MS) {
                    originIt.remove();
                }
            }

            Iterator<Map.Entry<Long, Long>> relayedFullIt = relayedFullAt.entrySet().iterator();
            while (relayedFullIt.hasNext()) {
                Map.Entry<Long, Long> entry = relayedFullIt.next();
                if (now - entry.getValue() > MAX_SEEN_AGE_MS) {
                    relayedFullIt.remove();
                }
            }
        }
    }

    private String resolveSenderName(@Nullable String senderHint) {
        if (senderHint != null) {
            String trimmed = senderHint.trim();
            if (!trimmed.isEmpty()) {
                cacheSenderName(trimmed);
                return trimmed;
            }
        }

        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                String cached = readCachedSenderName();
                return cached.isEmpty() ? "HLDSN User" : cached;
            }
            if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
                String displayName = user.getDisplayName().trim();
                cacheSenderName(displayName);
                return displayName;
            }
            if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
                String email = user.getEmail().trim();
                String fallbackName = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
                if (!fallbackName.isEmpty()) {
                    cacheSenderName(fallbackName);
                    return fallbackName;
                }
            }

            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .get()
                    .addOnSuccessListener(this::cacheSenderNameFromUserDoc)
                    .addOnFailureListener(e -> Log.d(TAG, "Sender profile fetch failed", e));
        } catch (Exception ignored) {
            // Keep SOS send path resilient even if auth lookup fails.
        }

        String cached = readCachedSenderName();
        return cached.isEmpty() ? "HLDSN User" : cached;
    }

    private void cacheSenderNameFromUserDoc(DocumentSnapshot documentSnapshot) {
        if (documentSnapshot == null || !documentSnapshot.exists()) {
            return;
        }
        String single = safeTrim(documentSnapshot.getString("name"));
        if (!single.isEmpty()) {
            cacheSenderName(single);
            return;
        }

        String first = safeTrim(documentSnapshot.getString("firstName"));
        String last = safeTrim(documentSnapshot.getString("lastName"));
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) {
            cacheSenderName(full);
        }
    }

    private void cacheSenderName(String name) {
        String clean = safeTrim(name);
        if (clean.isEmpty()) {
            return;
        }
        getSharedPreferences(SOS_PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_CACHED_SENDER_NAME, clean)
                .apply();
    }

    private String readCachedSenderName() {
        return safeTrim(getSharedPreferences(SOS_PREFS, MODE_PRIVATE)
                .getString(KEY_CACHED_SENDER_NAME, ""));
    }

    private String safeTrim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private void dispatchOnlineSosRecipients(SosPacket packet) {
        FirebaseUser sender = FirebaseAuth.getInstance().getCurrentUser();
        if (sender == null) {
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection(USERS_COLLECTION)
                .get()
                .addOnSuccessListener(snapshot -> {
                    java.util.List<RecipientCandidate> volunteers = new java.util.ArrayList<>();
                    java.util.List<RecipientCandidate> nearbyUsers = new java.util.ArrayList<>();
                    java.util.List<RecipientCandidate> nearbyNgos = new java.util.ArrayList<>();

                    double originLat = packet.getLatMilli() / 1000.0d;
                    double originLon = packet.getLonMilli() / 1000.0d;
                    boolean hasOriginLocation = packet.getLatMilli() != 0 || packet.getLonMilli() != 0;

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String uid = doc.getId();
                        if (uid == null || uid.isEmpty() || uid.equals(sender.getUid())) {
                            continue;
                        }

                        String role = normalizeRole(doc.getString("role"));
                        if (!isCandidateActive(doc)) {
                            continue;
                        }

                        Double lat = readCoordinate(doc, "latitude", "lat", "locationLat", "lastKnownLatitude");
                        Double lon = readCoordinate(doc, "longitude", "lon", "locationLon", "lastKnownLongitude");
                        if (lat == null || lon == null) {
                            continue;
                        }

                        double distance = hasOriginLocation
                                ? distanceMeters(originLat, originLon, lat, lon)
                                : Double.MAX_VALUE;

                        RecipientCandidate candidate = new RecipientCandidate(uid, role, lat, lon, distance);
                        if (ROLE_VOLUNTEER.equals(role)) {
                            volunteers.add(candidate);
                        } else if (ROLE_USER.equals(role)) {
                            if (distance <= ONLINE_RADIUS_METERS) {
                                nearbyUsers.add(candidate);
                            }
                        } else if (ROLE_NGO_ADMIN.equals(role)) {
                            if (distance <= ONLINE_RADIUS_METERS) {
                                nearbyNgos.add(candidate);
                            }
                        }
                    }

                    volunteers.sort((a, b) -> Double.compare(a.distanceMeters, b.distanceMeters));
                    nearbyUsers.sort((a, b) -> Double.compare(a.distanceMeters, b.distanceMeters));
                    nearbyNgos.sort((a, b) -> Double.compare(a.distanceMeters, b.distanceMeters));

                    Set<String> targets = new HashSet<>();
                    pickRecipients(volunteers, MAX_NEARBY_VOLUNTEERS, targets);
                    pickRecipients(nearbyUsers, Integer.MAX_VALUE, targets);
                    pickRecipients(nearbyNgos, MAX_NEARBY_NGO, targets);

                    if (targets.isEmpty()) {
                        sendStatus("SOS sent online: no nearby active recipients found");
                        return;
                    }

                    String senderName = packet.getSenderName().isEmpty() ? "HLDSN User" : packet.getSenderName();
                    String subtitle = (packet.getLatMilli() != 0 || packet.getLonMilli() != 0)
                            ? String.format(Locale.US, "Location: %.6f, %.6f", packet.getLatMilli() / 1000.0d, packet.getLonMilli() / 1000.0d)
                            : "Location unavailable";

                    for (String targetUid : targets) {
                        String inboxDocId = String.format(Locale.US, "%d_%s", packet.messageId, targetUid);
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("messageIdLong", packet.messageId);
                        payload.put("messageId", packet.shortId());
                        payload.put("senderUid", sender.getUid());
                        payload.put("senderName", senderName);
                        payload.put("recipientUid", targetUid);
                        payload.put("latMilli", packet.getLatMilli());
                        payload.put("lonMilli", packet.getLonMilli());
                        payload.put("epochSeconds", packet.getEpochSeconds());
                        payload.put("title", senderName.isEmpty() ? "Nearby SOS Alert" : "SOS from " + senderName);
                        payload.put("subtitle", subtitle);
                        payload.put("status", "pending");
                        payload.put("createdAt", FieldValue.serverTimestamp());

                        db.collection(SOS_INBOX_COLLECTION)
                                .document(inboxDocId)
                                .set(payload, SetOptions.merge());
                    }

                    sendStatus("SOS sent online to " + targets.size() + " nearby active recipients");
                })
                .addOnFailureListener(error -> Log.w(TAG, "Online SOS routing failed", error));
    }

    private void pickRecipients(java.util.List<RecipientCandidate> candidates, int limit, Set<String> targets) {
        int added = 0;
        for (RecipientCandidate candidate : candidates) {
            if (added >= limit) {
                break;
            }
            if (targets.add(candidate.uid)) {
                added++;
            }
        }
    }

    private String normalizeRole(@Nullable String rawRole) {
        if (rawRole == null) {
            return ROLE_USER;
        }
        String normalized = rawRole.trim().toLowerCase(Locale.US).replace('-', '_').replace(' ', '_');
        if ("ngoadmin".equals(normalized)) {
            return ROLE_NGO_ADMIN;
        }
        if (ROLE_VOLUNTEER.equals(normalized)) {
            return ROLE_VOLUNTEER;
        }
        if (ROLE_NGO_ADMIN.equals(normalized)) {
            return ROLE_NGO_ADMIN;
        }
        return ROLE_USER;
    }

    private boolean isCandidateActive(DocumentSnapshot doc) {
        Boolean isActive = doc.getBoolean("isActive");
        if (Boolean.TRUE.equals(isActive)) {
            return true;
        }
        Long ageMs = activeAgeMs(doc, "lastActiveAt", "lastSeen", "updated_at", "mesh_synced_at");
        if (ageMs != null) {
            return ageMs <= ACTIVE_WINDOW_MS;
        }
        return isActive == null;
    }

    @Nullable
    private Long activeAgeMs(DocumentSnapshot doc, String... keys) {
        long now = System.currentTimeMillis();
        for (String key : keys) {
            Object value = doc.get(key);
            Long timestampMs = toTimestampMs(value);
            if (timestampMs != null) {
                return Math.max(0L, now - timestampMs);
            }
        }
        return null;
    }

    @Nullable
    private Long toTimestampMs(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate().getTime();
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    @Nullable
    private Double readCoordinate(DocumentSnapshot doc, String... keys) {
        for (String key : keys) {
            Object value = doc.get(key);
            Double parsed = toDouble(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    @Nullable
    private Double toDouble(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadius = 6_371_000d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private void startPresenceHeartbeat() {
        handler.removeCallbacks(presenceHeartbeat);
        publishPresenceBestEffort();
        handler.postDelayed(presenceHeartbeat, PRESENCE_HEARTBEAT_MS);
    }

    private void publishPresenceBestEffort() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> patch = new HashMap<>();
        patch.put("isActive", true);
        patch.put("lastActiveAt", FieldValue.serverTimestamp());

        db.collection(USERS_COLLECTION)
                .document(user.getUid())
                .set(patch, SetOptions.merge());

        if (!hasLocationPermission()) {
            return;
        }

        try {
            @SuppressLint("MissingPermission")
            com.google.android.gms.tasks.Task<Location> task = locationClient.getLastLocation();
            task.addOnSuccessListener(location -> {
                if (!isValidSosLocation(location)) {
                    return;
                }
                Map<String, Object> locationPatch = new HashMap<>();
                locationPatch.put("latitude", location.getLatitude());
                locationPatch.put("longitude", location.getLongitude());
                locationPatch.put("lastLocationAt", FieldValue.serverTimestamp());
                db.collection(USERS_COLLECTION)
                        .document(user.getUid())
                        .set(locationPatch, SetOptions.merge());
            });
        } catch (Exception ignored) {
            // Presence update is best-effort only.
        }
    }

    private void markPresenceInactive() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }
        Map<String, Object> patch = new HashMap<>();
        patch.put("isActive", false);
        patch.put("lastInactiveAt", FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance()
                .collection(USERS_COLLECTION)
                .document(user.getUid())
                .set(patch, SetOptions.merge());
    }

    private void startRemoteSosInboxListener() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }
        if (remoteSosInboxListener != null) {
            remoteSosInboxListener.remove();
            remoteSosInboxListener = null;
        }

        remoteSosInboxListener = FirebaseFirestore.getInstance()
                .collection(SOS_INBOX_COLLECTION)
                .whereEqualTo("recipientUid", user.getUid())
                .whereEqualTo("status", "pending")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) {
                        return;
                    }
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String docId = doc.getId();
                        if (docId == null || docId.isEmpty() || processedRemoteInboxDocs.contains(docId)) {
                            continue;
                        }
                        processedRemoteInboxDocs.add(docId);
                        processRemoteSosInboxDocument(doc);
                        if (processedRemoteInboxDocs.size() > 512) {
                            processedRemoteInboxDocs.clear();
                        }
                    }
                });
    }

    private void processRemoteSosInboxDocument(DocumentSnapshot doc) {
        Long messageIdLong = doc.getLong("messageIdLong");
        if (messageIdLong == null) {
            return;
        }

        int latMilli = toInt(doc.get("latMilli"));
        int lonMilli = toInt(doc.get("lonMilli"));
        int epochSeconds = toInt(doc.get("epochSeconds"));
        String senderName = safeTrim(doc.getString("senderName"));

        SosPacket packet = SosPacket.createSos(
                messageIdLong,
                latMilli / 1000.0d,
                lonMilli / 1000.0d,
                epochSeconds > 0 ? epochSeconds : (int) Instant.now().getEpochSecond(),
                senderName
        );

        SosAlertRecord alertRecord = SosAlertStore.saveOrUpdateAlert(this, packet, true);
        notifyAlertListUpdated();
        showReceivedSosNotification(packet, alertRecord);

        Map<String, Object> deliveredPatch = new HashMap<>();
        deliveredPatch.put("status", "delivered");
        deliveredPatch.put("deliveredAt", FieldValue.serverTimestamp());
        doc.getReference().set(deliveredPatch, SetOptions.merge());
    }

    private int toInt(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("HLDSN SOS Mesh")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "SOS Mesh",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Background SOS mesh relay");
        manager.createNotificationChannel(channel);

        NotificationChannel alertChannel = new NotificationChannel(
                ALERT_CHANNEL_ID,
                "Received SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
        );
        alertChannel.setDescription("Popup alerts for nearby SOS detections");
        manager.createNotificationChannel(alertChannel);

        NotificationChannel meshAlertChannel = new NotificationChannel(
                MESH_ALERT_CHANNEL_ID,
                "Mesh Messages",
                NotificationManager.IMPORTANCE_HIGH
        );
        meshAlertChannel.setDescription("Popup alerts for encrypted offline mesh messages");
        manager.createNotificationChannel(meshAlertChannel);
    }

    private void sendStatus(String status) {
        Log.i(TAG, "STATUS: " + status);
        Intent intent = new Intent(ACTION_SOS_STATUS);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_STATUS, status);
        sendBroadcast(intent);
    }

    private void notifyAlertListUpdated() {
        Intent intent = new Intent(ACTION_SOS_ALERTS_UPDATED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void showReceivedSosNotification(SosPacket packet, SosAlertRecord alertRecord) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted. Skipping SOS popup notification.");
            return;
        }

        Intent openIntent = new Intent(this, HomePageActivity.class);
        openIntent.setAction(ACTION_OPEN_NOTIFICATIONS);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                packet.shortId().hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(alertRecord.getTitle())
                .setContentText(alertRecord.getSubtitle())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(alertRecord.getSubtitle()))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(5000 + Math.abs(packet.shortId().hashCode() % 1000), notification);
        }
    }

    private void showReceivedMeshNotification(String messageId, String senderLabel, String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Notification notification = new NotificationCompat.Builder(this, MESH_ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("New offline message")
                .setContentText(senderLabel + ": " + text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(senderLabel + ": " + text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(MESH_NOTIFICATION_BASE_ID + Math.abs(messageId.hashCode() % 1000), notification);
        }
    }

    private boolean isBleInCooldown() {
        return bleUnavailableUntilMs > SystemClock.elapsedRealtime();
    }

    private String advertiseFailureLabel(int errorCode) {
        switch (errorCode) {
            case AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE:
                return "DATA_TOO_LARGE";
            case AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                return "TOO_MANY_ADVERTISERS";
            case AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED:
                return "ALREADY_STARTED";
            case AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR:
                return "INTERNAL_ERROR";
            case AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                return "FEATURE_UNSUPPORTED";
            default:
                return "UNKNOWN";
        }
    }

    private String packetSummary(SosPacket packet) {
        if (packet == null) {
            return "null";
        }
        String type = packet.type == SosPacket.TYPE_ACK ? "ACK" : "SOS";
        String sender = packet.getSenderName().isEmpty() ? "unknown" : packet.getSenderName();
        String coords = (packet.getLatMilli() == 0 && packet.getLonMilli() == 0)
                ? ""
                : " lat=" + (packet.getLatMilli() / 1000.0d) + " lon=" + (packet.getLonMilli() / 1000.0d);
        return type + "#" + packet.shortId() + " hop=" + packet.hop + " ttl=" + packet.ttl
                + " sender=" + sender + coords;
    }

    private String beaconSummary(SosPacket packet) {
        if (packet == null) {
            return "null";
        }
        String type = packet.type == SosPacket.TYPE_ACK ? "ACK_BEACON" : "SOS_BEACON";
        return type + "#" + packet.shortId() + " hop=" + packet.hop + " ttl=" + packet.ttl;
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasFineLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean canScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean canAdvertise() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
                == PackageManager.PERMISSION_GRANTED;
    }

    private int getBatteryPercent() {
        BatteryManager batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager == null) {
            return 100;
        }
        int value = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return value > 0 ? value : 100;
    }

    private static final class RecipientCandidate {
        final String uid;
        final String role;
        final double lat;
        final double lon;
        final double distanceMeters;

        RecipientCandidate(String uid, String role, double lat, double lon, double distanceMeters) {
            this.uid = uid;
            this.role = role;
            this.lat = lat;
            this.lon = lon;
            this.distanceMeters = distanceMeters;
        }
    }

    private static final class QueuedPacket {
        final SosPacket packet;
        final long expireAtMs;
        final boolean hasFullPayload;
        int remaining;

        QueuedPacket(SosPacket packet, long expireAtMs, int remaining, boolean hasFullPayload) {
            this.packet = packet;
            this.expireAtMs = expireAtMs;
            this.hasFullPayload = hasFullPayload;
            this.remaining = remaining;
        }
    }

    private static final class OriginState {
        final long startedAtMs;
        int ackCount;
        boolean firstAckReported;

        OriginState(long startedAtMs) {
            this.startedAtMs = startedAtMs;
        }
    }
}







