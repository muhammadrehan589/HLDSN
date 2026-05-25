package com.example.hldsn.ngo_module;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.DocumentReference;

import java.util.HashMap;
import java.util.Map;

public final class NgoTaskStore {

    public static final String COLLECTION_NAME = "ngo_tasks";

    private NgoTaskStore() {
    }

    public static void createTask(FirebaseFirestore db,
                                  Map<String, Object> payload,
                                  OnCreatedListener listener,
                                  OnFailureListener failureListener) {
        if (db == null || payload == null) {
            return;
        }

        db.collection(COLLECTION_NAME)
                .add(payload)
                .addOnSuccessListener(documentReference -> {
                    if (listener != null) {
                        listener.onCreated(documentReference.getId());
                    }
                })
                .addOnFailureListener(error -> {
                    if (failureListener != null) {
                        failureListener.onFailure(error);
                    }
                });
    }

    public static ListenerRegistration observeTasksForNgo(FirebaseFirestore db, String ngoId, OnTasksListener listener) {
        if (db == null || ngoId == null || ngoId.trim().isEmpty()) {
            return null;
        }

        return db.collection(COLLECTION_NAME)
                .whereEqualTo("ngoId", ngoId.trim())
                .addSnapshotListener((snapshot, error) -> {
                    if (listener != null) {
                        listener.onTasks(snapshot, error);
                    }
                });
    }

    public static void markTaskCompleted(FirebaseFirestore db, String taskId) {
        if (db == null || taskId == null || taskId.trim().isEmpty()) {
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "completed");
        updates.put("completedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        db.collection(COLLECTION_NAME).document(taskId.trim()).set(updates, SetOptions.merge());
    }

    public interface OnCreatedListener {
        void onCreated(String taskId);
    }

    public interface OnFailureListener {
        void onFailure(Exception error);
    }

    public interface OnTasksListener {
        void onTasks(QuerySnapshot snapshot, Exception error);
    }
}