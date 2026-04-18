package com.example.hldsn.incident_report_module;

import com.google.firebase.Timestamp;

public class ChatMessage {
    private String messageId;
    private String senderId;
    private String senderName;
    private String text;
    private Timestamp timestamp;
    private boolean read;
    /** "normal" for regular messages, "sos" for SOS alerts. */
    private String messageType;
    /** Quick-action code such as "need_rescue". Empty for plain text. */
    private String quickType;
    /** Delivery state for offline mesh messages: pending/failed/sent. */
    private String deliveryStatus;
    /** Transport label: online or offline. */
    private String transportType;

    // Required empty constructor for Firestore deserialization
    public ChatMessage() {}

    public ChatMessage(String messageId, String senderId, String senderName,
                       String text, Timestamp timestamp, boolean read) {
        this.messageId  = messageId;
        this.senderId   = senderId;
        this.senderName = senderName;
        this.text       = text;
        this.timestamp  = timestamp;
        this.read       = read;
    }

    public String getMessageId()  { return messageId;  }
    public String getSenderId()   { return senderId;   }
    public String getSenderName() { return senderName; }
    public String getText()       { return text;       }
    public Timestamp getTimestamp(){ return timestamp; }
    public boolean isRead()       { return read;       }

    public void setMessageId(String v)   { messageId   = v; }
    public void setSenderId(String v)    { senderId    = v; }
    public void setSenderName(String v)  { senderName  = v; }
    public void setText(String v)        { text        = v; }
    public void setTimestamp(Timestamp v){ timestamp   = v; }
    public void setRead(boolean v)       { read        = v; }
    public void setMessageType(String v) { messageType = v; }

    public String  getMessageType() { return messageType; }
    public boolean isSosMessage()   { return "sos".equals(messageType); }

    public String getQuickType() { return quickType; }
    public void setQuickType(String quickType) { this.quickType = quickType; }

    public String getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(String deliveryStatus) { this.deliveryStatus = deliveryStatus; }

    public String getTransportType() { return transportType; }
    public void setTransportType(String transportType) { this.transportType = transportType; }

    public boolean isQuickMessage() {
        return quickType != null && !quickType.isEmpty();
    }

    public boolean isOfflineMessage() {
        return "offline".equals(transportType);
    }
}
