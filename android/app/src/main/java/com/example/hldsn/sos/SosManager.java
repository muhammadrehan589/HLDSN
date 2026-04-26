package com.example.hldsn.sos;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main SOS orchestrator.
 *
 * ── Call flow ──
 * triggerSos()
 *   └─► triggerAlarmFeedback()    (vibrate + alarm sound — immediate, always)
 *   └─► LocationHelper.getLocation()
 *         ├─► [internet] broadcastOnline()
 *         │     └─► Write SOS message to every Firestore chat the user has
 *         └─► [no internet] broadcastOffline()
 *               ├─► BleAdvertiser.startAdvertising()   (BLE mesh)
 *               └─► WifiDirectSosManager.startAsSender()  (WiFi Direct)
 */
public class SosManager {

    private static final String TAG = "SosManager";

    // How long the offline broadcast runs (5 minutes) before auto-stopping
    private static final long OFFLINE_BROADCAST_TTL_MS = 5 * 60 * 1_000L;

    // ── Callback ──────────────────────────────────────────────────────────────

    public interface SosTriggerCallback {
        /** Called the moment the SOS process kicks off (before location/network). */
        void onStarted();
        /** Online path complete. contactsReached = number of chat threads updated. */
        void onOnlineBroadcastComplete(int contactsReached);
        /** Offline path started — BLE + WiFi Direct are now broadcasting. */
        void onOfflineStarted();
        /** An unrecoverable error occurred. */
        void onError(String reason);
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final Context               context;
    private final BleAdvertiser         bleAdvertiser;
    private final WifiDirectSosManager  wifiDirectManager;

    private boolean sosActive = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public SosManager(Context context) {
        this.context          = context.getApplicationContext();
        this.bleAdvertiser    = new BleAdvertiser();
        this.wifiDirectManager = new WifiDirectSosManager(this.context);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Trigger SOS alert. Safe to call from the UI thread.
     * Idempotent — if already active, subsequent calls are ignored.
     */
    public void triggerSos(SosTriggerCallback callback) {
        if (sosActive) {
            Log.w(TAG, "SOS already active, ignoring duplicate trigger");
            return;
        }
        sosActive = true;

        // Step 1: immediate sensory feedback
        triggerAlarmFeedback();
        if (callback != null) callback.onStarted();

        // Step 2: acquire location, then branch
        new LocationHelper(context).getLocation(context, new LocationHelper.LocationCallback2() {
            @Override
            public void onLocationReceived(double lat, double lng) {
                if (isInternetAvailable()) {
                    broadcastOnline(lat, lng, callback);
                } else {
                    broadcastOffline(lat, lng, callback);
                }
            }

            @Override
            public void onFailed(String reason) {
                Log.w(TAG, "Location unavailable: " + reason + " — broadcasting without coords");
                if (isInternetAvailable()) {
                    broadcastOnline(0, 0, callback);
                } else {
                    broadcastOffline(0, 0, callback);
                }
            }
        });
    }

    /** Manually stop any ongoing offline SOS broadcast. */
    public void stopSos() {
        bleAdvertiser.stopAdvertising();
        wifiDirectManager.stop();
        sosActive = false;
        Log.d(TAG, "SOS stopped");
    }

    public boolean isSosActive() { return sosActive; }

    // ── Path A — Online (Firebase) ────────────────────────────────────────────

    private void broadcastOnline(double lat, double lng, SosTriggerCallback callback) {
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) {
            if (callback != null) callback.onError("User not logged in");
            sosActive = false;
            return;
        }

        String currentUid  = me.getUid();
        String senderName  = resolveName(me);
        String mapsUrl     = (lat != 0 || lng != 0)
                ? "https://maps.google.com/?q=" + lat + "," + lng
                : "(location unavailable)";
        String sosText     = "🆘 SOS ALERT! " + senderName + " needs immediate help!\n"
                           + "📍 Location: " + mapsUrl;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Fetch every registered user and post the SOS to every chat thread
        db.collection("users").get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        if (callback != null) callback.onOnlineBroadcastComplete(0);
                        return;
                    }

                    AtomicInteger pending = new AtomicInteger(0);
                    AtomicInteger reached = new AtomicInteger(0);

