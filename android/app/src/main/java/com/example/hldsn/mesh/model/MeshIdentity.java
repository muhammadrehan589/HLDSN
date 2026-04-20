package com.example.hldsn.mesh.model;

public class MeshIdentity {
    private final String userId;
    private final String displayName;
    private final String deviceName;
    private final String publicKey;
    private final long updatedAtMs;

    public MeshIdentity(String userId, String displayName, String deviceName, String publicKey) {
        this(userId, displayName, deviceName, publicKey, System.currentTimeMillis());
    }

    public MeshIdentity(String userId, String displayName, String deviceName, String publicKey, long updatedAtMs) {
        this.userId = userId;
        this.displayName = displayName;
        this.deviceName = deviceName;
        this.publicKey = publicKey;
        this.updatedAtMs = updatedAtMs;
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public long getUpdatedAtMs() {
        return updatedAtMs;
    }

    public String getDisplayLabel() {
        return displayName + " (" + deviceName + ")";
    }
}

