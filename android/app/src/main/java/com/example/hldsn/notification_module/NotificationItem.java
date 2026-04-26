package com.example.hldsn.notification_module;

import com.example.hldsn.incident_report_module.IncidentModel;

import org.json.JSONException;

public class NotificationItem {

    public static final String TYPE_INCIDENT = "incident";
    public static final String TYPE_SOS = "sos";

    private final String id;
    private final String type;
    private final String title;
    private final String subtitle;
    private final String detailLabel;
    private final long timestampMs;
    /** Serialised {@link SosAlertRecord} JSON – only populated for SOS items. */
    private final String sosAlertJson;

    /** General-purpose constructor (no SOS payload). */
    public NotificationItem(String id, String type, String title, String subtitle, String detailLabel, long timestampMs) {
        this(id, type, title, subtitle, detailLabel, timestampMs, null);
    }

    public NotificationItem(String id, String type, String title, String subtitle, String detailLabel,
                            long timestampMs, String sosAlertJson) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.subtitle = subtitle;
        this.detailLabel = detailLabel;
        this.timestampMs = timestampMs;
        this.sosAlertJson = sosAlertJson;
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

    public String getId() { return id; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getDetailLabel() { return detailLabel; }
    public long getTimestampMs() { return timestampMs; }
    public String getSosAlertJson() { return sosAlertJson; }
    public boolean isSosAlert() { return TYPE_SOS.equals(type); }
}
