package com.example.hldsn.sos;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

final class SosCodec {

    static final int BLE_MANUFACTURER_ID = 0x7A11;
    static final int BLE_BEACON_SIZE = 8;
    static final int FULL_FRAME_BASE_SIZE = 19;
    static final int MAX_SENDER_NAME_BYTES = 32;
    static final int MAX_UID_BYTES = 96;
    static final int MAX_QUICK_TYPE_BYTES = 32;
    static final int MAX_CHAT_TEXT_BYTES = 420;
    private static final int MIN_CHAT_FRAME_SIZE = 18;
    private static final int MIN_CHAT_ACK_FRAME_SIZE = 11;

    private SosCodec() {
    }

    static byte[] encodeFullFrame(SosPacket packet) {
        if (packet.type == SosPacket.TYPE_CHAT) {
            return encodeChatFrame(packet);
        }
        if (packet.type == SosPacket.TYPE_CHAT_ACK) {
            return encodeChatAckFrame(packet);
        }

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

    private static byte[] encodeChatFrame(SosPacket packet) {
        byte[] sourceUidBytes = limitUtf8(packet.getSourceUid(), MAX_UID_BYTES);
        byte[] targetUidBytes = limitUtf8(packet.getTargetUid(), MAX_UID_BYTES);
        byte[] quickTypeBytes = limitUtf8(packet.getQuickType(), MAX_QUICK_TYPE_BYTES);
        byte[] textBytes = limitUtf8(packet.getChatText(), MAX_CHAT_TEXT_BYTES);
        byte[] senderNameBytes = limitUtf8(packet.getSenderName(), MAX_SENDER_NAME_BYTES);

        int frameSize = 1 + 6 + 4 + 1 + 1
                + 1 + sourceUidBytes.length
                + 1 + targetUidBytes.length
                + 1 + quickTypeBytes.length
                + 2 + textBytes.length
                + 1 + senderNameBytes.length;

        ByteBuffer buffer = ByteBuffer.allocate(frameSize);
        buffer.put((byte) (packet.type & 0xFF));
        putUInt48(buffer, packet.messageId);
        buffer.putInt(packet.epochSeconds);
        buffer.put((byte) (packet.ttl & 0xFF));
        buffer.put((byte) (packet.hop & 0xFF));
        putByteString(buffer, sourceUidBytes);
        putByteString(buffer, targetUidBytes);
        putByteString(buffer, quickTypeBytes);
        putShortString(buffer, textBytes);
        putByteString(buffer, senderNameBytes);
        return buffer.array();
    }

    private static byte[] encodeChatAckFrame(SosPacket packet) {
        byte[] sourceUidBytes = limitUtf8(packet.getSourceUid(), MAX_UID_BYTES);
        byte[] targetUidBytes = limitUtf8(packet.getTargetUid(), MAX_UID_BYTES);

        int frameSize = 1 + 6 + 1 + 1
                + 1 + sourceUidBytes.length
                + 1 + targetUidBytes.length;

        ByteBuffer buffer = ByteBuffer.allocate(frameSize);
        buffer.put((byte) (packet.type & 0xFF));
        putUInt48(buffer, packet.messageId);
        buffer.put((byte) (packet.ttl & 0xFF));
        buffer.put((byte) (packet.hop & 0xFF));
        putByteString(buffer, sourceUidBytes);
        putByteString(buffer, targetUidBytes);
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
        if (frame == null || frame.length < 7) {
            return null;
        }

        int type = frame[0] & 0xFF;
        if (type == SosPacket.TYPE_CHAT) {
            return decodeChatFrame(frame);
        }
        if (type == SosPacket.TYPE_CHAT_ACK) {
            return decodeChatAckFrame(frame);
        }
        if (frame.length < FULL_FRAME_BASE_SIZE) {
            return null;
        }

        ByteBuffer buffer = ByteBuffer.wrap(frame);
        buffer.get();
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
        return new SosPacket(type, id, latMilli, lonMilli, epochSeconds, ttl, hop,
            senderName, "", "", "", "");
    }

    private static SosPacket decodeChatFrame(byte[] frame) {
        if (frame.length < MIN_CHAT_FRAME_SIZE) {
            return null;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(frame);
            int type = buffer.get() & 0xFF;
            if (type != SosPacket.TYPE_CHAT) {
                return null;
            }

            long id = readUInt48(buffer);
            int epochSeconds = buffer.getInt();
            int ttl = buffer.get() & 0xFF;
            int hop = buffer.get() & 0xFF;

            String sourceUid = readByteString(buffer, MAX_UID_BYTES);
            String targetUid = readByteString(buffer, MAX_UID_BYTES);
            String quickType = readByteString(buffer, MAX_QUICK_TYPE_BYTES);
            String text = readShortString(buffer, MAX_CHAT_TEXT_BYTES);
            String senderName = readByteString(buffer, MAX_SENDER_NAME_BYTES);

            return new SosPacket(
                    SosPacket.TYPE_CHAT,
                    id,
                    0,
                    0,
                    epochSeconds,
                    ttl,
                    hop,
                    senderName,
                    sourceUid,
                    targetUid,
                    text,
                    quickType
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static SosPacket decodeChatAckFrame(byte[] frame) {
        if (frame.length < MIN_CHAT_ACK_FRAME_SIZE) {
            return null;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(frame);
            int type = buffer.get() & 0xFF;
            if (type != SosPacket.TYPE_CHAT_ACK) {
                return null;
            }

            long id = readUInt48(buffer);
            int ttl = buffer.get() & 0xFF;
            int hop = buffer.get() & 0xFF;
            String sourceUid = readByteString(buffer, MAX_UID_BYTES);
            String targetUid = readByteString(buffer, MAX_UID_BYTES);

            return new SosPacket(
                    SosPacket.TYPE_CHAT_ACK,
                    id,
                    0,
                    0,
                    0,
                    ttl,
                    hop,
                    "",
                    sourceUid,
                    targetUid,
                    "",
                    ""
            );
        } catch (Exception ignored) {
            return null;
        }
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
        if (type != SosPacket.TYPE_SOS
                && type != SosPacket.TYPE_ACK
                && type != SosPacket.TYPE_CHAT
                && type != SosPacket.TYPE_CHAT_ACK) {
            return null;
        }
        if (type == SosPacket.TYPE_ACK) {
            return SosPacket.createAck(id);
        }
        if (type == SosPacket.TYPE_CHAT_ACK) {
            return new SosPacket(type, id, 0, 0, 0, ttl, hop,
                    "", "", "", "", "");
        }
        return new SosPacket(type, id, 0, 0, 0, ttl, hop,
                "", "", "", "", "");
    }

    private static byte[] limitUtf8(String value, int maxBytes) {
        byte[] bytes = value != null ? value.getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (bytes.length <= maxBytes) {
            return bytes;
        }
        byte[] clipped = new byte[maxBytes];
        System.arraycopy(bytes, 0, clipped, 0, maxBytes);
        return clipped;
    }

    private static void putByteString(ByteBuffer buffer, byte[] bytes) {
        int len = Math.min(255, bytes.length);
        buffer.put((byte) (len & 0xFF));
        if (len > 0) {
            buffer.put(bytes, 0, len);
        }
    }

    private static void putShortString(ByteBuffer buffer, byte[] bytes) {
        int len = Math.min(65535, bytes.length);
        buffer.putShort((short) (len & 0xFFFF));
        if (len > 0) {
            buffer.put(bytes, 0, len);
        }
    }

    private static String readByteString(ByteBuffer buffer, int maxBytes) {
        if (!buffer.hasRemaining()) {
            return "";
        }
        int declaredLen = buffer.get() & 0xFF;
        int clipped = Math.min(declaredLen, maxBytes);
        int readable = Math.min(clipped, buffer.remaining());
        if (readable <= 0) {
            skip(buffer, Math.min(declaredLen, buffer.remaining()));
            return "";
        }

        byte[] payload = new byte[readable];
        buffer.get(payload);
        if (declaredLen > readable) {
            skip(buffer, Math.min(declaredLen - readable, buffer.remaining()));
        }
        return new String(payload, StandardCharsets.UTF_8).trim();
    }

    private static String readShortString(ByteBuffer buffer, int maxBytes) {
        if (buffer.remaining() < 2) {
            return "";
        }
        int declaredLen = buffer.getShort() & 0xFFFF;
        int clipped = Math.min(declaredLen, maxBytes);
        int readable = Math.min(clipped, buffer.remaining());
        if (readable <= 0) {
            skip(buffer, Math.min(declaredLen, buffer.remaining()));
            return "";
        }

        byte[] payload = new byte[readable];
        buffer.get(payload);
        if (declaredLen > readable) {
            skip(buffer, Math.min(declaredLen - readable, buffer.remaining()));
        }
        return new String(payload, StandardCharsets.UTF_8).trim();
    }

    private static void skip(ByteBuffer buffer, int count) {
        if (count <= 0) {
            return;
        }
        buffer.position(buffer.position() + count);
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

