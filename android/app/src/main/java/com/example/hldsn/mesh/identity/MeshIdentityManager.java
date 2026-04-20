package com.example.hldsn.mesh.identity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.hldsn.mesh.model.MeshIdentity;
import com.example.hldsn.mesh.security.MeshCrypto;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Locale;
import java.util.UUID;

public class MeshIdentityManager {

    private static final String PREFS = "mesh_identity_store";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_DEVICE_NAME = "device_name";
    private static final String KEY_PUBLIC_KEY = "public_key";
    private static final String KEY_PRIVATE_KEY = "private_key";

    private final SharedPreferences prefs;
    private final MeshIdentitySyncRepository syncRepository;

    public MeshIdentityManager(@NonNull Context context) {
        Context app = context.getApplicationContext();
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.syncRepository = new MeshIdentitySyncRepository(FirebaseFirestore.getInstance());
    }

    public synchronized MeshLocalIdentity getOrCreateLocalIdentity(@Nullable String preferredDisplayName) {
        String userId = safeTrim(prefs.getString(KEY_USER_ID, ""));
        String displayName = safeTrim(prefs.getString(KEY_DISPLAY_NAME, ""));
        String deviceName = safeTrim(prefs.getString(KEY_DEVICE_NAME, ""));
        String publicKey = safeTrim(prefs.getString(KEY_PUBLIC_KEY, ""));
        String privateKey = safeTrim(prefs.getString(KEY_PRIVATE_KEY, ""));

        if (!userId.isEmpty() && !displayName.isEmpty() && !deviceName.isEmpty()
                && !publicKey.isEmpty() && !privateKey.isEmpty()) {
            if (preferredDisplayName != null && !preferredDisplayName.trim().isEmpty()
                    && !preferredDisplayName.trim().equals(displayName)) {
                displayName = preferredDisplayName.trim();
                prefs.edit().putString(KEY_DISPLAY_NAME, displayName).apply();
            }
            return new MeshLocalIdentity(userId, displayName, deviceName, publicKey, privateKey);
        }

        String finalDisplay = resolveDisplayName(preferredDisplayName);
        String finalDeviceName = resolveDeviceName();
        String finalUserId = UUID.randomUUID().toString();

        try {
            KeyPair keyPair = MeshCrypto.generateEcKeyPair();
            String finalPublic = MeshCrypto.encodeKey(keyPair.getPublic());
            String finalPrivate = MeshCrypto.encodeKey(keyPair.getPrivate());
            prefs.edit()
                    .putString(KEY_USER_ID, finalUserId)
                    .putString(KEY_DISPLAY_NAME, finalDisplay)
                    .putString(KEY_DEVICE_NAME, finalDeviceName)
                    .putString(KEY_PUBLIC_KEY, finalPublic)
                    .putString(KEY_PRIVATE_KEY, finalPrivate)
                    .apply();
            return new MeshLocalIdentity(finalUserId, finalDisplay, finalDeviceName, finalPublic, finalPrivate);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to generate mesh identity keys", e);
        }
    }

    public synchronized MeshIdentity getPublicIdentity(@Nullable String preferredDisplayName) {
        return getOrCreateLocalIdentity(preferredDisplayName).toPublicIdentity();
    }

    public void syncBestEffort(@Nullable String firebaseUid, @Nullable String preferredDisplayName) {
        MeshIdentity identity = getPublicIdentity(preferredDisplayName);
        String uid = safeTrim(firebaseUid);
        if (uid.isEmpty()) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            uid = user != null ? safeTrim(user.getUid()) : "";
        }
        if (!uid.isEmpty()) {
            syncRepository.syncBestEffort(identity, uid);
        }
    }

    private String resolveDisplayName(@Nullable String preferredDisplayName) {
        String preferred = safeTrim(preferredDisplayName);
        if (!preferred.isEmpty()) {
            return preferred;
        }
        return "User-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.US);
    }

    private String resolveDeviceName() {
        String manufacturer = safeTrim(Build.MANUFACTURER);
        String model = safeTrim(Build.MODEL);
        String merged = (manufacturer + " " + model).trim();
        return merged.isEmpty() ? "Android Device" : merged;
    }

    private String safeTrim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}

