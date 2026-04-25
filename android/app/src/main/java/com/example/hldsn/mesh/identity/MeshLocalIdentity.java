package com.example.hldsn.mesh.identity;

import com.example.hldsn.mesh.model.MeshIdentity;

public class MeshLocalIdentity extends MeshIdentity {
    private final String privateKey;

    public MeshLocalIdentity(
            String userId,
            String displayName,
            String deviceName,
            String publicKey,
            String privateKey
    ) {
        super(userId, displayName, deviceName, publicKey);
        this.privateKey = privateKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public MeshIdentity toPublicIdentity() {
        return new MeshIdentity(getUserId(), getDisplayName(), getDeviceName(), getPublicKey());
    }
}

