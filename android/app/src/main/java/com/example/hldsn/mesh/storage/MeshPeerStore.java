package com.example.hldsn.mesh.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.hldsn.mesh.model.MeshIdentity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MeshPeerStore {

    private static final String TAG = "MeshPeerStore";
    private static final String PREFS = "mesh_peer_store";
    private static final String KEY_PEERS = "peers";

    private final SharedPreferences prefs;

    public MeshPeerStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void savePeer(MeshIdentity identity) {
        if (identity == null) {
            return;
        }

        JSONArray current = loadArray();
        JSONArray updated = new JSONArray();
        boolean replaced = false;

        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (identity.getUserId().equals(item.optString("user_id"))) {
                updated.put(toJson(identity));
                replaced = true;
            } else {
                updated.put(item);
            }
        }

        if (!replaced) {
            updated.put(toJson(identity));
            Log.d(TAG, "savePeer: NEW peer discovered - " + identity.getDisplayLabel()
                    + " (id=" + identity.getUserId() + ")");
        } else {
            Log.d(TAG, "savePeer: REFRESHED peer - " + identity.getDisplayLabel()
                    + " (id=" + identity.getUserId() + ")");
        }

        prefs.edit().putString(KEY_PEERS, updated.toString()).apply();
    }

    @Nullable
    public synchronized MeshIdentity findByUserId(String userId) {
        JSONArray current = loadArray();
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (userId.equals(item.optString("user_id"))) {
                return fromJson(item);
            }
        }
        return null;
    }

    public synchronized List<MeshIdentity> getAll() {
        List<MeshIdentity> peers = new ArrayList<>();
        JSONArray current = loadArray();
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item == null) {
                continue;
            }
            MeshIdentity peer = fromJson(item);
            if (peer != null) {
                peers.add(peer);
            }
        }
        return peers;
    }

    public synchronized List<MeshIdentity> getRecentPeers(long maxAgeMs) {
        long now = System.currentTimeMillis();
        List<MeshIdentity> peers = new ArrayList<>();
        JSONArray current = loadArray();
        List<String> stalePeers = new ArrayList<>();

        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item == null) {
                continue;
            }
            long updatedAt = item.optLong("updated_at", 0L);
            String displayName = item.optString("display_name", "Unknown");
            String userId = item.optString("user_id", "unknown");

            if (updatedAt <= 0L) {
                stalePeers.add(displayName);
                continue;
            }

            long ageMs = now - updatedAt;
            if (ageMs > maxAgeMs) {
                stalePeers.add(displayName + " (stale, age=" + (ageMs/1000) + "s)");
                continue;
            }

            MeshIdentity peer = fromJson(item);
            if (peer != null) {
                peers.add(peer);
                Log.d(TAG, "getRecentPeers: ACTIVE peer - " + displayName + " age=" + (ageMs/1000) + "s");
            }
        }

        if (!stalePeers.isEmpty()) {
            Log.d(TAG, "getRecentPeers: FILTERED OUT " + stalePeers.size() + " stale peers: " + stalePeers);
        }

        return peers;
    }

    private JSONArray loadArray() {
        String raw = prefs.getString(KEY_PEERS, "[]");
        try {
            return new JSONArray(raw);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private JSONObject toJson(MeshIdentity identity) {
        try {
            JSONObject object = new JSONObject();
            object.put("user_id", identity.getUserId());
            object.put("display_name", identity.getDisplayName());
            object.put("device_name", identity.getDeviceName());
            object.put("public_key", identity.getPublicKey());
            object.put("updated_at", System.currentTimeMillis());
            return object;
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to serialize peer", e);
        }
    }

    @Nullable
    private MeshIdentity fromJson(JSONObject object) {
        String userId = object.optString("user_id", "").trim();
        String displayName = object.optString("display_name", "").trim();
        String deviceName = object.optString("device_name", "").trim();
        String publicKey = object.optString("public_key", "").trim();
        long updatedAt = object.optLong("updated_at", 0L);

        if (userId.isEmpty() || displayName.isEmpty() || deviceName.isEmpty() || publicKey.isEmpty()) {
            return null;
        }
        return new MeshIdentity(userId, displayName, deviceName, publicKey, updatedAt);
    }
}