                    for (var doc : snap.getDocuments()) {
                        String otherUid = doc.getId();
                        if (otherUid.equals(currentUid)) continue;

                        pending.incrementAndGet();

                        // Deterministic chatId (same algorithm as ConversationActivity)
                        String[] uids = {currentUid, otherUid};
                        Arrays.sort(uids);
                        String chatId = uids[0] + "_" + uids[1];

                        // Build SOS message map — messageType = "sos" triggers special UI
                        Map<String, Object> msg = new HashMap<>();
                        msg.put("messageId",   UUID.randomUUID().toString());
                        msg.put("senderId",    currentUid);
                        msg.put("senderName",  senderName);
                        msg.put("text",        sosText);
                        msg.put("timestamp",   Timestamp.now());
                        msg.put("read",        false);
                        msg.put("messageType", "sos");

                        DocumentReference chatRef = db.collection("chats").document(chatId);

                        chatRef.collection("messages").add(msg)
                                .addOnSuccessListener(ref -> {
                                    // Update chat-list metadata so the SOS shows as last message
                                    Map<String, Object> meta = new HashMap<>();
                                    meta.put("participants",    Arrays.asList(currentUid, otherUid));
                                    meta.put("lastMessage",     "🆘 SOS ALERT");
                                    meta.put("lastMessageTime", Timestamp.now());
                                    meta.put("unreadCounts." + otherUid, FieldValue.increment(1));
                                    chatRef.set(meta, SetOptions.merge());
                                    reached.incrementAndGet();
                                })
                                .addOnCompleteListener(task -> {
                                    if (pending.decrementAndGet() == 0 && callback != null) {
                                        callback.onOnlineBroadcastComplete(reached.get());
                                    }
                                });
                    }

                    // Edge case: all documents were the current user
                    if (pending.get() == 0 && callback != null) {
                        callback.onOnlineBroadcastComplete(0);
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError("Firestore error: " + e.getMessage());
                    sosActive = false;
                });
    }

    // ── Path B — Offline (BLE + WiFi Direct) ─────────────────────────────────

    private void broadcastOffline(double lat, double lng, SosTriggerCallback callback) {
        FirebaseUser me   = FirebaseAuth.getInstance().getCurrentUser();
        String senderName = (me != null) ? resolveName(me) : "Unknown";

        int nowEpoch = (int) (System.currentTimeMillis() / 1000L);
        SosPacket packet = SosPacket.createSos(lat, lng, nowEpoch, senderName);

        // Start BLE advertising
        bleAdvertiser.startAdvertising(context, packet);

        // Start WiFi Direct group + socket server
        wifiDirectManager.startAsSender(packet);

        // Auto-stop after TTL to preserve battery
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(this::stopSos, OFFLINE_BROADCAST_TTL_MS);

        Log.d(TAG, "Offline SOS broadcasting started — " + packet);
        if (callback != null) callback.onOfflineStarted();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Vibrate SOS pattern + play default alarm ringtone.
     * Provides immediate confirmation to the user that the SOS was triggered.
     */
    @SuppressWarnings("deprecation")
    private void triggerAlarmFeedback() {
        // Vibrate: . . .  — — —  . . .  (Morse SOS)
        long[] pattern = {0, 150, 100, 150, 100, 150,   // S: dot dot dot
                          200, 400, 100, 400, 100, 400,  // O: dash dash dash
                          200, 150, 100, 150, 100, 150}; // S: dot dot dot
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        }

        // Alarm sound
        try {
            android.net.Uri alarmUri = android.media.RingtoneManager.getDefaultUri(
                    android.media.RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = android.media.RingtoneManager.getDefaultUri(
                        android.media.RingtoneManager.TYPE_NOTIFICATION);
            }
            android.media.Ringtone ringtone =
                    android.media.RingtoneManager.getRingtone(context, alarmUri);
            if (ringtone != null) ringtone.play();
        } catch (Exception e) {
            Log.e(TAG, "Could not play alarm sound", e);
        }
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.Network net = cm.getActiveNetwork();
        if (net == null)    return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(net);
        return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private static String resolveName(FirebaseUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            return user.getDisplayName();
        }
        return user.getEmail() != null ? user.getEmail() : "Unknown";
    }
}
