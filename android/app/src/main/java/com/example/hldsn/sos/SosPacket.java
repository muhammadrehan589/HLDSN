package com.example.hldsn.sos;

import java.security.SecureRandom;
import java.util.Locale;

public final class SosPacket {

    static final int TYPE_SOS = 1;
    static final int TYPE_ACK = 2;
    static final int TYPE_CHAT = 3;
    static final int TYPE_CHAT_ACK = 4;
    static final int DEFAULT_TTL = 5;
    static final int CHAT_DEFAULT_TTL = 7;

    final int type;
    final long messageId;
    final int latMilli;
    final int lonMilli;
    final int epochSeconds;
    final int ttl;
    final int hop;
    final String senderName;
    final String sourceUid;
    final String targetUid;
    final String chatText;
    final String quickType;

    SosPacket(
            int type,
            long messageId,
            int latMilli,
            int lonMilli,
            int epochSeconds,
            int ttl,
            int hop,
            String senderName,
            String sourceUid,
            String targetUid,
            String chatText,
            String quickType
    ) {
        this.type = type;
        this.messageId = messageId & 0x0000FFFFFFFFFFFFL;
        this.latMilli = latMilli;
        this.lonMilli = lonMilli;
        this.epochSeconds = epochSeconds;
        this.ttl = ttl;
        this.hop = hop;
        this.senderName = sanitizeSenderName(senderName);
        this.sourceUid = sanitizeUid(sourceUid);
        this.targetUid = sanitizeUid(targetUid);
        this.chatText = sanitizeChatText(chatText);
        this.quickType = sanitizeQuickType(quickType);
    }

    static SosPacket createSos(double lat, double lon, int epochSeconds, String senderName) {
        SecureRandom random = new SecureRandom();
        long id = random.nextLong() & 0x0000FFFFFFFFFFFFL;
        int latMilli = (int) Math.max(-90000, Math.min(90000, Math.round(lat * 1000d)));
        int lonMilli = (int) Math.max(-180000, Math.min(180000, Math.round(lon * 1000d)));
        return new SosPacket(TYPE_SOS, id, latMilli, lonMilli, epochSeconds, DEFAULT_TTL, 0,
                senderName, "", "", "", "");
    }

    static SosPacket createAck(long messageId) {
        return new SosPacket(TYPE_ACK, messageId, 0, 0, 0, 0, 0,
                "", "", "", "", "");
    }

    static SosPacket createChat(
            long messageId,
            String sourceUid,
            String targetUid,
            String chatText,
            String quickType,
            int epochSeconds,
            String senderName
    ) {
        long id = messageId > 0
                ? (messageId & 0x0000FFFFFFFFFFFFL)
                : (new SecureRandom().nextLong() & 0x0000FFFFFFFFFFFFL);
        return new SosPacket(TYPE_CHAT, id, 0, 0, epochSeconds, CHAT_DEFAULT_TTL, 0,
                senderName, sourceUid, targetUid, chatText, quickType);
    }

    static SosPacket createChatAck(long messageId, String sourceUid, String targetUid) {
        return new SosPacket(TYPE_CHAT_ACK, messageId, 0, 0, 0, CHAT_DEFAULT_TTL, 0,
                "", sourceUid, targetUid, "", "");
    }

    SosPacket asRelay() {
        int nextHop = Math.min(15, hop + 1);
        int nextTtl = Math.max(0, ttl - 1);
        return new SosPacket(type, messageId, latMilli, lonMilli, epochSeconds, nextTtl, nextHop,
                senderName, sourceUid, targetUid, chatText, quickType);
    }

    boolean canRelay() {
        return ttl > 0 && (type == TYPE_SOS || type == TYPE_CHAT || type == TYPE_CHAT_ACK);
    }

    public String shortId() {
        return String.format(Locale.US, "%012X", messageId);
    }

    public int getLatMilli() {
        return latMilli;
    }

    public int getLonMilli() {
        return lonMilli;
    }

    public int getEpochSeconds() {
        return epochSeconds;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getSourceUid() {
        return sourceUid;
    }

    public String getTargetUid() {
        return targetUid;
    }

    public String getChatText() {
        return chatText;
    }

    public String getQuickType() {
        return quickType;
    }

    public boolean isChatPacket() {
        return type == TYPE_CHAT;
    }

    public boolean isChatAckPacket() {
        return type == TYPE_CHAT_ACK;
    }

    public boolean isTargetFor(String uid) {
        return uid != null && !uid.isEmpty() && uid.equals(targetUid);
    }

    private static String sanitizeSenderName(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
    }

    private static String sanitizeUid(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.length() > 96 ? trimmed.substring(0, 96) : trimmed;
    }

    private static String sanitizeChatText(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim();
        if (normalized.length() > 420) {
            return normalized.substring(0, 420);
        }
        return normalized;
    }

    private static String sanitizeQuickType(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim();
        if (normalized.length() > 32) {
            return normalized.substring(0, 32);
        }
        return normalized;
    }
}


