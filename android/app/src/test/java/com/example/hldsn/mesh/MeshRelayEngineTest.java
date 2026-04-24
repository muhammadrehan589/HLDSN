package com.example.hldsn.mesh;

import com.example.hldsn.mesh.identity.MeshLocalIdentity;
import com.example.hldsn.mesh.model.MeshIdentity;
import com.example.hldsn.mesh.model.MeshMessage;
import com.example.hldsn.mesh.model.RelayDecision;
import com.example.hldsn.mesh.relay.MeshRelayEngine;
import com.example.hldsn.mesh.security.MeshCrypto;
import com.example.hldsn.mesh.storage.SeenMessageStore;

import org.junit.Assert;
import org.junit.Test;

import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;

public class MeshRelayEngineTest {

    @Test
    public void incomingForDestinationIsDelivered() throws Exception {
        KeyPair alice = MeshCrypto.generateEcKeyPair();
        KeyPair bob = MeshCrypto.generateEcKeyPair();

        MeshLocalIdentity bobLocal = new MeshLocalIdentity(
                "bob-id",
                "Bob",
                "BobPhone",
                MeshCrypto.encodeKey(bob.getPublic()),
                MeshCrypto.encodeKey(bob.getPrivate())
        );

        Map<String, MeshIdentity> peers = new HashMap<>();
        peers.put("alice-id", new MeshIdentity(
                "alice-id",
                "Alice",
                "AlicePhone",
                MeshCrypto.encodeKey(alice.getPublic())
        ));

        MeshRelayEngine engine = new MeshRelayEngine(bobLocal, peers::get, new SeenMessageStore(256));

        String encrypted = MeshCrypto.encrypt(
                MeshCrypto.encodeKey(alice.getPrivate()),
                MeshCrypto.encodeKey(bob.getPublic()),
                "secret"
        );

        MeshMessage incoming = MeshMessage.createNew("alice-id", "bob-id", encrypted, 5);
        RelayDecision decision = engine.onIncomingMessage(incoming);

        Assert.assertEquals(RelayDecision.Action.DELIVER, decision.getAction());
        Assert.assertEquals("secret", decision.getClearText());
    }

    @Test
    public void duplicateMessageIsIgnored() throws Exception {
        KeyPair alice = MeshCrypto.generateEcKeyPair();
        KeyPair bob = MeshCrypto.generateEcKeyPair();

        MeshLocalIdentity bobLocal = new MeshLocalIdentity(
                "bob-id",
                "Bob",
                "BobPhone",
                MeshCrypto.encodeKey(bob.getPublic()),
                MeshCrypto.encodeKey(bob.getPrivate())
        );

        Map<String, MeshIdentity> peers = new HashMap<>();
        peers.put("alice-id", new MeshIdentity(
                "alice-id",
                "Alice",
                "AlicePhone",
                MeshCrypto.encodeKey(alice.getPublic())
        ));

        MeshRelayEngine engine = new MeshRelayEngine(bobLocal, peers::get, new SeenMessageStore(256));

        String encrypted = MeshCrypto.encrypt(
                MeshCrypto.encodeKey(alice.getPrivate()),
                MeshCrypto.encodeKey(bob.getPublic()),
                "duplicate"
        );

        MeshMessage incoming = MeshMessage.createNew("alice-id", "bob-id", encrypted, 5);

        RelayDecision first = engine.onIncomingMessage(incoming);
        RelayDecision second = engine.onIncomingMessage(incoming);

        Assert.assertEquals(RelayDecision.Action.DELIVER, first.getAction());
        Assert.assertEquals(RelayDecision.Action.IGNORE, second.getAction());
    }

    @Test
    public void forwardWhenNotDestinationAndTtlValid() throws Exception {
        KeyPair local = MeshCrypto.generateEcKeyPair();

        MeshLocalIdentity localIdentity = new MeshLocalIdentity(
                "relay-id",
                "Relay",
                "RelayPhone",
                MeshCrypto.encodeKey(local.getPublic()),
                MeshCrypto.encodeKey(local.getPrivate())
        );

        MeshRelayEngine engine = new MeshRelayEngine(localIdentity, userId -> null, new SeenMessageStore(256));

        MeshMessage incoming = new MeshMessage(
                "msg-1",
                "alice-id",
                "bob-id",
                "payload",
                System.currentTimeMillis(),
                2,
                0,
                com.example.hldsn.mesh.model.MessageStatus.SENT
        );

        RelayDecision decision = engine.onIncomingMessage(incoming);
        Assert.assertEquals(RelayDecision.Action.FORWARD, decision.getAction());
        Assert.assertEquals(1, decision.getMessage().getTtl());
        Assert.assertEquals(1, decision.getMessage().getHopCount());
    }
}

