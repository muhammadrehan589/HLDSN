package com.example.hldsn.mesh.identity;

import com.example.hldsn.mesh.model.MeshIdentity;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MeshIdentitySyncRepository {

    private final FirebaseFirestore firestore;

    public MeshIdentitySyncRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public void syncBestEffort(MeshIdentity identity, String firebaseUid) {
        if (identity == null || firebaseUid == null || firebaseUid.trim().isEmpty()) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("user_id", identity.getUserId());
        payload.put("display_name", identity.getDisplayName());
        payload.put("device_name", identity.getDeviceName());
        payload.put("public_key", identity.getPublicKey());
        payload.put("backup_uid", firebaseUid);
        payload.put("updated_at", FieldValue.serverTimestamp());

        firestore.collection("mesh_identities")
                .document(identity.getUserId())
                .set(payload, com.google.firebase.firestore.SetOptions.merge());

        Map<String, Object> userPatch = new HashMap<>();
        userPatch.put("user_id", identity.getUserId());
        userPatch.put("display_name", identity.getDisplayName());
        userPatch.put("device_name", identity.getDeviceName());
        userPatch.put("public_key", identity.getPublicKey());
        userPatch.put("mesh_synced_at", Timestamp.now());

        firestore.collection("users")
                .document(firebaseUid)
                .set(userPatch, com.google.firebase.firestore.SetOptions.merge());
    }
}

