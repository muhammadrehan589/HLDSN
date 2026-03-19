# Chat and SOS Module - Main Functionality Snippets

Use this file directly for Word copy/paste.

---

## 1. Chat Module

### A) Load chat users + fetch chat metadata

```java
private void loadUsers() {
    db.collection("users")
            .get()
            .addOnSuccessListener(querySnap -> {
                List<DocumentSnapshot> docs = querySnap.getDocuments();
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

                    ChatUser chatUser = new ChatUser(uid, name, userDoc.getString("photoUrl"), null, null, 0);
                    result.add(chatUser);
                    fetchChatMeta(chatUser, pending, result);
                }
            })
            .addOnFailureListener(e -> adapter.updateList(new ArrayList<>()));
}
```

### B) Realtime message listener + send message

```java
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

private void sendMessage() {
    String text = messageInput.getText().toString().trim();
    if (text.isEmpty()) return;
    messageInput.setText("");

    Timestamp now = Timestamp.now();
    Map<String, Object> msgMap = new HashMap<>();
    msgMap.put("senderId",   currentUid);
    msgMap.put("senderName", currentName);
    msgMap.put("text",       text);
    msgMap.put("timestamp",  now);
    msgMap.put("read",       false);

    db.collection("chats").document(chatId)
            .collection("messages")
            .add(msgMap)
            .addOnSuccessListener(ref -> updateChatMeta(text, now));
}
```

### C) Special SOS message rendering in chat UI

```java
@Override
public int getItemViewType(int position) {
    ChatMessage msg = messages.get(position);
    if (msg.isSosMessage()) return VIEW_TYPE_SOS;
    return msg.getSenderId() != null && msg.getSenderId().equals(currentUid)
            ? VIEW_TYPE_SENT
            : VIEW_TYPE_RECEIVED;
}
```

---

## 2. SOS Emergency Module

### A) Emergency button trigger from UI

```java
private void triggerSos() {
    SosManager sosManager = new SosManager(this);
    sosManager.triggerSos(new SosManager.SosTriggerCallback() {
        @Override
        public void onStarted() {
            runOnUiThread(() ->
                    Toast.makeText(HomePageActivity.this,
                            "🊘 SOS sent! Alerting your contacts...",
                            Toast.LENGTH_LONG).show());
        }

        @Override
        public void onOnlineBroadcastComplete(int contactsReached) {
            runOnUiThread(() ->
                    Toast.makeText(HomePageActivity.this,
                            "✅ SOS alert delivered to " + contactsReached + " contact(s)",
                            Toast.LENGTH_LONG).show());
        }

        @Override
        public void onOfflineStarted() {
            runOnUiThread(() ->
                    Toast.makeText(HomePageActivity.this,
                            "📲 No internet - broadcasting SOS via Bluetooth & Wi-Fi Direct",
                            Toast.LENGTH_LONG).show());
        }

        @Override
        public void onError(String reason) {
            runOnUiThread(() ->
                    Toast.makeText(HomePageActivity.this,
                            "⚠️ SOS error: " + reason,
                            Toast.LENGTH_LONG).show());
        }
    });
}
```

### B) SOS core flow (feedback, location, online/offline branching)

```java
public void triggerSos(SosTriggerCallback callback) {
    if (sosActive) {
        Log.w(TAG, "SOS already active, ignoring duplicate trigger");
        return;
    }
    sosActive = true;

    triggerAlarmFeedback();
    if (callback != null) callback.onStarted();

    new LocationHelper(context).getLocation(context, new LocationHelper.LocationCallback2() {
        @Override
        public void onLocationReceived(double lat, double lng) {
            if (isInternetAvailable()) {
                broadcastOnline(lat, lng, callback);
            } else {
                broadcastOffline(lat, lng, callback);
            }
        }

        @Override
        public void onFailed(String reason) {
            if (isInternetAvailable()) {
                broadcastOnline(0, 0, callback);
            } else {
                broadcastOffline(0, 0, callback);
            }
        }
    });
}
```

### C) Online SOS broadcast to all chat threads (messageType = "sos")

```java
private void broadcastOnline(double lat, double lng, SosTriggerCallback callback) {
    FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
    if (me == null) {
        if (callback != null) callback.onError("User not logged in");
        sosActive = false;
        return;
    }

    String currentUid  = me.getUid();
    String senderName  = resolveName(me);
    String mapsUrl     = (lat != 0 || lng != 0)
            ? "https://maps.google.com/?q=" + lat + "," + lng
            : "(location unavailable)";
    String sosText     = "🆘 SOS ALERT! " + senderName + " needs immediate help!\n"
                       + "📍 Location: " + mapsUrl;

    FirebaseFirestore db = FirebaseFirestore.getInstance();

    db.collection("users").get()
            .addOnSuccessListener(snap -> {
                for (var doc : snap.getDocuments()) {
                    String otherUid = doc.getId();
                    if (otherUid.equals(currentUid)) continue;

                    String[] uids = {currentUid, otherUid};
                    Arrays.sort(uids);
                    String chatId = uids[0] + "_" + uids[1];

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("senderId",    currentUid);
                    msg.put("senderName",  senderName);
                    msg.put("text",        sosText);
                    msg.put("timestamp",   Timestamp.now());
                    msg.put("read",        false);
                    msg.put("messageType", "sos");

                    db.collection("chats").document(chatId)
                            .collection("messages")
                            .add(msg);
                }
            });
}
```

---

## Source files used

- android/app/src/main/java/com/example/hldsn/incident_report_module/ChatsActivity.java
- android/app/src/main/java/com/example/hldsn/incident_report_module/ConversationActivity.java
- android/app/src/main/java/com/example/hldsn/incident_report_module/ChatMessageAdapter.java
- android/app/src/main/java/com/example/hldsn/home/HomePageActivity.java
- android/app/src/main/java/com/example/hldsn/sos/SosManager.java
