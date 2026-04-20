package com.example.hldsn.mesh.model;

import java.util.UUID;

public class MeshMessage {
    private final String messageId;
    private final String sourceId;
    private final String destinationId;
    private final String encryptedPayload;
    private final long timestamp;
    private final int ttl;
    private final int hopCount;
    private final MessageStatus status;

    public MeshMessage(
            String messageId,
            String sourceId,
            String destinationId,
            String encryptedPayload,
            long timestamp,
            int ttl,
            int hopCount,
            MessageStatus status
    ) {
        this.messageId = messageId;
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.encryptedPayload = encryptedPayload;
        this.timestamp = timestamp;
        this.ttl = ttl;
        this.hopCount = hopCount;
        this.status = status;
    }

    public static MeshMessage createNew(String sourceId, String destinationId, String encryptedPayload, int ttl) {
        return new MeshMessage(
                UUID.randomUUID().toString(),
                sourceId,
                destinationId,
                encryptedPayload,
                System.currentTimeMillis(),
                Math.max(0, ttl),
                0,
                MessageStatus.CREATED
        );
    }

    public String getMessageId() {
        return messageId;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public String getEncryptedPayload() {
        return encryptedPayload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getTtl() {
        return ttl;
    }

    public int getHopCount() {
        return hopCount;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public MeshMessage withStatus(MessageStatus nextStatus) {
        return new MeshMessage(
                messageId,
                sourceId,
                destinationId,
                encryptedPayload,
                timestamp,
                ttl,
                hopCount,
                nextStatus
        );
    }

    public MeshMessage forRelay() {
        return new MeshMessage(
                messageId,
                sourceId,
                destinationId,
                encryptedPayload,
                timestamp,
                Math.max(0, ttl - 1),
                hopCount + 1,
                MessageStatus.RELAYED
        );
    }
}

