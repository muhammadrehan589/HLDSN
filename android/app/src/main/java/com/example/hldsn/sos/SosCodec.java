package com.example.hldsn.sos;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

final class SosCodec {

    static final int BLE_MANUFACTURER_ID = 0x7A11;
    static final int BLE_BEACON_SIZE = 8;
    static final int FULL_FRAME_BASE_SIZE = 19;
    static final int MAX_SENDER_NAME_BYTES = 32;

    private SosCodec() {
    }

    static byte[] encodeFullFrame(SosPacket packet) {
        byte[] senderBytes = packet.type == SosPacket.TYPE_SOS
                ? packet.getSenderName().getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        if (senderBytes.length > MAX_SENDER_NAME_BYTES) {
            byte[] limited = new byte[MAX_SENDER_NAME_BYTES];
            System.arraycopy(senderBytes, 0, limited, 0, MAX_SENDER_NAME_BYTES);
            senderBytes = limited;
        }

        int frameSize = FULL_FRAME_BASE_SIZE + (packet.type == SosPacket.TYPE_SOS ? (1 + senderBytes.length) : 0);
        ByteBuffer buffer = ByteBuffer.allocate(frameSize);
        buffer.put((byte) (packet.type & 0xFF));
        putUInt48(buffer, packet.messageId);
        putInt24(buffer, packet.latMilli);
        putInt24(buffer, packet.lonMilli);
        buffer.putInt(packet.epochSeconds);
        buffer.put((byte) (packet.ttl & 0xFF));
        buffer.put((byte) (packet.hop & 0xFF));
        if (packet.type == SosPacket.TYPE_SOS) {
            buffer.put((byte) (senderBytes.length & 0xFF));
            if (senderBytes.length > 0) {
                buffer.put(senderBytes);
            }
        }
        return buffer.array();
    }

    static byte[] encodeBleBeacon(SosPacket packet) {
        ByteBuffer buffer = ByteBuffer.allocate(BLE_BEACON_SIZE);
        buffer.put((byte) (packet.type & 0xFF));
        putUInt48(buffer, packet.messageId);
        int ttlNibble = Math.max(0, Math.min(15, packet.ttl));
        int hopNibble = Math.max(0, Math.min(15, packet.hop));
        buffer.put((byte) ((ttlNibble << 4) | hopNibble));
        return buffer.array();
    }

    static SosPacket decodeFullFrame(byte[] frame) {
        if (frame == null || frame.length < FULL_FRAME_BASE_SIZE) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(frame);
        int type = buffer.get() & 0xFF;
        long id = readUInt48(buffer);
        int latMilli = readInt24(buffer);
        int lonMilli = readInt24(buffer);
        int epochSeconds = buffer.getInt();
        int ttl = buffer.get() & 0xFF;
        int hop = buffer.get() & 0xFF;
        if (type != SosPacket.TYPE_SOS && type != SosPacket.TYPE_ACK) {
            return null;
        }
        if (type == SosPacket.TYPE_ACK) {
            return SosPacket.createAck(id);
        }

        String senderName = "";
        int remaining = buffer.remaining();
        if (remaining > 0) {
            int declaredLen = buffer.get() & 0xFF;
            if (declaredLen > MAX_SENDER_NAME_BYTES) {
                declaredLen = MAX_SENDER_NAME_BYTES;
            }
            int readable = Math.min(declaredLen, buffer.remaining());
            if (readable > 0) {
                byte[] senderBytes = new byte[readable];
                buffer.get(senderBytes);
                senderName = new String(senderBytes, StandardCharsets.UTF_8).trim();
            }
        }
        return new SosPacket(type, id, latMilli, lonMilli, epochSeconds, ttl, hop, senderName);
    }

    static SosPacket decodeBleBeacon(byte[] frame) {
        if (frame == null || frame.length != BLE_BEACON_SIZE) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(frame);
        int type = buffer.get() & 0xFF;
        long id = readUInt48(buffer);
        int packed = buffer.get() & 0xFF;
        int ttl = (packed >> 4) & 0x0F;
        int hop = packed & 0x0F;
        if (type != SosPacket.TYPE_SOS && type != SosPacket.TYPE_ACK) {
            return null;
        }
        if (type == SosPacket.TYPE_ACK) {
            return SosPacket.createAck(id);
        }
        return new SosPacket(type, id, 0, 0, 0, ttl, hop, "");
    }

    private static void putUInt48(ByteBuffer buffer, long value) {
        buffer.put((byte) ((value >> 40) & 0xFF));
        buffer.put((byte) ((value >> 32) & 0xFF));
        buffer.put((byte) ((value >> 24) & 0xFF));
        buffer.put((byte) ((value >> 16) & 0xFF));
        buffer.put((byte) ((value >> 8) & 0xFF));
        buffer.put((byte) (value & 0xFF));
    }

    private static long readUInt48(ByteBuffer buffer) {
        long value = 0;
        for (int i = 0; i < 6; i++) {
            value = (value << 8) | (buffer.get() & 0xFFL);
        }
        return value;
    }

    private static void putInt24(ByteBuffer buffer, int value) {
        int clamped = Math.max(-0x800000, Math.min(0x7FFFFF, value));
        buffer.put((byte) ((clamped >> 16) & 0xFF));
        buffer.put((byte) ((clamped >> 8) & 0xFF));
        buffer.put((byte) (clamped & 0xFF));
    }

    private static int readInt24(ByteBuffer buffer) {
        int b1 = buffer.get() & 0xFF;
        int b2 = buffer.get() & 0xFF;
        int b3 = buffer.get() & 0xFF;
        int value = (b1 << 16) | (b2 << 8) | b3;
        if ((value & 0x800000) != 0) {
            value |= 0xFF000000;
        }
        return value;
    }
}

