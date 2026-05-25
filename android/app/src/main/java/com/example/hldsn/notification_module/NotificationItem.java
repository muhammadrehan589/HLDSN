package com.example.hldsn.notification_module;

import com.example.hldsn.incident_report_module.IncidentModel;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import org.json.JSONException;
import org.json.JSONObject;

public class NotificationItem {

    public static final String TYPE_INCIDENT = "incident";
    public static final String TYPE_SOS = "sos";
    public static final String TYPE_USER_NOTIFICATION = "user_notification";
    public static final String TYPE_TASK_ASSIGNMENT = "task_assignment";

    private final String id;
    private final String type;
    private final String title;
    private final String subtitle;
    private final String detailLabel;
    private final long timestampMs;
    private final String payloadJson;
    /** Serialised {@link SosAlertRecord} JSON – only populated for SOS items. */
    private final String sosAlertJson;

    /** General-purpose constructor (no SOS payload). */
    public NotificationItem(String id, String type, String title, String subtitle, String detailLabel, long timestampMs) {
        this(id, type, title, subtitle, detailLabel, timestampMs, null, null);
    }

    public NotificationItem(String id, String type, String title, String subtitle, String detailLabel,
                            long timestampMs, String sosAlertJson) {
        this(id, type, title, subtitle, detailLabel, timestampMs, sosAlertJson, null);
    }

    public NotificationItem(String id, String type, String title, String subtitle, String detailLabel,
                            long timestampMs, String sosAlertJson, String payloadJson) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.subtitle = subtitle;
        this.detailLabel = detailLabel;
        this.timestampMs = timestampMs;
        this.sosAlertJson = sosAlertJson;
        this.payloadJson = payloadJson;
    }

    public static NotificationItem fromIncident(IncidentModel incident) {
        long timestamp = incident.getCreatedAt() != null ? incident.getCreatedAt().getTime() : 0L;
        String id = incident.getId() != null ? incident.getId() : String.valueOf(timestamp);
        String location = incident.getLocation() != null ? incident.getLocation() : "your area";
        return new NotificationItem(
                id,
                TYPE_INCIDENT,
                incident.getIncidentType(),
                "Near " + location,
                "View",
                timestamp
        );
    }

    public static NotificationItem fromSosAlert(SosAlertRecord alert) {
        String json = null;
        try {
            json = alert.toJson().toString();
        } catch (JSONException ignored) {
        }
        return new NotificationItem(
                alert.getMessageId(),
                TYPE_SOS,
                alert.getTitle(),
                alert.getSubtitle(),
                "View Location",
                alert.getTimestampMs(),
                json
        );
    }

    public static NotificationItem fromUserNotification(DocumentSnapshot documentSnapshot) {
        Timestamp createdAt = documentSnapshot.getTimestamp("createdAt");
        long timestamp = createdAt != null ? createdAt.toDate().getTime() : 0L;

        return new NotificationItem(
                documentSnapshot.getId(),
            firstNonBlank(documentSnapshot.getString("type"), TYPE_USER_NOTIFICATION),
                firstNonBlank(documentSnapshot.getString("title"), "Notification"),
                firstNonBlank(documentSnapshot.getString("subtitle"), ""),
                firstNonBlank(documentSnapshot.getString("detailLabel"), "View Details"),
            timestamp,
            null,
            toJson(documentSnapshot)
        );
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getDetailLabel() { return detailLabel; }
    public long getTimestampMs() { return timestampMs; }
    public String getPayloadJson() { return payloadJson; }
    public String getSosAlertJson() { return sosAlertJson; }
    public boolean isSosAlert() { return TYPE_SOS.equals(type); }
    public boolean isUserNotification() {
        return TYPE_USER_NOTIFICATION.equals(type)
                || TYPE_TASK_ASSIGNMENT.equals(type)
                || "volunteer_application".equals(type)
                || "volunteer_approved".equals(type)
                || "volunteer_rejected".equals(type)
                || "task_completed".equals(type);
    }
    public boolean isTaskAssignment() { return TYPE_TASK_ASSIGNMENT.equals(type); }

    public String getTaskId() {
        return getPayloadValue("taskId");
    }

    public String getNotificationRecipientUid() {
        return getPayloadValue("recipientUid");
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String getPayloadValue(String key) {
        if (payloadJson == null || payloadJson.trim().isEmpty()) {
            return "";
        }
        try {
            JSONObject object = new JSONObject(payloadJson);
            return firstNonBlank(object.optString(key, ""), "");
        } catch (JSONException ignored) {
            return "";
        }
    }

    private static String toJson(DocumentSnapshot snapshot) {
        try {
            return new JSONObject(snapshot.getData() == null ? new java.util.HashMap<>() : snapshot.getData()).toString();
        } catch (Exception ignored) {
            return null;
        }
    }
}
