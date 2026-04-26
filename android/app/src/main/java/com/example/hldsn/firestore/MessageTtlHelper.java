package com.example.hldsn.firestore;

import com.google.firebase.Timestamp;

public final class MessageTtlHelper {

    private static final long MESSAGE_TTL_SECONDS = 24L * 60L * 60L;

    private MessageTtlHelper() {
        // Utility class
    }

    public static Timestamp calculateExpireAt(Timestamp baseTimestamp) {
        if (baseTimestamp == null) {
            baseTimestamp = Timestamp.now();
        }
        return new Timestamp(
                baseTimestamp.getSeconds() + MESSAGE_TTL_SECONDS,
                baseTimestamp.getNanoseconds()
        );
    }
}

