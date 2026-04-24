package com.example.hldsn.mesh.relay;

import com.example.hldsn.mesh.identity.MeshLocalIdentity;
import com.example.hldsn.mesh.model.MessageStatus;
import com.example.hldsn.mesh.model.MeshIdentity;
import com.example.hldsn.mesh.model.MeshMessage;
import com.example.hldsn.mesh.model.RelayDecision;
import com.example.hldsn.mesh.security.MeshCrypto;
import com.example.hldsn.mesh.storage.SeenMessageStore;

import java.security.GeneralSecurityException;

public class MeshRelayEngine {

    public interface PeerResolver {
        MeshIdentity resolveById(String userId);
    }

    private final MeshLocalIdentity localIdentity;
    private final PeerResolver peerResolver;
    private final SeenMessageStore seenMessageStore;

    public MeshRelayEngine(MeshLocalIdentity localIdentity, PeerResolver peerResolver, SeenMessageStore seenMessageStore) {
        this.localIdentity = localIdentity;
        this.peerResolver = peerResolver;
        this.seenMessageStore = seenMessageStore;
    }

    public MeshMessage createOutgoingMessage(String destinationId, String clearText, int ttl)
            throws GeneralSecurityException {
        MeshIdentity destination = peerResolver.resolveById(destinationId);
        if (destination == null) {
            throw new IllegalArgumentException("Unknown destination peer: " + destinationId);
        }

        String encryptedPayload = MeshCrypto.encrypt(
                localIdentity.getPrivateKey(),
                destination.getPublicKey(),
                clearText
        );

        MeshMessage message = MeshMessage.createNew(
                localIdentity.getUserId(),
                destinationId,
                encryptedPayload,
                ttl
        ).withStatus(MessageStatus.SENT);

        seenMessageStore.markSeen(message.getMessageId());
        return message;
    }

    public RelayDecision onIncomingMessage(MeshMessage message) {
        if (message == null) {
            return RelayDecision.ignore(null);
        }

        boolean alreadySeen = seenMessageStore.markSeen(message.getMessageId());
        if (alreadySeen) {
            return RelayDecision.ignore(message.withStatus(MessageStatus.DROPPED_DUPLICATE));
        }

        if (localIdentity.getUserId().equals(message.getDestinationId())) {
            MeshIdentity source = peerResolver.resolveById(message.getSourceId());
            if (source == null) {
                return RelayDecision.ignore(message.withStatus(MessageStatus.FAILED));
            }
            try {
                String clearText = MeshCrypto.decrypt(
                        localIdentity.getPrivateKey(),
                        source.getPublicKey(),
                        message.getEncryptedPayload()
                );
                return RelayDecision.deliver(message.withStatus(MessageStatus.DELIVERED), clearText);
            } catch (GeneralSecurityException e) {
                return RelayDecision.ignore(message.withStatus(MessageStatus.FAILED));
            }
        }

        if (message.getTtl() <= 0 || localIdentity.getUserId().equals(message.getSourceId())) {
            return RelayDecision.ignore(message.withStatus(MessageStatus.DROPPED_TTL_EXPIRED));
        }

        return RelayDecision.forward(message.forRelay());
    }
}

