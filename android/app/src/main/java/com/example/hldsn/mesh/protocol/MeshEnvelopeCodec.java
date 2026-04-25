package com.example.hldsn.mesh.protocol;

import com.example.hldsn.mesh.model.MessageStatus;
import com.example.hldsn.mesh.model.MeshIdentity;
import com.example.hldsn.mesh.model.MeshMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public final class MeshEnvelopeCodec {

    private static final String TYPE_IDENTITY = "identity";
    private static final String TYPE_MESSAGE = "message";

    private MeshEnvelopeCodec() {
    }

    public static byte[] encodeIdentityFrame(MeshIdentity identity) {
        try {
            JSONObject object = new JSONObject();
            object.put("frame_type", TYPE_IDENTITY);
            object.put("user_id", identity.getUserId());
            object.put("display_name", identity.getDisplayName());
            object.put("device_name", identity.getDeviceName());
            object.put("public_key", identity.getPublicKey());
            return object.toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to encode identity frame", e);
        }
    }

    public static MeshIdentity decodeIdentityFrame(byte[] frame) {
        if (frame == null || frame.length == 0) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(new String(frame, StandardCharsets.UTF_8));
            if (!TYPE_IDENTITY.equals(object.optString("frame_type"))) {
                return null;
            }
            String userId = object.optString("user_id", "").trim();
            String displayName = object.optString("display_name", "").trim();
            String deviceName = object.optString("device_name", "").trim();
            String publicKey = object.optString("public_key", "").trim();
            if (userId.isEmpty() || displayName.isEmpty() || deviceName.isEmpty() || publicKey.isEmpty()) {
                return null;
            }
            return new MeshIdentity(userId, displayName, deviceName, publicKey);
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] encodeMessageFrame(MeshMessage message) {
        try {
            JSONObject object = new JSONObject();
            object.put("frame_type", TYPE_MESSAGE);
            object.put("message_id", message.getMessageId());
            object.put("source_id", message.getSourceId());
            object.put("destination_id", message.getDestinationId());
            object.put("encrypted_payload", message.getEncryptedPayload());
            object.put("timestamp", message.getTimestamp());
            object.put("ttl", message.getTtl());
            object.put("hop_count", message.getHopCount());
            object.put("status", message.getStatus().name());
            return object.toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to encode message frame", e);
        }
    }

    public static MeshMessage decodeMessageFrame(byte[] frame) {
        if (frame == null || frame.length == 0) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(new String(frame, StandardCharsets.UTF_8));
            if (!TYPE_MESSAGE.equals(object.optString("frame_type"))) {
                return null;
            }
            String id = object.optString("message_id", "").trim();
            String source = object.optString("source_id", "").trim();
            String destination = object.optString("destination_id", "").trim();
            String encryptedPayload = object.optString("encrypted_payload", "").trim();
            long timestamp = object.optLong("timestamp", System.currentTimeMillis());
            int ttl = object.optInt("ttl", 0);
            int hopCount = object.optInt("hop_count", 0);
            MessageStatus status = parseStatus(object.optString("status", MessageStatus.SENT.name()));

            if (id.isEmpty() || source.isEmpty() || destination.isEmpty() || encryptedPayload.isEmpty()) {
                return null;
            }

            return new MeshMessage(id, source, destination, encryptedPayload, timestamp, ttl, hopCount, status);
        } catch (Exception e) {
            return null;
        }
    }

    private static MessageStatus parseStatus(String raw) {
        try {
            return MessageStatus.valueOf(raw);
        } catch (Exception ignored) {
            return MessageStatus.SENT;
        }
    }
}

