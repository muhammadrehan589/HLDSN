package com.example.hldsn.incident_report_module;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatsActivity extends AppCompatActivity {

    private RecyclerView     chatListRecyclerView;
    private EditText         searchChats;
    private ChatListAdapter  adapter;
    private FirebaseFirestore db;
    private String            currentUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chats);

        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) { finish(); return; }
        currentUid = me.getUid();

        db = FirebaseFirestore.getInstance();

        // ── Header buttons ───────────────────────────────────────────────────
        ImageView backIcon = findViewById(R.id.chatBackIcon);
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

        // ── Load users ───────────────────────────────────────────────────────
        loadUsers();
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadUsers() {
        db.collection("users")
                .get()                              // tries server first
                .addOnSuccessListener(this::processUserSnapshot)
                .addOnFailureListener(e -> {
                    // Server unavailable (offline) – fall back to local Firestore cache.
                    db.collection("users")
                            .get(Source.CACHE)
                            .addOnSuccessListener(this::processUserSnapshot)
                            .addOnFailureListener(e2 -> adapter.updateList(new ArrayList<>()));
                });
    }

    private void processUserSnapshot(QuerySnapshot querySnap) {
        List<DocumentSnapshot> docs = querySnap.getDocuments();
        // Filter out self
        List<DocumentSnapshot> others = new ArrayList<>();
        for (DocumentSnapshot doc : docs) {
            if (!doc.getId().equals(currentUid)) others.add(doc);
        }

        if (others.isEmpty()) {
            adapter.updateList(new ArrayList<>());
            return;
        }

        List<ChatUser> result = new ArrayList<>();
        AtomicInteger pending = new AtomicInteger(others.size());

        for (DocumentSnapshot userDoc : others) {
            String uid  = userDoc.getId();
            String name = userDoc.getString("name");
            if (name == null || name.isEmpty()) name = userDoc.getString("email");
            if (name == null) name = "Unknown";

            ChatUser chatUser = new ChatUser(uid, name,
                    userDoc.getString("photoUrl"),
                    null, null, 0);
            result.add(chatUser);

            // Fetch chat metadata for this user
            fetchChatMeta(chatUser, pending, result);
        }
    }

    private void fetchChatMeta(ChatUser chatUser, AtomicInteger pending,
                                List<ChatUser> result) {
        String[] uids = {currentUid, chatUser.getUid()};
        Arrays.sort(uids);
        String chatId = uids[0] + "_" + uids[1];

        db.collection("chats").document(chatId)
                .get()
                .addOnSuccessListener(chatDoc -> {
                    bindChatMeta(chatUser, chatDoc);
                    onChatMetaLoaded(pending, result);
                })
                .addOnFailureListener(e -> db.collection("chats").document(chatId)
                        .get(Source.CACHE)
                        .addOnSuccessListener(chatDoc -> {
                            bindChatMeta(chatUser, chatDoc);
                            onChatMetaLoaded(pending, result);
                        })
                        .addOnFailureListener(cacheError -> onChatMetaLoaded(pending, result)));
    }

    private void bindChatMeta(ChatUser chatUser, DocumentSnapshot chatDoc) {
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
        // Once all fetches done, push to adapter
        if (pending.decrementAndGet() == 0) {
            // Sort: conversations with messages first, by time desc
            result.sort((a, b) -> {
                Timestamp ta = a.getLastMessageTime();
                Timestamp tb = b.getLastMessageTime();
                if (ta == null && tb == null) return 0;
                if (ta == null) return 1;
                if (tb == null) return -1;
                return tb.compareTo(ta);
            });
            runOnUiThread(() -> adapter.updateList(result));
        }
    }
}

