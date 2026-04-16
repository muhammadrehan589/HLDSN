package com.example.hldsn.incident_report_module;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConversationActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID   = "extra_user_id";
    public static final String EXTRA_USER_NAME = "extra_user_name";

    // ── Views ──────────────────────────────────────────────────────────────────
    private ImageView   backIcon;
    private TextView    convAvatarInitial;
    private TextView    convUserName;
    private TextView    convOnlineStatus;
    private RecyclerView messagesRecyclerView;
    private EditText    messageInput;
    private ImageView   sendButton;

    // ── Data ───────────────────────────────────────────────────────────────────
    private String otherUid;
    private String otherName;
    private String currentUid;
    private String currentName;
    private String chatId;

    private final List<ChatMessage>  messageList = new ArrayList<>();
    private ChatMessageAdapter       adapter;
    private ListenerRegistration     messagesListener;

    private FirebaseFirestore db;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        // Resolve current user
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) { finish(); return; }
        currentUid  = me.getUid();
        currentName = me.getDisplayName() != null ? me.getDisplayName() : me.getEmail();

        // Extras from intent
        otherUid  = getIntent().getStringExtra(EXTRA_USER_ID);
        otherName = getIntent().getStringExtra(EXTRA_USER_NAME);
        if (otherUid == null || otherUid.isEmpty()) { finish(); return; }

        // Deterministic chatId: sorted UIDs joined by "_"
        String[] uids = {currentUid, otherUid};
        Arrays.sort(uids);
        chatId = uids[0] + "_" + uids[1];

        db = FirebaseFirestore.getInstance();

        bindViews();
        setupRecyclerView();
        setupClickListeners();
        listenForMessages();
        markChatRead();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messagesListener != null) messagesListener.remove();
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void bindViews() {
        backIcon             = findViewById(R.id.backButton);
        convAvatarInitial    = findViewById(R.id.convAvatarInitial);
        convUserName         = findViewById(R.id.convUserName);
        convOnlineStatus     = findViewById(R.id.convOnlineStatus);
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messageInput         = findViewById(R.id.messageInput);
        sendButton           = findViewById(R.id.sendButton);

        // Populate header
        convUserName.setText(otherName != null ? otherName : "");
        String initial = (otherName != null && !otherName.isEmpty())
                ? String.valueOf(otherName.charAt(0)).toUpperCase() : "?";
        convAvatarInitial.setText(initial);
        convOnlineStatus.setText("Online");
    }

    private void setupRecyclerView() {
        adapter = new ChatMessageAdapter(this, messageList);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        messagesRecyclerView.setLayoutManager(lm);
        messagesRecyclerView.setAdapter(adapter);
    }

    private void setupClickListeners() {
        backIcon.setOnClickListener(v -> finish());

        sendButton.setOnClickListener(v -> sendMessage());

        // Enable / disable send button based on input
        messageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                sendButton.setAlpha(s.toString().trim().isEmpty() ? 0.4f : 1f);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        sendButton.setAlpha(0.4f); // start disabled
    }

    // ── Firestore listeners ────────────────────────────────────────────────────

    private void listenForMessages() {
        messagesListener = db.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    messageList.clear();
                    for (var doc : snapshots.getDocuments()) {
                        ChatMessage msg = doc.toObject(ChatMessage.class);
                        if (msg != null) {
                            msg.setMessageId(doc.getId());
                            messageList.add(msg);
                        }
                    }
                    adapter.notifyDataSetChanged();
                    if (!messageList.isEmpty()) {
                        messagesRecyclerView.scrollToPosition(messageList.size() - 1);
                    }
                });
    }

    private void markChatRead() {
        db.collection("chats").document(chatId)
                .update("unreadCounts." + currentUid, 0)
                .addOnFailureListener(e -> { /* doc may not exist yet — ignore */ });
    }

    // ── Send ───────────────────────────────────────────────────────────────────

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;
        messageInput.setText("");

        Timestamp now = Timestamp.now();

        // Build message map
        Map<String, Object> msgMap = new HashMap<>();
        msgMap.put("senderId",   currentUid);
        msgMap.put("senderName", currentName);
        msgMap.put("text",       text);
        msgMap.put("timestamp",  now);
        msgMap.put("read",       false);

        // Write message sub-document
        db.collection("chats").document(chatId)
                .collection("messages")
                .add(msgMap)
                .addOnSuccessListener(ref -> updateChatMeta(text, now))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to send message",
                                Toast.LENGTH_SHORT).show());
    }

    private void updateChatMeta(String lastText, Timestamp ts) {
        DocumentReference chatRef = db.collection("chats").document(chatId);

        Map<String, Object> chatData = new HashMap<>();
        chatData.put("participants",  Arrays.asList(currentUid, otherUid));
        chatData.put("lastMessage",   lastText);
        chatData.put("lastMessageTime", ts);
        // Increment unread count for the other user
        chatData.put("unreadCounts." + otherUid, FieldValue.increment(1));

        chatRef.set(chatData, com.google.firebase.firestore.SetOptions.merge());
    }
}
