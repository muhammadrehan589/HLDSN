package com.example.hldsn.sos;

import java.security.SecureRandom;
import java.util.Locale;

public final class SosPacket {

    static final int TYPE_SOS = 1;
    static final int TYPE_ACK = 2;
    static final int DEFAULT_TTL = 5;

    final int type;
    final long messageId;
    final int latMilli;
    final int lonMilli;
    final int epochSeconds;
    final int ttl;
    final int hop;
    final String senderName;

    SosPacket(int type, long messageId, int latMilli, int lonMilli, int epochSeconds, int ttl, int hop, String senderName) {
        this.type = type;
        this.messageId = messageId;
        this.latMilli = latMilli;
        this.lonMilli = lonMilli;
        this.epochSeconds = epochSeconds;
        this.ttl = ttl;
        this.hop = hop;
        this.senderName = sanitizeSenderName(senderName);
    }

    static SosPacket createSos(double lat, double lon, int epochSeconds, String senderName) {
        SecureRandom random = new SecureRandom();
        long id = random.nextLong() & 0x0000FFFFFFFFFFFFL;
        return createSos(id, lat, lon, epochSeconds, senderName);
    }

    static SosPacket createSos(long id, double lat, double lon, int epochSeconds, String senderName) {
        int latMilli = (int) Math.max(-90000, Math.min(90000, Math.round(lat * 1000d)));
        int lonMilli = (int) Math.max(-180000, Math.min(180000, Math.round(lon * 1000d)));
        return new SosPacket(TYPE_SOS, id & 0x0000FFFFFFFFFFFFL, latMilli, lonMilli, epochSeconds, DEFAULT_TTL, 0, senderName);
    }

    static SosPacket createAck(long messageId) {
        return new SosPacket(TYPE_ACK, messageId & 0x0000FFFFFFFFFFFFL, 0, 0, 0, 0, 0, null);
    }

    SosPacket asRelay() {
        int nextHop = Math.min(15, hop + 1);
        int nextTtl = Math.max(0, ttl - 1);
        return new SosPacket(TYPE_SOS, messageId, latMilli, lonMilli, epochSeconds, nextTtl, nextHop, senderName);
    }

    boolean canRelay() {
        return type == TYPE_SOS && ttl > 0;
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
}


