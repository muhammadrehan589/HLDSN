package com.example.hldsn.sos;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Lightweight data carrier for an SOS alert.
 *
 * BLE payload layout (max ~20 usable bytes of service data):
 *   [0]      – 0xAA  (SOS identifier)
 *   [1..4]   – latitude  as int32  (lat * 1_000_000)
 *   [5..8]   – longitude as int32  (lng * 1_000_000)
 *   [9..N]   – sender name, UTF-8, up to 11 bytes
 *
 * Wi-Fi Direct / socket: send as JSON string (see WifiDirectSosManager).
 */
public class SosPacket {

    public static final byte SOS_IDENTIFIER = (byte) 0xAA;

    private static final int MAX_NAME_BYTES = 11;   // keeps total payload ≤ 20 bytes
    private static final int MIN_DECODE_LEN = 9;    // id(1) + lat(4) + lng(4)

    public final String senderName;
    public final double latitude;
    public final double longitude;

    public SosPacket(String senderName, double latitude, double longitude) {
        this.senderName = senderName != null ? senderName : "Unknown";
        this.latitude   = latitude;
        this.longitude  = longitude;
    }

    // ── Encode ────────────────────────────────────────────────────────────────

    /** Encode to compact byte array suitable for BLE service-data payload. */
    public byte[] encode() {
        byte[] raw  = senderName.getBytes(StandardCharsets.UTF_8);
        // Safely truncate to MAX_NAME_BYTES without splitting a multi-byte codepoint
        int    len  = Math.min(raw.length, MAX_NAME_BYTES);

        ByteBuffer buf = ByteBuffer.allocate(MIN_DECODE_LEN + len);
        buf.put(SOS_IDENTIFIER);
        buf.putInt((int) (latitude  * 1_000_000));
        buf.putInt((int) (longitude * 1_000_000));
        buf.put(raw, 0, len);
        return buf.array();
    }

    // ── Decode ────────────────────────────────────────────────────────────────

    /**
     * Decode from BLE service-data bytes.
     * Returns {@code null} if the data is not a valid SOS packet.
     */
    public static SosPacket decode(byte[] data) {
        if (data == null || data.length < MIN_DECODE_LEN) return null;
        if (data[0] != SOS_IDENTIFIER)                    return null;

        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.get();                                   // skip identifier
        double lat  = buf.getInt() / 1_000_000.0;
        double lng  = buf.getInt() / 1_000_000.0;

        byte[] nameBytes = new byte[buf.remaining()];
        buf.get(nameBytes);
        String name = new String(nameBytes, StandardCharsets.UTF_8);

        return new SosPacket(name, lat, lng);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Google Maps deep-link for the SOS location. */
    public String getMapsUrl() {
        if (latitude == 0 && longitude == 0) return "(location unavailable)";
        return "https://maps.google.com/?q=" + latitude + "," + longitude;
    }

    @Override
    public String toString() {
        return "SosPacket{name='" + senderName + "', lat=" + latitude + ", lng=" + longitude + "}";
    }
}
