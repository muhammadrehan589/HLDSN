package com.example.hldsn.incident_report_module;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.example.hldsn.sos.SosForegroundService;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ConversationActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "extra_user_id";
    public static final String EXTRA_USER_NAME = "extra_user_name";

    private static final String MESH_SERVICE_CLASS = "com.example.hldsn.sos.SosForegroundService";

    // Views
    private ImageView backIcon;
    private TextView convAvatarInitial;
    private TextView convUserName;
    private TextView convOnlineStatus;
    private RecyclerView messagesRecyclerView;
    private EditText messageInput;
    private ImageView sendButton;

    private TextView quickNeedRescue;
    private TextView quickNeedMedical;
    private TextView quickFireSeen;
    private TextView quickRoadBlocked;
    private TextView quickIAmSafe;
    private TextView quickFoundShelter;

    // Data
    private String otherUid;
    private String otherName;
    private String currentUid;
    private String currentName;
    private String chatId;

    private final List<ChatMessage> messageList = new ArrayList<>();
    private final List<ChatMessage> cloudMessages = new ArrayList<>();
    private final List<ChatMessage> offlineMessages = new ArrayList<>();
    private ChatMessageAdapter adapter;
    private ListenerRegistration messagesListener;
    private FirebaseFirestore db;
    private boolean offlineReceiverRegistered;

    private final BroadcastReceiver offlineChatReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            String action = intent.getAction();
            if (SosForegroundService.ACTION_OFFLINE_CHAT_STATUS.equals(action)) {
                handleOfflineStatus(intent);
            } else if (SosForegroundService.ACTION_OFFLINE_CHAT_RECEIVED.equals(action)) {
                handleOfflineIncoming(intent);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) {
            finish();
            return;
        }

        currentUid = me.getUid();
        currentName = me.getDisplayName() != null ? me.getDisplayName() : me.getEmail();

        otherUid = getIntent().getStringExtra(EXTRA_USER_ID);
        otherName = getIntent().getStringExtra(EXTRA_USER_NAME);
        if (otherUid == null || otherUid.isEmpty()) {
            finish();
            return;
        }

        chatId = ChatIdUtil.buildChatId(currentUid, otherUid);
        db = FirebaseFirestore.getInstance();

        bindViews();
        setupRecyclerView();
        setupClickListeners();
        setupQuickActions();

        startMeshServiceForOfflineChat();
        listenForMessages();
        markChatRead();

        reloadOfflineMessages();
        renderMergedMessages();
        updateConnectionLabel();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!offlineReceiverRegistered) {
            IntentFilter filter = new IntentFilter(SosForegroundService.ACTION_OFFLINE_CHAT_STATUS);
            filter.addAction(SosForegroundService.ACTION_OFFLINE_CHAT_RECEIVED);
            ContextCompat.registerReceiver(
                    this,
                    offlineChatReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
            );
            offlineReceiverRegistered = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateConnectionLabel();
        reloadOfflineMessages();
        renderMergedMessages();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (offlineReceiverRegistered) {
            try {
                unregisterReceiver(offlineChatReceiver);
            } catch (IllegalArgumentException ignored) {
                // Receiver may already be unregistered.
            }
            offlineReceiverRegistered = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messagesListener != null) {
            messagesListener.remove();
        }
    }

    private void bindViews() {
        backIcon = findViewById(R.id.backButton);
        convAvatarInitial = findViewById(R.id.convAvatarInitial);
        convUserName = findViewById(R.id.convUserName);
        convOnlineStatus = findViewById(R.id.convOnlineStatus);
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);

        quickNeedRescue = findViewById(R.id.quickNeedRescue);
        quickNeedMedical = findViewById(R.id.quickNeedMedical);
        quickFireSeen = findViewById(R.id.quickFireSeen);
        quickRoadBlocked = findViewById(R.id.quickRoadBlocked);
        quickIAmSafe = findViewById(R.id.quickIAmSafe);
        quickFoundShelter = findViewById(R.id.quickFoundShelter);

        convUserName.setText(otherName != null ? otherName : "");
        String initial = (otherName != null && !otherName.isEmpty())
                ? String.valueOf(otherName.charAt(0)).toUpperCase(Locale.US)
                : "?";
        convAvatarInitial.setText(initial);
    }

    private void setupRecyclerView() {
        adapter = new ChatMessageAdapter(this, messageList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        messagesRecyclerView.setLayoutManager(layoutManager);
        messagesRecyclerView.setAdapter(adapter);
    }

    private void setupClickListeners() {
        backIcon.setOnClickListener(v -> finish());

        sendButton.setOnClickListener(v -> sendTextMessage());

        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                sendButton.setAlpha(s.toString().trim().isEmpty() ? 0.4f : 1f);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        sendButton.setAlpha(0.4f);
    }

    private void setupQuickActions() {
        setupQuickAction(quickNeedRescue, "need_rescue", "Need rescue");
        setupQuickAction(quickNeedMedical, "need_medical", "Need medical");
        setupQuickAction(quickFireSeen, "fire_seen", "Fire seen");
        setupQuickAction(quickRoadBlocked, "road_blocked", "Road blocked");
        setupQuickAction(quickIAmSafe, "i_am_safe", "I am safe");
        setupQuickAction(quickFoundShelter, "found_shelter", "Found shelter");
    }

    private void setupQuickAction(View view, String quickType, String label) {
        if (view == null) {
            return;
        }
        view.setOnClickListener(v -> sendMessage(label, quickType));
    }

    private void sendTextMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        messageInput.setText("");
        sendMessage(text, "");
    }

    private void sendMessage(String text, String quickType) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        if (isInternetAvailable()) {
            sendOnlineMessage(text.trim(), safe(quickType));
        } else {
            sendOfflineMessage(text.trim(), safe(quickType));
        }
        updateConnectionLabel();
    }

    private void sendOnlineMessage(String text, String quickType) {
        Timestamp now = Timestamp.now();

        Map<String, Object> msgMap = new HashMap<>();
        msgMap.put("senderId", currentUid);
        msgMap.put("senderName", currentName);
        msgMap.put("text", text);
        msgMap.put("timestamp", now);
        msgMap.put("read", false);
        msgMap.put("messageType", quickType.isEmpty() ? "normal" : "quick");
        msgMap.put("quickType", quickType);
        msgMap.put("transportType", "online");

        db.collection("chats")
                .document(chatId)
                .collection("messages")
                .add(msgMap)
                .addOnSuccessListener(ref -> updateChatMeta(text, now))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show());
    }

    private void sendOfflineMessage(String text, String quickType) {
        long meshMessageId = generateMeshMessageId();
        String messageId = toMessageId(meshMessageId);

        ChatMessage local = new ChatMessage();
        local.setMessageId(messageId);
        local.setSenderId(currentUid);
        local.setSenderName(currentName);
        local.setText(text);
        local.setTimestamp(new Timestamp(new Date()));
        local.setRead(false);
        local.setMessageType(quickType.isEmpty() ? "normal" : "quick");
        local.setQuickType(quickType);
        local.setTransportType("offline");
        local.setDeliveryStatus("pending");

        OfflineChatStore.upsertMessage(this, chatId, local);
        reloadOfflineMessages();
        renderMergedMessages();
        scrollToBottom();

        startMeshServiceForOfflineChat();

        Intent intent = new Intent();
        intent.setClassName(getPackageName(), MESH_SERVICE_CLASS);
        intent.setAction(SosForegroundService.ACTION_SEND_OFFLINE_CHAT);
        intent.putExtra(SosForegroundService.EXTRA_CHAT_MESSAGE_ID, meshMessageId);
        intent.putExtra(SosForegroundService.EXTRA_CHAT_TARGET_UID, otherUid);
        intent.putExtra(SosForegroundService.EXTRA_CHAT_TEXT, text);
        intent.putExtra(SosForegroundService.EXTRA_CHAT_QUICK_TYPE, quickType);
        ContextCompat.startForegroundService(this, intent);
    }

    private void startMeshServiceForOfflineChat() {
        Intent intent = new Intent();
        intent.setClassName(getPackageName(), MESH_SERVICE_CLASS);
        intent.setAction(SosForegroundService.ACTION_START_MESH);
        ContextCompat.startForegroundService(this, intent);
    }

    private void listenForMessages() {
        messagesListener = db.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) {
                        return;
                    }

                    cloudMessages.clear();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots.getDocuments()) {
                        ChatMessage msg = doc.toObject(ChatMessage.class);
                        if (msg == null) {
                            continue;
                        }
                        if (msg.getMessageId() == null || msg.getMessageId().isEmpty()) {
                            msg.setMessageId(doc.getId());
                        }
                        cloudMessages.add(msg);
                    }

                    renderMergedMessages();
                });
    }

    private void reloadOfflineMessages() {
        offlineMessages.clear();
        offlineMessages.addAll(OfflineChatStore.getMessages(this, chatId));
    }

    private void renderMergedMessages() {
        Map<String, ChatMessage> merged = new LinkedHashMap<>();

        for (ChatMessage msg : offlineMessages) {
            merged.put(buildMergeKey(msg), msg);
        }

        for (ChatMessage msg : cloudMessages) {
            String key = buildMergeKey(msg);
            ChatMessage existing = merged.get(key);
            if (existing != null
                    && existing.isOfflineMessage()
                    && (msg.getDeliveryStatus() == null || msg.getDeliveryStatus().isEmpty())) {
                msg.setDeliveryStatus(existing.getDeliveryStatus());
            }
            merged.put(key, msg);
        }

        List<ChatMessage> sorted = new ArrayList<>(merged.values());
        sorted.sort((left, right) -> Long.compare(timestampOf(left), timestampOf(right)));

        messageList.clear();
        messageList.addAll(sorted);
        adapter.notifyDataSetChanged();
        scrollToBottom();
    }

    private String buildMergeKey(ChatMessage msg) {
        String id = msg.getMessageId();
        if (id != null && !id.isEmpty()) {
            return id;
        }
        return safe(msg.getSenderId()) + "_" + timestampOf(msg) + "_" + safe(msg.getText());
    }

    private long timestampOf(ChatMessage msg) {
        if (msg == null || msg.getTimestamp() == null) {
            return 0L;
        }
        return msg.getTimestamp().toDate().getTime();
    }

    private void markChatRead() {
        db.collection("chats")
                .document(chatId)
                .update("unreadCounts." + currentUid, 0)
                .addOnFailureListener(e -> {
                    // Document may not exist yet.
                });
    }

    private void updateChatMeta(String lastText, Timestamp ts) {
        DocumentReference chatRef = db.collection("chats").document(chatId);

        Map<String, Object> chatData = new HashMap<>();
        chatData.put("participants", java.util.Arrays.asList(currentUid, otherUid));
        chatData.put("lastMessage", lastText);
        chatData.put("lastMessageTime", ts);
        chatData.put("unreadCounts." + otherUid, FieldValue.increment(1));

        chatRef.set(chatData, com.google.firebase.firestore.SetOptions.merge());
    }

    private void handleOfflineStatus(Intent intent) {
        String status = safe(intent.getStringExtra(SosForegroundService.EXTRA_CHAT_STATUS));
        String statusChatId = safe(intent.getStringExtra(SosForegroundService.EXTRA_CHAT_ID));
        if (!statusChatId.isEmpty() && !chatId.equals(statusChatId)) {
            return;
        }

        long messageIdLong = intent.getLongExtra(SosForegroundService.EXTRA_CHAT_MESSAGE_ID, -1L);
        if (messageIdLong <= 0) {
            return;
        }
        String messageId = toMessageId(messageIdLong);

        OfflineChatStore.updateDeliveryStatus(this, chatId, messageId, status);
        reloadOfflineMessages();
        renderMergedMessages();

        if ("failed".equals(status)) {
            Toast.makeText(this,
                    "Could not send to this user yet. Retrying through mesh.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void handleOfflineIncoming(Intent intent) {
        String sourceUid = safe(intent.getStringExtra(SosForegroundService.EXTRA_CHAT_SOURCE_UID));
        if (!otherUid.equals(sourceUid)) {
            return;
        }

        String quickType = safe(intent.getStringExtra(SosForegroundService.EXTRA_CHAT_QUICK_TYPE));
        String text = safe(intent.getStringExtra(SosForegroundService.EXTRA_CHAT_TEXT));
        String senderName = safe(intent.getStringExtra(SosForegroundService.EXTRA_CHAT_SENDER_NAME));
        long messageIdLong = intent.getLongExtra(SosForegroundService.EXTRA_CHAT_MESSAGE_ID, -1L);
        long timestampMs = intent.getLongExtra(SosForegroundService.EXTRA_CHAT_TIMESTAMP_MS, System.currentTimeMillis());

        if (messageIdLong <= 0) {
            return;
        }

        ChatMessage message = new ChatMessage();
        message.setMessageId(toMessageId(messageIdLong));
        message.setSenderId(sourceUid);
        message.setSenderName(senderName.isEmpty() ? otherName : senderName);
        message.setText(text.isEmpty() ? quickLabel(quickType) : text);
        message.setMessageType(quickType.isEmpty() ? "normal" : "quick");
        message.setQuickType(quickType);
        message.setTransportType("offline");
        message.setDeliveryStatus("sent");
        message.setRead(false);
        message.setTimestamp(new Timestamp(new Date(timestampMs)));

        OfflineChatStore.upsertMessage(this, chatId, message);
        reloadOfflineMessages();
        renderMergedMessages();
    }

    private void updateConnectionLabel() {
        if (isInternetAvailable()) {
            convOnlineStatus.setText("Online");
            convOnlineStatus.setTextColor(Color.parseColor("#2DD09E"));
        } else {
            convOnlineStatus.setText("Offline mesh mode");
            convOnlineStatus.setTextColor(Color.parseColor("#FFDE59"));
        }
    }

    private boolean isInternetAvailable() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        Network activeNetwork = manager.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }
        NetworkCapabilities caps = manager.getNetworkCapabilities(activeNetwork);
        if (caps == null) {
            return false;
        }
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    private long generateMeshMessageId() {
        SecureRandom random = new SecureRandom();
        return random.nextLong() & 0x0000FFFFFFFFFFFFL;
    }

    private String toMessageId(long value) {
        return String.format(Locale.US, "%012X", value & 0x0000FFFFFFFFFFFFL);
    }

    private String quickLabel(String quickType) {
        switch (quickType) {
            case "need_rescue":
                return "Need rescue";
            case "need_medical":
                return "Need medical";
            case "fire_seen":
                return "Fire seen";
            case "road_blocked":
                return "Road blocked";
            case "i_am_safe":
                return "I am safe";
            case "found_shelter":
                return "Found shelter";
            default:
                return "Quick emergency message";
        }
    }

    private void scrollToBottom() {
        if (messageList.isEmpty()) {
            return;
        }
        messagesRecyclerView.scrollToPosition(messageList.size() - 1);
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
