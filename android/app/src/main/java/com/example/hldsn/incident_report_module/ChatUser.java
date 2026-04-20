package com.example.hldsn.incident_report_module;

import com.google.firebase.Timestamp;

public class ChatUser {
    private String uid;
    private String name;
    private String photoUrl;
    private String meshUserId;
    private String meshPublicKey;
    private String meshDeviceName;
    private String lastMessage;
    private Timestamp lastMessageTime;
    private int unreadCount;

    // Required empty constructor for Firestore deserialization
    public ChatUser() {}

    public ChatUser(String uid, String name, String photoUrl,
                    String lastMessage, Timestamp lastMessageTime, int unreadCount) {
        this.uid             = uid;
        this.name            = name;
        this.photoUrl        = photoUrl;
        this.lastMessage     = lastMessage;
        this.lastMessageTime = lastMessageTime;
        this.unreadCount     = unreadCount;
    }

    public String    getUid()             { return uid;             }
    public String    getName()            { return name;            }
    public String    getPhotoUrl()        { return photoUrl;        }
    public String    getMeshUserId()      { return meshUserId;      }
    public String    getMeshPublicKey()   { return meshPublicKey;   }
    public String    getMeshDeviceName()  { return meshDeviceName;  }
    public String    getLastMessage()     { return lastMessage;     }
    public Timestamp getLastMessageTime() { return lastMessageTime; }
    public int       getUnreadCount()     { return unreadCount;     }

    public void setUid(String v)              { uid             = v; }
    public void setName(String v)             { name            = v; }
    public void setPhotoUrl(String v)         { photoUrl        = v; }
    public void setMeshUserId(String v)       { meshUserId      = v; }
    public void setMeshPublicKey(String v)    { meshPublicKey   = v; }
    public void setMeshDeviceName(String v)   { meshDeviceName  = v; }
    public void setLastMessage(String v)      { lastMessage     = v; }
    public void setLastMessageTime(Timestamp v){ lastMessageTime = v; }
    public void setUnreadCount(int v)         { unreadCount     = v; }
}
