package com.example.hldsn.notification_module;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.hldsn.sos.SosPacket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class SosAlertStore {

    private static final String TAG = "SosAlertStore";
    private static final String PREFS_NAME = "sos_alert_store";
    private static final String KEY_ALERTS = "alerts";
    private static final int MAX_ALERTS = 25;

    private SosAlertStore() {
    }

    public static synchronized SosAlertRecord saveOrUpdateAlert(Context context, SosPacket packet, boolean fullFrameReceived) {
        List<SosAlertRecord> alerts = getAlerts(context);
        String messageId = packet.shortId();
        long timestampMs = packet.getEpochSeconds() > 0 ? packet.getEpochSeconds() * 1000L : System.currentTimeMillis();
        String sender = packet.getSenderName();
        String title = buildTitle(fullFrameReceived, sender);
        String subtitle = buildSubtitle(packet, fullFrameReceived, timestampMs);

        SosAlertRecord updated = new SosAlertRecord(messageId, title, subtitle, timestampMs, false,
                packet.getLatMilli(), packet.getLonMilli());
        boolean replaced = false;
        for (int i = 0; i < alerts.size(); i++) {
            SosAlertRecord current = alerts.get(i);
            if (messageId.equals(current.getMessageId())) {
                boolean keepSeen = current.isSeen() && !fullFrameReceived;
                // Prefer non-zero coordinates: keep existing if new packet has no location
                int lat = packet.getLatMilli() != 0 ? packet.getLatMilli() : current.getLatMilli();
                int lon = packet.getLonMilli() != 0 ? packet.getLonMilli() : current.getLonMilli();
                updated = new SosAlertRecord(messageId, title, subtitle, timestampMs,
                        keepSeen ? current.isSeen() : false, lat, lon);
                alerts.set(i, updated);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            alerts.add(updated);
        }

        alerts.sort(Comparator.comparingLong(SosAlertRecord::getTimestampMs).reversed());
        if (alerts.size() > MAX_ALERTS) {
            alerts = new ArrayList<>(alerts.subList(0, MAX_ALERTS));
        }
        persist(context, alerts);
        Log.d(TAG, "Stored SOS alert " + messageId + " fullFrame=" + fullFrameReceived);
        return updated;
    }

    public static synchronized List<SosAlertRecord> getAlerts(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = preferences.getString(KEY_ALERTS, "[]");
        List<SosAlertRecord> alerts = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) {
                    alerts.add(SosAlertRecord.fromJson(object));
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "Failed to parse SOS alerts", e);
        }
        alerts.sort(Comparator.comparingLong(SosAlertRecord::getTimestampMs).reversed());
        return alerts;
    }

    public static synchronized int getUnseenCount(Context context) {
        int count = 0;
        for (SosAlertRecord alert : getAlerts(context)) {
            if (!alert.isSeen()) {
                count++;
            }
        }
        return count;
    }

    public static synchronized void markAllSeen(Context context) {
        List<SosAlertRecord> alerts = getAlerts(context);
        List<SosAlertRecord> updated = new ArrayList<>();
        for (SosAlertRecord alert : alerts) {
            updated.add(alert.withSeen(true));
        }
        persist(context, updated);
    }

    /**
     * Removes a specific SOS alert from the local store by its ID.
     */
    public static synchronized void removeAlertById(Context context, String messageId) {
        // 1. Get the current list of alerts
        List<SosAlertRecord> alerts = getAlerts(context);

        // 2. Remove the alert that matches the ID
        // Note: removeIf requires Java 8+ (which your project is using)
        alerts.removeIf(alert -> alert.getMessageId().equals(messageId));

        // 3. Save the filtered list back to SharedPreferences
        persist(context, alerts);
    }

    private static void persist(Context context, List<SosAlertRecord> alerts) {
        JSONArray array = new JSONArray();
        for (SosAlertRecord alert : alerts) {
            try {
                array.put(alert.toJson());
            } catch (JSONException e) {
                Log.w(TAG, "Failed to serialize SOS alert", e);
            }
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ALERTS, array.toString())
                .apply();
    }

    private static String buildSubtitle(SosPacket packet, boolean fullFrameReceived, long timestampMs) {
        String time = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(timestampMs));
        String senderPart = packet.getSenderName().isEmpty() ? "" : "From: " + packet.getSenderName() + " • ";
        if (fullFrameReceived && packet.getLatMilli() != 0 && packet.getLonMilli() != 0) {
            return senderPart + "Location: " + (packet.getLatMilli() / 1000.0d) + ", " + (packet.getLonMilli() / 1000.0d) + " • " + time;
        }
        return senderPart + (fullFrameReceived ? "Emergency details received" : "Emergency beacon received") + " • " + time;
    }

    private static String buildTitle(boolean fullFrameReceived, String senderName) {
        if (senderName == null || senderName.trim().isEmpty()) {
            return fullFrameReceived ? "Nearby SOS Alert" : "SOS Beacon Detected";
        }
        return fullFrameReceived ? "SOS from " + senderName : "SOS Beacon from " + senderName;
    }
}

