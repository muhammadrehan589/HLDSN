package com.example.hldsn.incident_report_module;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.hldsn.mesh.model.MeshIdentity;
import com.example.hldsn.mesh.storage.MeshPeerStore;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ChatsViewModel extends AndroidViewModel {

    private static final String TAG = "ChatsViewModel";
    private static final long NEARBY_PEER_MAX_AGE_MS = 600_000L;
    private static final long PEER_REFRESH_MS = 3_000L;

    private final FirebaseFirestore db;
    private final MeshPeerStore meshPeerStore;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final MutableLiveData<List<ChatUser>> onlineUsers = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<ChatUser>> nearbyUsers = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");

    private final AtomicLong onlineVersion = new AtomicLong(0L);
    private final AtomicLong nearbyVersion = new AtomicLong(0L);

    private String currentUid;
    private ListenerRegistration usersRegistration;
    private boolean streamsRunning;

    private final Runnable nearbyRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshNearbyUsers();
            handler.postDelayed(this, PEER_REFRESH_MS);
        }
    };

    public ChatsViewModel(@NonNull Application application) {
        super(application);
        db = FirebaseFirestore.getInstance();
        meshPeerStore = new MeshPeerStore(application.getApplicationContext());

        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        currentUid = me != null ? me.getUid() : null;
    }

    public LiveData<List<ChatUser>> getOnlineUsers() {
        return onlineUsers;
    }

    public LiveData<List<ChatUser>> getNearbyUsers() {
        return nearbyUsers;
    }

    public LiveData<String> getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String query) {
        searchQuery.setValue(query == null ? "" : query.trim());
    }

    public void startDataStreams() {
        if (streamsRunning) {
            return;
        }
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        currentUid = me != null ? me.getUid() : null;
        if (currentUid == null || currentUid.trim().isEmpty()) {
            return;
        }

        Log.d(TAG, "startDataStreams: starting streams for user " + currentUid);

        streamsRunning = true;
        startOnlineUsersListener();
        handler.removeCallbacks(nearbyRefreshRunnable);
        handler.post(nearbyRefreshRunnable);
    }

    public void stopDataStreams() {
        Log.d(TAG, "stopDataStreams: stopping streams");
        streamsRunning = false;
        handler.removeCallbacks(nearbyRefreshRunnable);
        if (usersRegistration != null) {
            usersRegistration.remove();
            usersRegistration = null;
        }
    }

    private void startOnlineUsersListener() {
        if (usersRegistration != null || currentUid == null) {
            Log.w(TAG, "startOnlineUsersListener: already registered or no current user");
            return;
        }

        Log.d(TAG, "startOnlineUsersListener: starting listener for users collection");
        usersRegistration = db.collection("users")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "startOnlineUsersListener: error in listener", e);
                        return;
                    }

                    if (snapshots == null) {
                        Log.w(TAG, "startOnlineUsersListener: snapshots is null");
                        return;
                    }

                    long version = onlineVersion.incrementAndGet();
                    List<ChatUser> users = new ArrayList<>();
                    Log.d(TAG, "startOnlineUsersListener: got " + snapshots.size() + " documents");

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String uid = safeTrim(doc.getId());
                        Log.d(TAG, "Processing user: " + uid);

                        if (uid.isEmpty() || uid.equals(currentUid)) {
                            Log.d(TAG, "Skipping user: " + uid);
                            continue;
                        }

                        String displayName = resolveDisplayName(doc);
                        Log.d(TAG, "User " + uid + " name: " + displayName);

                        ChatUser chatUser = new ChatUser(uid, displayName,
                                doc.getString("profileImageUrl"), null, null, 0);
                        chatUser.setMeshUserId(safeTrim(doc.getString("user_id")));
                        chatUser.setMeshPublicKey(safeTrim(doc.getString("public_key")));
                        chatUser.setMeshDeviceName(safeTrim(doc.getString("device_name")));

                        // Track presence for display purposes
                        Boolean isOnline = doc.getBoolean("isOnline");
                        chatUser.setOnline(isOnline != null && isOnline);

                        Timestamp lastSeenTime = doc.getTimestamp("lastSeenTime");
                        if (lastSeenTime != null) {
                            chatUser.setLastSeenTime(lastSeenTime);
                        }

                        users.add(chatUser);
                    }

                    Log.d(TAG, "startOnlineUsersListener: posting " + users.size() + " users");
                    // Push base list immediately, then enrich with last-message metadata.
                    postSortedOnlineUsers(users, version);
                    enrichWithChatMeta(users, version, true);
                });
    }

    private void refreshNearbyUsers() {
        if (currentUid == null || currentUid.trim().isEmpty()) {
            nearbyUsers.postValue(new ArrayList<>());
            return;
        }

        long version = nearbyVersion.incrementAndGet();
        List<MeshIdentity> recentPeers = meshPeerStore.getRecentPeers(NEARBY_PEER_MAX_AGE_MS);
        List<MeshIdentity> allPeers = meshPeerStore.getAll();
        Map<String, MeshIdentity> deduped = new LinkedHashMap<>();

        for (MeshIdentity peer : recentPeers) {
            if (peer != null) {
                String peerId = safeTrim(peer.getUserId());
                if (!peerId.isEmpty()) {
                    deduped.put(peerId, peer);
                }
            }
        }

        for (MeshIdentity peer : allPeers) {
            if (peer != null) {
                String peerId = safeTrim(peer.getUserId());
                if (!peerId.isEmpty()) {
                    deduped.putIfAbsent(peerId, peer);
                }
            }
        }

        List<ChatUser> users = new ArrayList<>();
        for (MeshIdentity peer : deduped.values()) {
            String peerId = safeTrim(peer.getUserId());
            if (peerId.isEmpty() || peerId.equals(currentUid)) {
                continue;
            }

            ChatUser chatUser = new ChatUser(peerId, peer.getDisplayLabel(), null,
                    null, null, 0);
            chatUser.setMeshUserId(peerId);
            chatUser.setMeshPublicKey(peer.getPublicKey());
            chatUser.setMeshDeviceName(peer.getDeviceName());
            users.add(chatUser);
        }

        postSortedNearbyUsers(users, version);
        enrichWithChatMeta(users, version, false);
    }

    private void enrichWithChatMeta(List<ChatUser> users, long version, boolean isOnlineStream) {
        if (users.isEmpty() || currentUid == null) {
            if (isOnlineStream) {
                postSortedOnlineUsers(users, version);
            } else {
                postSortedNearbyUsers(users, version);
            }
            return;
        }

        AtomicInteger pending = new AtomicInteger(users.size());
        for (ChatUser chatUser : users) {
            fetchChatMeta(chatUser, () -> {
                if (pending.decrementAndGet() == 0) {
                    if (isOnlineStream) {
                        postSortedOnlineUsers(users, version);
                    } else {
                        postSortedNearbyUsers(users, version);
                    }
                }
            });
        }
    }

    private void fetchChatMeta(ChatUser chatUser, Runnable onDone) {
        String peerKey = safeTrim(chatUser.getMeshUserId());
        if (peerKey.isEmpty()) {
            peerKey = safeTrim(chatUser.getUid());
        }
        if (peerKey.isEmpty() || currentUid == null) {
            onDone.run();
            return;
        }

        String[] ids = {currentUid, peerKey};
        Arrays.sort(ids);
        String chatId = ids[0] + "_" + ids[1];

        db.collection("chats").document(chatId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        chatUser.setLastMessage(doc.getString("lastMessage"));
                        chatUser.setLastMessageTime(doc.getTimestamp("lastMessageTime"));
                        Long unread = doc.getLong("unreadCounts." + currentUid);
                        chatUser.setUnreadCount(unread != null ? unread.intValue() : 0);
                    }
                    onDone.run();
                })
                .addOnFailureListener(e -> onDone.run());
    }

    private void postSortedOnlineUsers(List<ChatUser> users, long version) {
        if (version != onlineVersion.get()) {
            return;
        }
        onlineUsers.postValue(sortUsers(users));
    }

    private void postSortedNearbyUsers(List<ChatUser> users, long version) {
        if (version != nearbyVersion.get()) {
            return;
        }
        nearbyUsers.postValue(sortUsers(users));
    }

    private List<ChatUser> sortUsers(List<ChatUser> users) {
        List<ChatUser> sorted = new ArrayList<>(users);
        sorted.sort((a, b) -> {
            Timestamp ta = a.getLastMessageTime();
            Timestamp tb = b.getLastMessageTime();
            if (ta == null && tb == null) {
                return safeTrim(a.getName()).compareToIgnoreCase(safeTrim(b.getName()));
            }
            if (ta == null) {
                return 1;
            }
            if (tb == null) {
                return -1;
            }
            return tb.compareTo(ta);
        });
        return sorted;
    }

    private String resolveDisplayName(DocumentSnapshot doc) {
        String name = safeTrim(doc.getString("name"));
        if (!name.isEmpty()) {
            return name;
        }

        String displayName = safeTrim(doc.getString("display_name"));
        if (!displayName.isEmpty()) {
            return displayName;
        }

        String firstName = safeTrim(doc.getString("firstName"));
        String lastName = safeTrim(doc.getString("lastName"));
        String fullName = (firstName + " " + lastName).trim();
        if (!fullName.isEmpty()) {
            return fullName;
        }

        String email = safeTrim(doc.getString("email"));
        if (!email.isEmpty()) {
            int at = email.indexOf('@');
            return at > 0 ? email.substring(0, at) : email;
        }

        return "HLDSN User";
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    protected void onCleared() {
        stopDataStreams();
        super.onCleared();
    }
}


