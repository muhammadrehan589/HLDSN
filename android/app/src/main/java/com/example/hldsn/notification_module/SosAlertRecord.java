package com.example.hldsn.notification_module;

import org.json.JSONException;
import org.json.JSONObject;

public class SosAlertRecord {

    private static final String KEY_MESSAGE_ID = "messageId";
    private static final String KEY_TITLE = "title";
    private static final String KEY_SUBTITLE = "subtitle";
    private static final String KEY_TIMESTAMP = "timestampMs";
    private static final String KEY_SEEN = "seen";
    private static final String KEY_LAT_MILLI = "latMilli";
    private static final String KEY_LON_MILLI = "lonMilli";
    private static final String KEY_SENDER_NAME = "senderName";

    private final String messageId;
    private final String title;
    private final String subtitle;
    private final long timestampMs;
    private final boolean seen;
    private final int latMilli;
    private final int lonMilli;
    private final String senderName;

    /** Backward-compatible constructor (no location). */
    public SosAlertRecord(String messageId, String title, String subtitle, long timestampMs, boolean seen) {
        this(messageId, title, subtitle, timestampMs, seen, 0, 0, "");
    }

    public SosAlertRecord(String messageId, String title, String subtitle, long timestampMs, boolean seen,
                          int latMilli, int lonMilli) {
        this(messageId, title, subtitle, timestampMs, seen, latMilli, lonMilli, "");
    }

    public SosAlertRecord(String messageId, String title, String subtitle, long timestampMs, boolean seen,
                          int latMilli, int lonMilli, String senderName) {
        this.messageId = messageId;
        this.title = title;
        this.subtitle = subtitle;
        this.timestampMs = timestampMs;
        this.seen = seen;
        this.latMilli = latMilli;
        this.lonMilli = lonMilli;
        this.senderName = senderName == null ? "" : senderName.trim();
    }

    public String getMessageId() { return messageId; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public long getTimestampMs() { return timestampMs; }
    public boolean isSeen() { return seen; }
    public int getLatMilli() { return latMilli; }
    public int getLonMilli() { return lonMilli; }
    public String getSenderName() { return senderName; }
    public boolean hasLocation() { return latMilli != 0 || lonMilli != 0; }

    public SosAlertRecord withSeen(boolean seenValue) {
        return new SosAlertRecord(messageId, title, subtitle, timestampMs, seenValue, latMilli, lonMilli, senderName);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put(KEY_MESSAGE_ID, messageId);
        object.put(KEY_TITLE, title);
        object.put(KEY_SUBTITLE, subtitle);
        object.put(KEY_TIMESTAMP, timestampMs);
        object.put(KEY_SEEN, seen);
        object.put(KEY_LAT_MILLI, latMilli);
        object.put(KEY_LON_MILLI, lonMilli);
        object.put(KEY_SENDER_NAME, senderName);
        return object;
    }

    public static SosAlertRecord fromJson(JSONObject object) throws JSONException {
        return new SosAlertRecord(
                object.optString(KEY_MESSAGE_ID),
                object.optString(KEY_TITLE),
                object.optString(KEY_SUBTITLE),
                object.optLong(KEY_TIMESTAMP),
                object.optBoolean(KEY_SEEN, false),
                object.optInt(KEY_LAT_MILLI, 0),
                object.optInt(KEY_LON_MILLI, 0),
                object.optString(KEY_SENDER_NAME, "")
        );
    }
}
