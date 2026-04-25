package com.example.hldsn.incident_report_module;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.example.hldsn.mesh.model.MeshIdentity;
import com.example.hldsn.mesh.storage.MeshPeerStore;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatsActivity extends AppCompatActivity {

    private static final String TAG = "ChatsActivity";
    // Increased from 45s to 10 minutes to handle very flaky Wi-Fi Direct connections
    // and allow time for connection recovery while user navigates between screens
    private static final long NEARBY_PEER_MAX_AGE_MS = 600_000L;
    private static final long PEER_REFRESH_MS = 3_000L;  // Refresh every 3 seconds instead of 6
    private static final String EXTRA_MESH_USER_ID = "extra_mesh_user_id";
    private static final String EXTRA_MESH_PUBLIC_KEY = "extra_mesh_public_key";
    private static final String EXTRA_MESH_DEVICE_NAME = "extra_mesh_device_name";

    private RecyclerView     chatListRecyclerView;
    private EditText         searchChats;
    private ChatListAdapter  adapter;
    private FirebaseFirestore db;
    private String            currentUid;
    private MeshPeerStore meshPeerStore;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            loadNearbyUsers();
            refreshHandler.postDelayed(this, PEER_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chats);

        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) { finish(); return; }
        currentUid = me.getUid();

        db = FirebaseFirestore.getInstance();
        meshPeerStore = new MeshPeerStore(this);

        // ── Header buttons ───────────────────────────────────────────────────
        ImageView backIcon = findViewById(R.id.backButton);
        if (backIcon != null) backIcon.setOnClickListener(v -> finish());

        // communityTab → ReportIncidentActivity
        android.view.View communityTab = findViewById(R.id.communityTab);
        if (communityTab != null) {
            communityTab.setOnClickListener(v ->
                    startActivity(new Intent(this, ReportIncidentActivity.class)));
        }

        // communitybtn → DisplayReportActivity
        android.view.View communitybtn = findViewById(R.id.communitybtn);
        if (communitybtn != null) {
            communitybtn.setOnClickListener(v ->
                    startActivity(new Intent(this, DisplayReportActivity.class)));
        }

        // ── RecyclerView ─────────────────────────────────────────────────────
        chatListRecyclerView = findViewById(R.id.chatListRecyclerView);
        chatListRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ChatListAdapter(this, new ArrayList<>(), user -> {
            Intent intent = new Intent(ChatsActivity.this, ConversationActivity.class);
            intent.putExtra(ConversationActivity.EXTRA_USER_ID,   user.getUid());
            intent.putExtra(ConversationActivity.EXTRA_USER_NAME, user.getName());
            intent.putExtra(EXTRA_MESH_USER_ID, user.getMeshUserId());
            intent.putExtra(EXTRA_MESH_PUBLIC_KEY, user.getMeshPublicKey());
            intent.putExtra(EXTRA_MESH_DEVICE_NAME, user.getMeshDeviceName());
            startActivity(intent);
        });
        chatListRecyclerView.setAdapter(adapter);

        // ── Search ───────────────────────────────────────────────────────────
        searchChats = findViewById(R.id.searchChats);
        if (searchChats != null) {
            searchChats.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    adapter.getFilter().filter(s);
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        // ── Load nearby mesh peers ──────────────────────────────────────────
        loadNearbyUsers();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Load peers immediately when entering chat screen
        loadNearbyUsers();
        // Start periodic refresh while viewing chat
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.post(refreshRunnable);
        Log.d(TAG, "onStart: peer refresh started");
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Keep peer refresh running in background (don't stop it)
        // This ensures peers remain available even when not viewing chat
        Log.d(TAG, "onStop: peer refresh continues in background");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Only stop refresh when activity is destroyed (not just stopped)
        refreshHandler.removeCallbacks(refreshRunnable);
        Log.d(TAG, "onDestroy: peer refresh stopped");
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadNearbyUsers() {
        List<MeshIdentity> nearbyPeers = meshPeerStore.getRecentPeers(NEARBY_PEER_MAX_AGE_MS);
        List<MeshIdentity> allKnownPeers = meshPeerStore.getAll();
        Map<String, MeshIdentity> deduped = new LinkedHashMap<>();
        for (MeshIdentity peer : nearbyPeers) {
            if (peer != null && peer.getUserId() != null && !peer.getUserId().trim().isEmpty()) {
                deduped.put(peer.getUserId(), peer);
            }
        }
        for (MeshIdentity peer : allKnownPeers) {
            if (peer != null && peer.getUserId() != null && !peer.getUserId().trim().isEmpty()) {
                deduped.putIfAbsent(peer.getUserId(), peer);
            }
        }
        nearbyPeers = new ArrayList<>(deduped.values());
        long now = System.currentTimeMillis();
        
        Log.d(TAG, "loadNearbyUsers: found " + nearbyPeers.size() + " peers total (recent-first), recent_window="
                + (NEARBY_PEER_MAX_AGE_MS / 1000) + "s");

        if (!nearbyPeers.isEmpty()) {
            for (MeshIdentity peer : nearbyPeers) {
                long ageMs = now - peer.getUpdatedAtMs();
                Log.d(TAG, "  - Peer: " + peer.getDisplayLabel() + " (id=" + peer.getUserId() + ") age=" + (ageMs/1000) + "s");
            }
        }
        
        if (nearbyPeers.isEmpty()) {
            Log.d(TAG, "loadNearbyUsers: no nearby peers, showing empty state");
            renderUsers(new ArrayList<>());
            return;
        }

        List<ChatUser> result = new ArrayList<>();
        AtomicInteger pending = new AtomicInteger(nearbyPeers.size());

        for (MeshIdentity peer : nearbyPeers) {
            if (peer == null || peer.getUserId() == null || peer.getUserId().trim().isEmpty()) {
                onChatMetaLoaded(pending, result);
                continue;
            }
            if (peer.getUserId().equals(currentUid)) {
                Log.d(TAG, "loadNearbyUsers: skipping self peer id");
                onChatMetaLoaded(pending, result);
                continue;
            }

            ChatUser chatUser = new ChatUser(peer.getUserId(), peer.getDisplayLabel(),
                    null, null, null, 0);
            chatUser.setMeshUserId(peer.getUserId());
            chatUser.setMeshPublicKey(peer.getPublicKey());
            chatUser.setMeshDeviceName(peer.getDeviceName());
            result.add(chatUser);
            
            Log.d(TAG, "loadNearbyUsers: added peer to chat list: " + peer.getDisplayLabel());

            fetchChatMeta(chatUser, pending, result);
        }

        // Show discovered peers immediately; chat metadata can arrive later.
        renderSortedUsers(result);
    }

    private void fetchChatMeta(ChatUser chatUser, AtomicInteger pending,
                                List<ChatUser> result) {
        String peerKey = chatUser.getMeshUserId() != null && !chatUser.getMeshUserId().trim().isEmpty()
                ? chatUser.getMeshUserId().trim()
                : chatUser.getUid();
        String[] uids = {currentUid, peerKey};
        Arrays.sort(uids);
        String chatId = uids[0] + "_" + uids[1];

        db.collection("chats").document(chatId)
                .get()
                .addOnSuccessListener(chatDoc -> {
                    bindChatMeta(chatUser, chatDoc);
                    onChatMetaLoaded(pending, result);
                })
                .addOnFailureListener(e -> onChatMetaLoaded(pending, result));
    }

    private void bindChatMeta(ChatUser chatUser, com.google.firebase.firestore.DocumentSnapshot chatDoc) {
        if (chatDoc == null || !chatDoc.exists()) {
            return;
        }
        chatUser.setLastMessage(chatDoc.getString("lastMessage"));
        Timestamp ts = chatDoc.getTimestamp("lastMessageTime");
        chatUser.setLastMessageTime(ts);

        Long unread = chatDoc.getLong("unreadCounts." + currentUid);
        chatUser.setUnreadCount(unread != null ? unread.intValue() : 0);
    }

    private void onChatMetaLoaded(AtomicInteger pending, List<ChatUser> result) {
        pending.decrementAndGet();
        // Keep UI responsive by applying incremental metadata updates.
        renderSortedUsers(result);
    }

    private void renderSortedUsers(List<ChatUser> users) {
        List<ChatUser> sorted = new ArrayList<>(users);
        sorted.sort((a, b) -> {
            Timestamp ta = a.getLastMessageTime();
            Timestamp tb = b.getLastMessageTime();
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
        runOnUiThread(() -> renderUsers(sorted));
    }

    private void renderUsers(List<ChatUser> users) {
        adapter.updateList(users);
    }
}

