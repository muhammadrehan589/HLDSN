package com.example.hldsn.mesh;

import com.example.hldsn.mesh.security.MeshCrypto;

import org.junit.Assert;
import org.junit.Test;

import java.security.KeyPair;

public class MeshCryptoTest {

    @Test
    public void encryptDecryptRoundTrip() throws Exception {
        KeyPair alice = MeshCrypto.generateEcKeyPair();
        KeyPair bob = MeshCrypto.generateEcKeyPair();

        String encrypted = MeshCrypto.encrypt(
                MeshCrypto.encodeKey(alice.getPrivate()),
                MeshCrypto.encodeKey(bob.getPublic()),
                "hello mesh"
        );

        String clear = MeshCrypto.decrypt(
                MeshCrypto.encodeKey(bob.getPrivate()),
                MeshCrypto.encodeKey(alice.getPublic()),
                encrypted
        );

        Assert.assertEquals("hello mesh", clear);
    }
}

