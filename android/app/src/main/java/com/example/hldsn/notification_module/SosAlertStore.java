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
    private static final String TRACE_TAG = "SOS_ALERT_TRACE";
    private static final String PREFS_NAME = "sos_alert_store";
    private static final String KEY_ALERTS = "alerts";
    private static final int MAX_ALERTS = 25;

    private SosAlertStore() {
    }

    public static synchronized SosAlertRecord saveOrUpdateAlert(Context context, SosPacket packet, boolean fullFrameReceived) {
        List<SosAlertRecord> alerts = getAlerts(context);
        String messageId = packet.shortId();
        long timestampMs = packet.getEpochSeconds() > 0 ? packet.getEpochSeconds() * 1000L : System.currentTimeMillis();
        String packetSender = packet.getSenderName();
        int mergedLat = packet.getLatMilli();
        int mergedLon = packet.getLonMilli();
        String mergedSender = packetSender == null ? "" : packetSender.trim();
        boolean seen = false;

        boolean replaced = false;
        for (int i = 0; i < alerts.size(); i++) {
            SosAlertRecord current = alerts.get(i);
            if (messageId.equals(current.getMessageId())) {
                Log.d(TRACE_TAG, "MERGE_BEFORE id=" + messageId
                        + " fullFrame=" + fullFrameReceived
                        + " packet_sender=" + mergedSender
                        + " packet_latMilli=" + mergedLat
                        + " packet_lonMilli=" + mergedLon
                        + " existing_sender=" + current.getSenderName()
                        + " existing_latMilli=" + current.getLatMilli()
                        + " existing_lonMilli=" + current.getLonMilli());
                seen = current.isSeen() && !fullFrameReceived;
                if (mergedLat == 0 && mergedLon == 0 && current.hasLocation()) {
                    mergedLat = current.getLatMilli();
                    mergedLon = current.getLonMilli();
                }
                if (mergedSender.isEmpty()) {
                    mergedSender = current.getSenderName();
                }
                replaced = true;
                break;
            }
        }

        String title = buildTitle(fullFrameReceived, mergedSender);
        String subtitle = buildSubtitle(mergedSender, mergedLat, mergedLon, fullFrameReceived, timestampMs);
        SosAlertRecord updated = new SosAlertRecord(messageId, title, subtitle, timestampMs, seen,
                mergedLat, mergedLon, mergedSender);

        if (replaced) {
            for (int i = 0; i < alerts.size(); i++) {
                if (messageId.equals(alerts.get(i).getMessageId())) {
                    alerts.set(i, updated);
                    break;
                }
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
        Log.i(TRACE_TAG, "MERGE_AFTER id=" + messageId
                + " fullFrame=" + fullFrameReceived
                + " stored_sender=" + updated.getSenderName()
                + " stored_latMilli=" + updated.getLatMilli()
                + " stored_lonMilli=" + updated.getLonMilli()
                + " hasLocation=" + updated.hasLocation());
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

    private static String buildSubtitle(String senderName, int latMilli, int lonMilli, boolean fullFrameReceived, long timestampMs) {
        String time = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(timestampMs));
        String senderPart = senderName == null || senderName.trim().isEmpty() ? "" : "From: " + senderName.trim() + " • ";
        if (latMilli != 0 || lonMilli != 0) {
            return senderPart + String.format(Locale.US, "Location: %.6f, %.6f • %s", latMilli / 1000.0d, lonMilli / 1000.0d, time);
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

