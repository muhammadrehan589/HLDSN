package com.example.hldsn.mesh.storage;

import java.util.LinkedHashMap;
import java.util.Map;

public class SeenMessageStore {

    private final int maxEntries;
    private final LinkedHashMap<String, Long> seen = new LinkedHashMap<>();

    public SeenMessageStore(int maxEntries) {
        this.maxEntries = Math.max(100, maxEntries);
    }

    public synchronized boolean markSeen(String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return true;
        }
        Long existing = seen.put(messageId, System.currentTimeMillis());
        if (existing != null) {
            return true;
        }
        trim();
        return false;
    }

    private void trim() {
        while (seen.size() > maxEntries) {
            String oldest = null;
            for (Map.Entry<String, Long> entry : seen.entrySet()) {
                oldest = entry.getKey();
                break;
            }
            if (oldest == null) {
                return;
            }
            seen.remove(oldest);
        }
    }
}

