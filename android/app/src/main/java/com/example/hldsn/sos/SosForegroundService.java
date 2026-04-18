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

import com.example.hldsn.R;
import com.example.hldsn.home.HomePageActivity;
import com.example.hldsn.incident_report_module.ChatIdUtil;
import com.example.hldsn.incident_report_module.ChatMessage;
import com.example.hldsn.incident_report_module.OfflineChatStore;
import com.example.hldsn.notification_module.SosAlertRecord;
import com.example.hldsn.notification_module.SosAlertStore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.Timestamp;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public class SosForegroundService extends Service {

    public static final String ACTION_START_MESH = "com.example.hldsn.sos.ACTION_START_MESH";
    public static final String ACTION_TRIGGER_SOS = "com.example.hldsn.sos.ACTION_TRIGGER_SOS";
    public static final String ACTION_SEND_OFFLINE_CHAT = "com.example.hldsn.sos.ACTION_SEND_OFFLINE_CHAT";
    public static final String ACTION_SOS_STATUS = "com.example.hldsn.sos.ACTION_SOS_STATUS";
    public static final String ACTION_SOS_ALERTS_UPDATED = "com.example.hldsn.sos.ACTION_SOS_ALERTS_UPDATED";
    public static final String ACTION_OPEN_NOTIFICATIONS = "com.example.hldsn.sos.ACTION_OPEN_NOTIFICATIONS";
    public static final String ACTION_OFFLINE_CHAT_STATUS = "com.example.hldsn.sos.ACTION_OFFLINE_CHAT_STATUS";
    public static final String ACTION_OFFLINE_CHAT_RECEIVED = "com.example.hldsn.sos.ACTION_OFFLINE_CHAT_RECEIVED";
    public static final String EXTRA_STATUS = "extra_status";
    public static final String EXTRA_CHAT_MESSAGE_ID = "extra_chat_message_id";
    public static final String EXTRA_CHAT_TARGET_UID = "extra_chat_target_uid";
    public static final String EXTRA_CHAT_SOURCE_UID = "extra_chat_source_uid";
    public static final String EXTRA_CHAT_TEXT = "extra_chat_text";
    public static final String EXTRA_CHAT_QUICK_TYPE = "extra_chat_quick_type";
    public static final String EXTRA_CHAT_STATUS = "extra_chat_status";
    public static final String EXTRA_CHAT_ID = "extra_chat_id";
    public static final String EXTRA_CHAT_SENDER_NAME = "extra_chat_sender_name";
    public static final String EXTRA_CHAT_TIMESTAMP_MS = "extra_chat_timestamp_ms";

    private static final String TAG = "SosFgService";
    private static final String CHANNEL_ID = "sos_mesh_channel";
    private static final String ALERT_CHANNEL_ID = "sos_received_alerts";
    private static final String CHAT_ALERT_CHANNEL_ID = "offline_chat_alerts";
    private static final int NOTIFICATION_ID = 3101;

    private static final long TICK_MS = 800L;
    private static final long ADVERTISE_WINDOW_MS = 650L;
    private static final long MAX_SEEN_AGE_MS = 2 * 60_000L;
    private static final long BLE_RETRY_COOLDOWN_MS = 10_000L;
    private static final long BLE_REINIT_MIN_INTERVAL_MS = 1_500L;
    private static final long CHAT_FAIL_TIMEOUT_MS = 35_000L;
    private static final long CHAT_RETRY_INTERVAL_MS = 5_000L;
    private static final long CHAT_MAX_LIFETIME_MS = 12 * 60 * 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final ArrayDeque<QueuedPacket> outbound = new ArrayDeque<>();
    private final Map<Long, Long> seenSosAt = new HashMap<>();
    private final Map<Long, OriginState> originStates = new HashMap<>();
    private final Map<Long, Long> relayedFullAt = new HashMap<>();
    private final Map<Long, PendingChatState> pendingChats = new HashMap<>();
    private final Map<String, Long> seenChatAcksAt = new HashMap<>();

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

    private final Runnable meshTick = new Runnable() {
        @Override
        public void run() {
            try {
                tickPendingChats();
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
        Log.i(TAG, "Service created. Initializing mesh transports.");
        wifiDirectTransport = new WifiDirectTransport(this, new WifiDirectTransport.Callback() {
            @Override
            public void onFrameReceived(byte[] frame) {
                SosPacket packet = SosCodec.decodeFullFrame(frame);
                if (packet != null) {
                    Log.i(TAG, "Wi-Fi Direct full frame received: " + packetSummary(packet));
                    onPacketReceived(packet, true);
                }
            }

            @Override
            public void onStatus(String status) {
                sendStatus(status);
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
            triggerSos();
        } else if (ACTION_SEND_OFFLINE_CHAT.equals(action)) {
            startMesh();
            handleOfflineChatSend(intent);
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
        handler.post(meshTick);
        sendStatus("HLDSN SOS mesh ready");
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

    private void triggerSos() {
        Log.i(TAG, "SOS trigger requested.");
        if (!hasLocationPermission()) {
            Log.w(TAG, "SOS blocked: no location permission.");
            sendStatus("Location permission required for SOS");
            return;
        }

        if (forceLocationManagerFallback) {
            Log.i(TAG, "Using forced LocationManager fallback (FLP disabled for session).");
            fetchLocationManagerFallback();
            return;
        }

        if (!isPlayServicesAvailable()) {
            Log.w(TAG, "Google Play services unavailable. Using LocationManager fallback.");
            sendStatus("Using device GPS fallback");
            forceLocationManagerFallback = true;
            fetchLocationManagerFallback();
            return;
        }

        CancellationTokenSource tokenSource = new CancellationTokenSource();
        handler.postDelayed(tokenSource::cancel, 3000L);

        try {
            @SuppressLint("MissingPermission")
            com.google.android.gms.tasks.Task<android.location.Location> task =
                    locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.getToken());
            task.addOnSuccessListener(location -> {
                if (location != null) {
                    Log.i(TAG, "FLP current location success lat=" + location.getLatitude() + " lon=" + location.getLongitude());
                    queueOriginSos(location.getLatitude(), location.getLongitude());
                } else {
                    Log.w(TAG, "FLP current location returned null. Trying fallback chain.");
                    fetchLastLocationFallback();
                }
            }).addOnFailureListener(e -> {
                Log.w(TAG, "getCurrentLocation failed", e);
                if (isBrokerPackageFailure(e)) {
                    forceLocationManagerFallback = true;
                    sendStatus("Google location service unstable, using device GPS fallback");
                    Log.w(TAG, "Detected broker package failure. Forcing LocationManager fallback for this session.");
                }
                fetchLastLocationFallback();
            });
        } catch (SecurityException sec) {
            if (isBrokerPackageFailure(sec)) {
                forceLocationManagerFallback = true;
                sendStatus("Google location service unstable, using device GPS fallback");
                Log.w(TAG, "SecurityException from FLP broker. Switching to LocationManager fallback.", sec);
                fetchLocationManagerFallback();
                return;
            }
            Log.w(TAG, "SecurityException while requesting FLP location.", sec);
            sendStatus("Location permission missing");
        } catch (RuntimeException runtimeException) {
            Log.w(TAG, "FLP runtime failure. Falling back to LocationManager.", runtimeException);
            forceLocationManagerFallback = true;
            fetchLocationManagerFallback();
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchLastLocationFallback() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "No location permission during fallback. Sending SOS with 0,0.");
            queueOriginSos(0d, 0d);
            return;
        }
        if (forceLocationManagerFallback) {
            Log.i(TAG, "Skipping FLP lastLocation due to forced fallback flag.");
            fetchLocationManagerFallback();
            return;
        }
        locationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        Log.i(TAG, "FLP lastLocation success lat=" + location.getLatitude() + " lon=" + location.getLongitude());
                        queueOriginSos(location.getLatitude(), location.getLongitude());
                    } else {
                        Log.w(TAG, "FLP lastLocation returned null. Using LocationManager fallback.");
                        fetchLocationManagerFallback();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Fused lastLocation failed. Using LocationManager fallback.", e);
                    if (isBrokerPackageFailure(e)) {
                        forceLocationManagerFallback = true;
                        Log.w(TAG, "Broker failure confirmed during lastLocation. Keeping forced fallback enabled.");
                    }
                    fetchLocationManagerFallback();
                });
    }

    private boolean isBrokerPackageFailure(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        String message = throwable.getMessage();
        return message != null && message.contains("Unknown calling package name 'com.google.android.gms'");
    }

    private void fetchLocationManagerFallback() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "LocationManager fallback blocked: no location permission. Sending 0,0.");
            queueOriginSos(0d, 0d);
            return;
        }

        try {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) {
                Log.w(TAG, "LocationManager unavailable. Sending SOS with 0,0.");
                queueOriginSos(0d, 0d);
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
            if (best != null) {
                Log.i(TAG, "LocationManager fallback selected lat=" + best.getLatitude() + " lon=" + best.getLongitude());
                queueOriginSos(best.getLatitude(), best.getLongitude());
            } else {
                Log.w(TAG, "No last known location from providers. Sending SOS with 0,0.");
                queueOriginSos(0d, 0d);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "LocationManager fallback permission error", e);
            queueOriginSos(0d, 0d);
        }
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

    private void queueOriginSos(double lat, double lon) {
        int nowEpoch = (int) Instant.now().getEpochSecond();
        String senderName = resolveSenderName();
        SosPacket packet = SosPacket.createSos(lat, lon, nowEpoch, senderName);
        synchronized (lock) {
            seenSosAt.put(packet.messageId, SystemClock.elapsedRealtime());
            originStates.put(packet.messageId, new OriginState(SystemClock.elapsedRealtime()));
            outbound.addFirst(new QueuedPacket(packet, SystemClock.elapsedRealtime() + 10_000L, 12, true));
        }
        Log.i(TAG, "Queued origin SOS: " + packetSummary(packet) + " lat=" + lat + " lon=" + lon + " sender=" + senderName);
        updateNotification("Broadcasting SOS " + packet.shortId());
        sendStatus("SOS broadcast started");
    }

    private void handleOfflineChatSend(Intent intent) {
        if (intent == null) {
            return;
        }

        String sourceUid = resolveCurrentUid();
        String targetUid = intent.getStringExtra(EXTRA_CHAT_TARGET_UID);
        String quickType = safe(intent.getStringExtra(EXTRA_CHAT_QUICK_TYPE));
        String text = safe(intent.getStringExtra(EXTRA_CHAT_TEXT));

        if (sourceUid.isEmpty() || targetUid == null || targetUid.isEmpty()) {
            return;
        }

        if (text.isEmpty() && quickType.isEmpty()) {
            return;
        }

        long requestedMessageId = intent.getLongExtra(EXTRA_CHAT_MESSAGE_ID, 0L);
        int nowEpoch = (int) Instant.now().getEpochSecond();
        String senderName = resolveSenderName();
        SosPacket packet = SosPacket.createChat(
                requestedMessageId,
                sourceUid,
                targetUid,
                text,
                quickType,
                nowEpoch,
                senderName
        );

        String chatId = ChatIdUtil.buildChatId(sourceUid, targetUid);
        long now = SystemClock.elapsedRealtime();

        synchronized (lock) {
            pendingChats.put(packet.messageId, new PendingChatState(packet, chatId, targetUid, now));
        }

        persistOutgoingOfflineChat(packet, chatId, "pending");
        broadcastOfflineChatStatus(packet.messageId, chatId, targetUid, "pending");

        if (wifiDirectTransport != null) {
            wifiDirectTransport.sendFrame(SosCodec.encodeFullFrame(packet));
        }
    }

    private void tickPendingChats() {
        if (pendingChats.isEmpty()) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        Map<Long, PendingChatState> snapshot;

        synchronized (lock) {
            Iterator<Map.Entry<Long, PendingChatState>> iterator = pendingChats.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, PendingChatState> entry = iterator.next();
                PendingChatState state = entry.getValue();
                if (now - state.createdAtMs > CHAT_MAX_LIFETIME_MS) {
                    iterator.remove();
                }
            }
            snapshot = new HashMap<>(pendingChats);
        }

        for (PendingChatState state : snapshot.values()) {
            if (!state.failedBroadcasted && now - state.createdAtMs >= CHAT_FAIL_TIMEOUT_MS) {
                state.failedBroadcasted = true;
                persistOutgoingOfflineChat(state.packet, state.chatId, "failed");
                broadcastOfflineChatStatus(state.packet.messageId, state.chatId, state.targetUid, "failed");
            }

            if (now >= state.nextRetryAtMs) {
                state.nextRetryAtMs = now + CHAT_RETRY_INTERVAL_MS;
                if (wifiDirectTransport != null) {
                    wifiDirectTransport.sendFrame(SosCodec.encodeFullFrame(state.packet));
                }
            }
        }
    }

    private void markPendingChatDelivered(long messageId) {
        PendingChatState state;
        synchronized (lock) {
            state = pendingChats.remove(messageId);
        }
        if (state == null) {
            return;
        }

        persistOutgoingOfflineChat(state.packet, state.chatId, "sent");
        broadcastOfflineChatStatus(state.packet.messageId, state.chatId, state.targetUid, "sent");
    }

    private void persistOutgoingOfflineChat(SosPacket packet, String chatId, String deliveryStatus) {
        ChatMessage msg = new ChatMessage();
        msg.setMessageId(toMessageId(packet.messageId));
        msg.setSenderId(packet.getSourceUid());
        msg.setSenderName(packet.getSenderName());
        msg.setText(resolveChatBody(packet));
        msg.setMessageType(packet.getQuickType().isEmpty() ? "normal" : "quick");
        msg.setQuickType(packet.getQuickType());
        msg.setTimestamp(new Timestamp(new Date(packet.getEpochSeconds() * 1000L)));
        msg.setRead(false);
        msg.setTransportType("offline");
        msg.setDeliveryStatus(deliveryStatus);
        OfflineChatStore.upsertMessage(this, chatId, msg);
    }

    private void persistIncomingOfflineChat(SosPacket packet) {
        String chatId = ChatIdUtil.buildChatId(packet.getSourceUid(), packet.getTargetUid());

        ChatMessage msg = new ChatMessage();
        msg.setMessageId(toMessageId(packet.messageId));
        msg.setSenderId(packet.getSourceUid());
        msg.setSenderName(packet.getSenderName());
        msg.setText(resolveChatBody(packet));
        msg.setMessageType(packet.getQuickType().isEmpty() ? "normal" : "quick");
        msg.setQuickType(packet.getQuickType());
        long timestampMs = packet.getEpochSeconds() > 0
                ? packet.getEpochSeconds() * 1000L
                : System.currentTimeMillis();
        msg.setTimestamp(new Timestamp(new Date(timestampMs)));
        msg.setRead(false);
        msg.setTransportType("offline");
        msg.setDeliveryStatus("sent");
        OfflineChatStore.upsertMessage(this, chatId, msg);
    }

    private void sendOfflineChatReceivedBroadcast(SosPacket packet) {
        Intent intent = new Intent(ACTION_OFFLINE_CHAT_RECEIVED);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_CHAT_MESSAGE_ID, packet.messageId);
        intent.putExtra(EXTRA_CHAT_SOURCE_UID, packet.getSourceUid());
        intent.putExtra(EXTRA_CHAT_TARGET_UID, packet.getTargetUid());
        intent.putExtra(EXTRA_CHAT_SENDER_NAME, packet.getSenderName());
        intent.putExtra(EXTRA_CHAT_TEXT, packet.getChatText());
        intent.putExtra(EXTRA_CHAT_QUICK_TYPE, packet.getQuickType());
        intent.putExtra(EXTRA_CHAT_TIMESTAMP_MS, packet.getEpochSeconds() * 1000L);
        sendBroadcast(intent);
    }

    private void broadcastOfflineChatStatus(long messageId, String chatId, String targetUid, String status) {
        Intent intent = new Intent(ACTION_OFFLINE_CHAT_STATUS);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_CHAT_MESSAGE_ID, messageId);
        intent.putExtra(EXTRA_CHAT_ID, chatId);
        intent.putExtra(EXTRA_CHAT_TARGET_UID, targetUid);
        intent.putExtra(EXTRA_CHAT_STATUS, status);
        sendBroadcast(intent);
    }

    private void showReceivedOfflineChatNotification(SosPacket packet) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String sender = packet.getSenderName().isEmpty() ? "HLDSN User" : packet.getSenderName();
        String text = resolveChatBody(packet);

        Intent openIntent = new Intent(this, com.example.hldsn.incident_report_module.ConversationActivity.class);
        openIntent.putExtra(com.example.hldsn.incident_report_module.ConversationActivity.EXTRA_USER_ID, packet.getSourceUid());
        openIntent.putExtra(com.example.hldsn.incident_report_module.ConversationActivity.EXTRA_USER_NAME, sender);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) (packet.messageId & 0x7FFFFFFF),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHAT_ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Offline message from " + sender)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(7000 + Math.abs((int) (packet.messageId % 1000)), notification);
        }
    }

    private String resolveChatBody(SosPacket packet) {
        String body = safe(packet.getChatText());
        if (!body.isEmpty()) {
            return body;
        }
        String quickType = packet.getQuickType();
        if (quickType == null || quickType.isEmpty()) {
            return "(empty message)";
        }
        switch (quickType) {
            case "need_rescue":
                return "Need rescue";
            case "need_medical":
                return "Need medical";
            case "fire_seen":
                return "Fire seen";
            case "road_blocked":
                return "Road blocked";
            case "i_am_safe":
                return "I am safe";
            case "found_shelter":
                return "Found shelter";
            default:
                return "Quick emergency message";
        }
    }

    private String resolveCurrentUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null && user.getUid() != null ? user.getUid() : "";
    }

    private String toMessageId(long messageId) {
        return String.format(Locale.US, "%012X", messageId & 0x0000FFFFFFFFFFFFL);
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private void onPacketReceived(SosPacket packet, boolean fullFrameReceived) {
        Log.i(TAG, "Packet received transport=" + (fullFrameReceived ? "WIFI_DIRECT" : "BLE_BEACON")
                + " data=" + (fullFrameReceived ? packetSummary(packet) : beaconSummary(packet)));

        if (packet.type == SosPacket.TYPE_CHAT_ACK) {
            handleChatAckPacket(packet, fullFrameReceived);
            return;
        }

        if (packet.type == SosPacket.TYPE_CHAT) {
            handleChatPacket(packet, fullFrameReceived);
            return;
        }

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

    private void handleChatPacket(SosPacket packet, boolean fullFrameReceived) {
        if (!fullFrameReceived) {
            // Chat payload is only available in full Wi-Fi Direct frames.
            return;
        }

        String currentUid = resolveCurrentUid();
        if (currentUid == null || currentUid.isEmpty()) {
            return;
        }

        boolean isNew;
        synchronized (lock) {
            Long seenAt = seenSosAt.get(packet.messageId);
            isNew = seenAt == null;
            seenSosAt.put(packet.messageId, SystemClock.elapsedRealtime());
        }

        if (!isNew) {
            return;
        }

        if (packet.isTargetFor(currentUid)) {
            persistIncomingOfflineChat(packet);
            sendOfflineChatReceivedBroadcast(packet);
            showReceivedOfflineChatNotification(packet);
            queueChatAck(packet.messageId, currentUid, packet.getSourceUid());
            sendStatus("Offline message received");
            return;
        }

        if (!packet.canRelay()) {
            return;
        }

        if (wifiDirectTransport != null) {
            wifiDirectTransport.sendFrame(SosCodec.encodeFullFrame(packet.asRelay()));
        }
    }

    private void handleChatAckPacket(SosPacket packet, boolean fullFrameReceived) {
        if (!fullFrameReceived) {
            return;
        }

        String dedupeKey = packet.type + ":" + packet.messageId + ":"
                + packet.getSourceUid() + ":" + packet.getTargetUid();
        synchronized (lock) {
            if (seenChatAcksAt.containsKey(dedupeKey)) {
                return;
            }
            seenChatAcksAt.put(dedupeKey, SystemClock.elapsedRealtime());
        }

        String currentUid = resolveCurrentUid();
        if (currentUid == null || currentUid.isEmpty()) {
            return;
        }

        if (packet.isTargetFor(currentUid)) {
            markPendingChatDelivered(packet.messageId);
            return;
        }

        if (!packet.canRelay()) {
            return;
        }

        if (wifiDirectTransport != null) {
            wifiDirectTransport.sendFrame(SosCodec.encodeFullFrame(packet.asRelay()));
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

    private void queueChatAck(long messageId, String sourceUid, String targetUid) {
        if (targetUid == null || targetUid.isEmpty()) {
            return;
        }
        if (wifiDirectTransport == null) {
            return;
        }
        SosPacket ack = SosPacket.createChatAck(messageId, sourceUid, targetUid);
        wifiDirectTransport.sendFrame(SosCodec.encodeFullFrame(ack));
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
        }

        if (chosen == TransportSelector.Transport.BLE && bleReady) {
            advertiseFrame(queued);
            return;
        }

        if (!wifiReady && bleReady) {
            Log.d(TAG, "SOS_FALLBACK using BLE because Wi-Fi Direct not ready. packet=" + packetSummary(queued.packet));
            advertiseFrame(queued);
            return;
        }

        if (queued.packet.type == SosPacket.TYPE_SOS) {
            if (!sentOnWifi && wifiDirectTransport != null && queued.hasFullPayload) {
                Log.d(TAG, "SOS_WIFI_ATTEMPT packet=" + packetSummary(queued.packet));
                wifiDirectTransport.sendFrame(fullFrame);
            }
            if (bleReady) {
                Log.d(TAG, "SOS_BLE_ATTEMPT packet=" + packetSummary(queued.packet));
                advertiseFrame(queued);
            } else if (!wifiReady) {
                Log.w(TAG, "SOS_NOT_SENT packet=" + packetSummary(queued.packet)
                        + " reason=no_transport_ready");
            }
        }
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

            Iterator<Map.Entry<String, Long>> chatAckIt = seenChatAcksAt.entrySet().iterator();
            while (chatAckIt.hasNext()) {
                Map.Entry<String, Long> entry = chatAckIt.next();
                if (now - entry.getValue() > MAX_SEEN_AGE_MS) {
                    chatAckIt.remove();
                }
            }
        }
    }

    private String resolveSenderName() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                return "HLDSN User";
            }
            if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
                return user.getDisplayName().trim();
            }
            if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
                return user.getEmail().trim();
            }
        } catch (Exception ignored) {
            // Keep SOS send path resilient even if auth lookup fails.
        }
        return "HLDSN User";
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

        NotificationChannel chatAlertChannel = new NotificationChannel(
            CHAT_ALERT_CHANNEL_ID,
            "Offline Chat Alerts",
            NotificationManager.IMPORTANCE_HIGH
        );
        chatAlertChannel.setDescription("Delivered offline mesh chat messages");
        manager.createNotificationChannel(chatAlertChannel);
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
        String type;
        switch (packet.type) {
            case SosPacket.TYPE_ACK:
                type = "ACK";
                break;
            case SosPacket.TYPE_CHAT:
                type = "CHAT";
                break;
            case SosPacket.TYPE_CHAT_ACK:
                type = "CHAT_ACK";
                break;
            default:
                type = "SOS";
                break;
        }
        String sender = packet.getSenderName().isEmpty() ? "unknown" : packet.getSenderName();
        String coords = (packet.getLatMilli() == 0 && packet.getLonMilli() == 0)
                ? ""
                : " lat=" + (packet.getLatMilli() / 1000.0d) + " lon=" + (packet.getLonMilli() / 1000.0d);
        String route = " src=" + packet.getSourceUid() + " dst=" + packet.getTargetUid();
        return type + "#" + packet.shortId() + " hop=" + packet.hop + " ttl=" + packet.ttl
                + " sender=" + sender + coords + route;
    }

    private String beaconSummary(SosPacket packet) {
        if (packet == null) {
            return "null";
        }
        String type;
        switch (packet.type) {
            case SosPacket.TYPE_ACK:
                type = "ACK_BEACON";
                break;
            case SosPacket.TYPE_CHAT:
                type = "CHAT_BEACON";
                break;
            case SosPacket.TYPE_CHAT_ACK:
                type = "CHAT_ACK_BEACON";
                break;
            default:
                type = "SOS_BEACON";
                break;
        }
        return type + "#" + packet.shortId() + " hop=" + packet.hop + " ttl=" + packet.ttl;
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
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

    private static final class PendingChatState {
        final SosPacket packet;
        final String chatId;
        final String targetUid;
        final long createdAtMs;
        long nextRetryAtMs;
        boolean failedBroadcasted;

        PendingChatState(SosPacket packet, String chatId, String targetUid, long createdAtMs) {
            this.packet = packet;
            this.chatId = chatId;
            this.targetUid = targetUid;
            this.createdAtMs = createdAtMs;
            this.nextRetryAtMs = createdAtMs;
            this.failedBroadcasted = false;
        }
    }
}

