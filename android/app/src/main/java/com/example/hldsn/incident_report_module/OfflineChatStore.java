package com.example.hldsn.incident_report_module;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.Timestamp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class OfflineChatStore {

    private static final String PREFS_NAME = "offline_chat_store_v1";
    private static final String KEY_PREFIX = "conversation_";
    private static final int MAX_MESSAGES_PER_CHAT = 300;

    private OfflineChatStore() {
    }

    public static synchronized List<ChatMessage> getMessages(Context context, String chatId) {
        SharedPreferences prefs = getPrefs(context);
        JSONArray array = parseArray(prefs.getString(buildKey(chatId), "[]"));

        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            ChatMessage msg = fromJson(item);
            if (msg != null) {
                messages.add(msg);
            }
        }
        return messages;
    }

    public static synchronized void upsertMessage(Context context, String chatId, ChatMessage message) {
        if (message == null) {
            return;
        }

        SharedPreferences prefs = getPrefs(context);
        String key = buildKey(chatId);
        JSONArray array = parseArray(prefs.getString(key, "[]"));

        boolean updated = false;
        String incomingId = safe(message.getMessageId());

        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (incomingId.equals(item.optString("messageId"))) {
                try {
                    array.put(i, toJson(message));
                } catch (Exception ignored) {
                    // Ignore malformed array entries and continue.
                }
                updated = true;
                break;
            }
        }

        if (!updated) {
            array.put(toJson(message));
        }

        trimToMaxSize(array);
        prefs.edit().putString(key, array.toString()).apply();
    }

    public static synchronized void updateDeliveryStatus(
            Context context,
            String chatId,
            String messageId,
            String status
    ) {
        if (messageId == null || messageId.isEmpty()) {
            return;
        }

        SharedPreferences prefs = getPrefs(context);
        String key = buildKey(chatId);
        JSONArray array = parseArray(prefs.getString(key, "[]"));
        boolean changed = false;

        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (messageId.equals(item.optString("messageId"))) {
                item.remove("deliveryStatus");
                safePut(item, "deliveryStatus", status);
                safePut(item, "transportType", "offline");
                changed = true;
                break;
            }
        }

        if (changed) {
            prefs.edit().putString(key, array.toString()).apply();
        }
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String buildKey(String chatId) {
        return KEY_PREFIX + safe(chatId);
    }

    private static JSONArray parseArray(String raw) {
        try {
            return new JSONArray(raw != null ? raw : "[]");
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static JSONObject toJson(ChatMessage msg) {
        JSONObject obj = new JSONObject();
        safePut(obj, "messageId", msg.getMessageId());
        safePut(obj, "senderId", msg.getSenderId());
        safePut(obj, "senderName", msg.getSenderName());
        safePut(obj, "text", msg.getText());
        safePut(obj, "messageType", msg.getMessageType());
        safePut(obj, "quickType", msg.getQuickType());
        safePut(obj, "deliveryStatus", msg.getDeliveryStatus());
        safePut(obj, "transportType", msg.getTransportType());
        safePut(obj, "timestampMs", getTimestampMs(msg));
        safePut(obj, "read", msg.isRead());
        return obj;
    }

    private static ChatMessage fromJson(JSONObject obj) {
        try {
            ChatMessage msg = new ChatMessage();
            msg.setMessageId(obj.optString("messageId", ""));
            msg.setSenderId(obj.optString("senderId", ""));
            msg.setSenderName(obj.optString("senderName", ""));
            msg.setText(obj.optString("text", ""));
            msg.setMessageType(obj.optString("messageType", "normal"));
            msg.setQuickType(obj.optString("quickType", ""));
            msg.setDeliveryStatus(obj.optString("deliveryStatus", ""));
            msg.setTransportType(obj.optString("transportType", ""));
            msg.setRead(obj.optBoolean("read", false));

            long timestampMs = obj.optLong("timestampMs", System.currentTimeMillis());
            msg.setTimestamp(new Timestamp(new Date(timestampMs)));
            return msg;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void trimToMaxSize(JSONArray array) {
        if (array.length() <= MAX_MESSAGES_PER_CHAT) {
            return;
        }

        int start = Math.max(0, array.length() - MAX_MESSAGES_PER_CHAT);
        JSONArray trimmed = new JSONArray();
        for (int i = start; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                trimmed.put(item);
            }
        }

        while (array.length() > 0) {
            array.remove(array.length() - 1);
        }

        for (int i = 0; i < trimmed.length(); i++) {
            array.put(trimmed.opt(i));
        }
    }

    private static long getTimestampMs(ChatMessage msg) {
        if (msg.getTimestamp() == null) {
            return System.currentTimeMillis();
        }
        return msg.getTimestamp().toDate().getTime();
    }

    private static void safePut(JSONObject obj, String key, Object value) {
        try {
            obj.put(key, value != null ? value : JSONObject.NULL);
        } catch (Exception ignored) {
            // no-op
        }
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
