package com.example.hldsn.mesh;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.hldsn.mesh.identity.MeshIdentityManager;
import com.example.hldsn.mesh.identity.MeshLocalIdentity;
import com.example.hldsn.mesh.model.MeshIdentity;
import com.example.hldsn.mesh.model.MeshMessage;
import com.example.hldsn.mesh.model.RelayDecision;
import com.example.hldsn.mesh.protocol.MeshEnvelopeCodec;
import com.example.hldsn.mesh.relay.MeshRelayEngine;
import com.example.hldsn.mesh.storage.MeshPeerStore;
import com.example.hldsn.mesh.storage.SeenMessageStore;

import java.security.GeneralSecurityException;

public class MeshMessagingCoordinator {

    private static final String MESH_TAG = "MeshMessaging";

    private final MeshIdentityManager identityManager;
    private final MeshPeerStore peerStore;
    private final MeshRelayEngine relayEngine;

    public MeshMessagingCoordinator(Context context, @Nullable String preferredDisplayName) {
        this.identityManager = new MeshIdentityManager(context);
        this.peerStore = new MeshPeerStore(context);

        MeshLocalIdentity local = identityManager.getOrCreateLocalIdentity(preferredDisplayName);
        Log.d(MESH_TAG, "coordinator: initialized local_id=" + local.getUserId() + " display=" + local.getDisplayName());
        
        this.relayEngine = new MeshRelayEngine(local, peerStore::findByUserId, new SeenMessageStore(1024));
    }

    public MeshIdentity getLocalIdentity() {
        return identityManager.getPublicIdentity(null);
    }

    public String getLocalUserId() {
        return getLocalIdentity().getUserId();
    }

    public MeshIdentity getPeerByUserId(String userId) {
        return peerStore.findByUserId(userId);
    }

    public void saveDiscoveredPeer(MeshIdentity peer) {
        Log.d(MESH_TAG, "saveDiscoveredPeer: peer=" + peer.getDisplayLabel() + " id=" + peer.getUserId());
        peerStore.savePeer(peer);
    }

    public byte[] buildIdentityFrame() {
        byte[] frame = MeshEnvelopeCodec.encodeIdentityFrame(getLocalIdentity());
        Log.d(MESH_TAG, "buildIdentityFrame: encoded=" + frame.length + " bytes local_id=" + getLocalUserId());
        return frame;
    }

    public MeshMessage buildEncryptedMessage(String destinationUserId, String clearText, int ttl)
            throws GeneralSecurityException {
        Log.d(MESH_TAG, "buildEncryptedMessage: START dest=" + destinationUserId + " text_len=" + clearText.length() + " ttl=" + ttl);
        MeshMessage msg = relayEngine.createOutgoingMessage(destinationUserId, clearText, ttl);
        Log.d(MESH_TAG, "buildEncryptedMessage: SUCCESS msg_id=" + msg.getMessageId() + " encrypted_payload_len=" + msg.getEncryptedPayload().length());
        return msg;
    }

    public byte[] encodeMessage(MeshMessage message) {
        byte[] encoded = MeshEnvelopeCodec.encodeMessageFrame(message);
        Log.d(MESH_TAG, "encodeMessage: msg_id=" + message.getMessageId() + " frame_len=" + encoded.length);
        return encoded;
    }

    public RelayDecision onIncomingFrame(byte[] frame) {
        Log.d(MESH_TAG, "onIncomingFrame: START frame_len=" + frame.length);
        
        MeshIdentity identity = MeshEnvelopeCodec.decodeIdentityFrame(frame);
        if (identity != null) {
            Log.d(MESH_TAG, "onIncomingFrame: IDENTITY_FRAME from=" + identity.getDisplayLabel() + " id=" + identity.getUserId());
            peerStore.savePeer(identity);
            return RelayDecision.ignore(null);
        }

        MeshMessage message = MeshEnvelopeCodec.decodeMessageFrame(frame);
        if (message != null) {
            Log.d(MESH_TAG, "onIncomingFrame: MESSAGE_FRAME msg_id=" + message.getMessageId() + " from=" + message.getSourceId() 
                    + " to=" + message.getDestinationId() + " payload_len=" + message.getEncryptedPayload().length());
        }
        
        RelayDecision decision = relayEngine.onIncomingMessage(message);
        Log.d(MESH_TAG, "onIncomingFrame: decision=" + (decision != null ? decision.getAction() : "null"));
        return decision;
    }

    public void syncIdentityBestEffort(@Nullable String firebaseUid, @Nullable String preferredDisplayName) {
        Log.d(MESH_TAG, "syncIdentityBestEffort: firebase_uid=" + firebaseUid + " display=" + preferredDisplayName);
        identityManager.syncBestEffort(firebaseUid, preferredDisplayName);
    }
}

