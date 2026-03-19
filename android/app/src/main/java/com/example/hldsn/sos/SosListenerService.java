package com.example.hldsn.sos;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.hldsn.R;
import com.example.hldsn.home.HomePageActivity;

/**
 * Long-running foreground service that continuously monitors for SOS alerts
 * from nearby devices via:
 *  - BLE passive scanning  (low battery, ~30 m range per hop)
 *  - Wi-Fi Direct DNS-SD discovery (~150 m range)
 *
 * When an SOS is detected it:
 *  1. Shows a high-priority heads-up notification with the sender's name & map link.
 *  2. Silently re-broadcasts the SOS via BLE for 60 seconds (RELAY) so devices
 *     further away also get alerted — extending the mesh range automatically.
 *
 * Started by HomePageActivity; declared in AndroidManifest with
 * foregroundServiceType="connectedDevice|location".
 */
public class SosListenerService extends Service {

    private static final String TAG = "SosListenerService";

    // Notification channel IDs
    public static final String CHANNEL_FOREGROUND = "sos_foreground_channel";
    public static final String CHANNEL_ALERT      = "sos_alert_channel";

    // Notification IDs
    private static final int NOTIF_ID_FG    = 1001;
    private static final int NOTIF_ID_ALERT = 1002;

    // How long (ms) this device re-broadcasts a received SOS before stopping
    private static final long RELAY_DURATION_MS = 60_000;

    private BleScanner            bleScanner;
    private BleAdvertiser         relayAdvertiser;
    private WifiDirectSosManager  wifiDirectManager;

    private final Handler handler = new Handler(Looper.getMainLooper());

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();

        bleScanner       = new BleScanner();
        relayAdvertiser  = new BleAdvertiser();
        wifiDirectManager = new WifiDirectSosManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Must call startForeground within 5 s of startForegroundService()
        startForeground(NOTIF_ID_FG, buildForegroundNotification());

        startBleScanning();
        startWifiDirectDiscovery();

        return START_STICKY;   // restart if killed by OS
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        bleScanner.stopScanning();
        relayAdvertiser.stopAdvertising();
        wifiDirectManager.stop();
        Log.d(TAG, "SosListenerService destroyed");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── BLE scanning ──────────────────────────────────────────────────────────

    private void startBleScanning() {
        bleScanner.setListener(packet -> {
            Log.d(TAG, "SOS via BLE: " + packet);
            showSosNotification(packet);
            relayViaBle(packet);   // silent automatic relay
        });
        bleScanner.startScanning(this);
    }

    /**
     * Re-advertise the received SOS payload for {@value #RELAY_DURATION_MS} ms.
     * This is the "mesh hop" — devices that can't reach the original sender
     * will now receive the SOS from THIS device's advertisement.
     */
    private void relayViaBle(SosPacket packet) {
        if (relayAdvertiser.isAdvertising()) return;   // already relaying
        relayAdvertiser.startAdvertising(this, packet);
        handler.postDelayed(() -> {
            relayAdvertiser.stopAdvertising();
            Log.d(TAG, "BLE relay stopped after " + RELAY_DURATION_MS + " ms");
        }, RELAY_DURATION_MS);
    }

    // ── Wi-Fi Direct discovery ────────────────────────────────────────────────

    private void startWifiDirectDiscovery() {
        wifiDirectManager.startAsReceiver(packet -> {
            Log.d(TAG, "SOS via WiFi Direct: " + packet);
            showSosNotification(packet);
        });
    }

    // ── Notification helpers ──────────────────────────────────────────────────

    private void createNotificationChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        // Persistent low-priority foreground channel
        NotificationChannel fg = new NotificationChannel(
                CHANNEL_FOREGROUND,
                "SOS Listener",
                NotificationManager.IMPORTANCE_LOW);
        fg.setDescription("HLDSN is monitoring for nearby SOS alerts");
        nm.createNotificationChannel(fg);

        // High-priority alert channel for received SOS
        NotificationChannel alert = new NotificationChannel(
                CHANNEL_ALERT,
                "SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH);
        alert.setDescription("Emergency SOS alerts from nearby HLDSN users");
        alert.enableVibration(true);
        alert.setVibrationPattern(new long[]{0, 400, 200, 400, 200, 400});
        alert.enableLights(true);
        alert.setLightColor(Color.RED);
        nm.createNotificationChannel(alert);
    }

    private Notification buildForegroundNotification() {
        Intent intent = new Intent(this, HomePageActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_FOREGROUND)
                .setContentTitle("HLDSN Active")
                .setContentText("Monitoring for nearby SOS alerts")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pi)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void showSosNotification(SosPacket packet) {
        String mapUrl  = packet.getMapsUrl();
        String bigText = "⚠️ " + packet.senderName + " needs immediate help!\n📍 " + mapUrl;

        // Make the map URL tappable
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mapUrl));
        PendingIntent mapPi = PendingIntent.getActivity(
                this, packet.senderName.hashCode(), mapIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Use default alarm tone for maximum urgency
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmSound == null) {
            alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ALERT)
                        .setContentTitle("🆘 SOS from " + packet.senderName)
                        .setContentText("Tap to open map location")
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText))
                        .setSmallIcon(R.drawable.ic_notification)
                        .setColor(Color.RED)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setAutoCancel(true)
                        .setVibrate(new long[]{0, 400, 200, 400, 200, 400})
                        .setSound(alarmSound)
                        .setContentIntent(mapPi)
                        .addAction(android.R.drawable.ic_dialog_map,
                                "Open Map", mapPi);

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            // Use unique ID per sender so multiple SOS alerts stack
            nm.notify(NOTIF_ID_ALERT + packet.senderName.hashCode(), builder.build());
        }
    }
}
