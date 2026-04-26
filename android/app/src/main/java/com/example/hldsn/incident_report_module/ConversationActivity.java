package com.example.hldsn.incident_report_module;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.example.hldsn.firestore.MessageTtlHelper;
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

    private static final String MESH_TAG = "MeshMessaging";
    private static final String MESSAGE_LOG_TAG = "MessageWriteAudit";
    public static final String EXTRA_USER_ID   = "extra_user_id";
    public static final String EXTRA_USER_NAME = "extra_user_name";
    public static final String EXTRA_MESH_USER_ID = "extra_mesh_user_id";
    public static final String EXTRA_MESH_PUBLIC_KEY = "extra_mesh_public_key";
    public static final String EXTRA_MESH_DEVICE_NAME = "extra_mesh_device_name";
    private static final String SOS_SERVICE_CLASS = "com.example.hldsn.sos.SosForegroundService";
    private static final String ACTION_SEND_MESH_MESSAGE = "com.example.hldsn.sos.ACTION_SEND_MESH_MESSAGE";
    private static final String EXTRA_MESH_DESTINATION_ID = "extra_mesh_destination_id";
    private static final String EXTRA_MESH_TEXT = "extra_mesh_text";
    private static final String EXTRA_MESH_TTL = "extra_mesh_ttl";
    private static final String EXTRA_MESH_DISPLAY_NAME = "extra_mesh_display_name";

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
    private String destinationMeshUserId;
    private String destinationMeshPublicKey;
    private String destinationMeshDeviceName;

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
        destinationMeshUserId = safeTrim(getIntent().getStringExtra(EXTRA_MESH_USER_ID));
        destinationMeshPublicKey = safeTrim(getIntent().getStringExtra(EXTRA_MESH_PUBLIC_KEY));
        destinationMeshDeviceName = safeTrim(getIntent().getStringExtra(EXTRA_MESH_DEVICE_NAME));
        if (destinationMeshUserId.isEmpty()) {
            destinationMeshUserId = otherUid;
        }

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
        backIcon             = findViewById(R.id.convBackIcon);
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
        sendMeshMessageIfPossible(text);

        Timestamp now = Timestamp.now();
        Timestamp expireAt = MessageTtlHelper.calculateExpireAt(now);

        // Build message map
        Map<String, Object> msgMap = new HashMap<>();
        msgMap.put("senderId",   currentUid);
        msgMap.put("senderName", currentName);
        msgMap.put("text",       text);
        msgMap.put("timestamp",  now);
        msgMap.put("expireAt",   expireAt);
        msgMap.put("read",       false);

        String messageCollectionPath = "chats/" + chatId + "/messages";
        Log.i(MESSAGE_LOG_TAG, "WRITE_REQUEST collection=" + messageCollectionPath + " payload=" + msgMap);

        // Write message sub-document
        db.collection("chats").document(chatId)
                .collection("messages")
                .add(msgMap)
                .addOnSuccessListener(ref -> {
                    updateChatMeta(text, now);
                    Log.i(MESSAGE_LOG_TAG, "WRITE_SUCCESS docPath=" + ref.getPath() + " expireAt_present=" + msgMap.containsKey("expireAt"));
                    ref.get()
                            .addOnSuccessListener(snapshot -> Log.i(MESSAGE_LOG_TAG,
                                    "WRITE_READBACK docPath=" + ref.getPath() + " data=" + snapshot.getData()))
                            .addOnFailureListener(e -> Log.w(MESSAGE_LOG_TAG,
                                    "WRITE_READBACK_FAILED docPath=" + ref.getPath(), e));
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to send message",
                                Toast.LENGTH_SHORT).show());
    }

    private void sendMeshMessageIfPossible(String text) {
        if (!destinationMeshUserId.isEmpty()) {
            Log.d(MESH_TAG, "sendMeshMessageIfPossible: using peer metadata dest=" + destinationMeshUserId
                    + " has_pubkey=" + !destinationMeshPublicKey.isEmpty());

            Intent meshIntent = new Intent();
            meshIntent.setClassName(getPackageName(), SOS_SERVICE_CLASS);
            meshIntent.setAction(ACTION_SEND_MESH_MESSAGE);
            meshIntent.putExtra(EXTRA_MESH_DESTINATION_ID, destinationMeshUserId);
            meshIntent.putExtra(EXTRA_MESH_TEXT, text);
            meshIntent.putExtra(EXTRA_MESH_TTL, 5);
            if (!destinationMeshPublicKey.isEmpty()) {
                meshIntent.putExtra(EXTRA_MESH_PUBLIC_KEY, destinationMeshPublicKey);
            }
            meshIntent.putExtra(EXTRA_MESH_DISPLAY_NAME,
                    otherName != null && !otherName.trim().isEmpty() ? otherName.trim() : "Peer");
            if (!destinationMeshDeviceName.isEmpty()) {
                meshIntent.putExtra(EXTRA_MESH_DEVICE_NAME, destinationMeshDeviceName);
            }

            Log.d(MESH_TAG, "sendMeshMessageIfPossible: starting mesh send service");
            startForegroundService(meshIntent);
            return;
        }

        Log.d(MESH_TAG, "sendMeshMessageIfPossible: no peer metadata, querying firestore for dest=" + otherUid);
        db.collection("users")
                .document(otherUid)
                .get()
                .addOnSuccessListener(doc -> {
                    String destinationMeshId = doc != null ? doc.getString("user_id") : null;
                    if (destinationMeshId == null || destinationMeshId.trim().isEmpty()) {
                        Log.w(MESH_TAG, "sendMeshMessageIfPossible: user has no mesh id");
                        return;
                    }
                    String destinationPublicKey = doc.getString("public_key");
                    String destinationDisplayName = doc.getString("display_name");
                    String destinationDeviceName = doc.getString("device_name");

                    Log.d(MESH_TAG, "sendMeshMessageIfPossible: firestore lookup dest=" + destinationMeshId
                            + " has_pubkey=" + (destinationPublicKey != null && !destinationPublicKey.isEmpty()));

                    Intent meshIntent = new Intent();
                    meshIntent.setClassName(getPackageName(), SOS_SERVICE_CLASS);
                    meshIntent.setAction(ACTION_SEND_MESH_MESSAGE);
                    meshIntent.putExtra(EXTRA_MESH_DESTINATION_ID, destinationMeshId.trim());
                    meshIntent.putExtra(EXTRA_MESH_TEXT, text);
                    meshIntent.putExtra(EXTRA_MESH_TTL, 5);
                    if (destinationPublicKey != null && !destinationPublicKey.trim().isEmpty()) {
                        meshIntent.putExtra(EXTRA_MESH_PUBLIC_KEY, destinationPublicKey.trim());
                    }
                    meshIntent.putExtra(EXTRA_MESH_DISPLAY_NAME,
                            destinationDisplayName != null && !destinationDisplayName.trim().isEmpty()
                                    ? destinationDisplayName.trim()
                                    : (otherName != null ? otherName : "Peer"));
                    if (destinationDeviceName != null && !destinationDeviceName.trim().isEmpty()) {
                        meshIntent.putExtra(EXTRA_MESH_DEVICE_NAME, destinationDeviceName.trim());
                    }

                    Log.d(MESH_TAG, "sendMeshMessageIfPossible: starting mesh send service");
                    startForegroundService(meshIntent);
                })
                .addOnFailureListener(e -> {
                    Log.e(MESH_TAG, "sendMeshMessageIfPossible: firestore lookup failed", e);
                });
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
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
