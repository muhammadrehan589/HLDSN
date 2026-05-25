package com.example.hldsn.notification_module;

import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public final class UserNotificationStore {

    public static final String COLLECTION_NAME = "user_notifications";

    private UserNotificationStore() {
    }

    public static void createVolunteerApplicationNotification(FirebaseFirestore db,
                                                             String recipientUid,
                                                             String ngoId,
                                                             String ngoName,
                                                             String volunteerUid,
                                                             String volunteerName,
                                                             String applicationId) {
        if (db == null || recipientUid == null || recipientUid.trim().isEmpty()) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientUid", recipientUid.trim());
        payload.put("type", "volunteer_application");
        payload.put("title", "New Volunteer Application");
        payload.put("subtitle", volunteerName + " applied for " + ngoName);
        payload.put("detailLabel", "Review");
        payload.put("ngoId", ngoId);
        payload.put("ngoName", ngoName);
        payload.put("volunteerUid", volunteerUid);
        payload.put("volunteerName", volunteerName);
        payload.put("applicationId", applicationId);
        payload.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection(COLLECTION_NAME).add(payload);
    }

    public static void createVolunteerDecisionNotification(FirebaseFirestore db,
                                                           String recipientUid,
                                                           String ngoId,
                                                           String ngoName,
                                                           String volunteerName,
                                                           String decision,
                                                           String reason,
                                                           String applicationId) {
        if (db == null || recipientUid == null || recipientUid.trim().isEmpty()) {
            return;
        }

        String normalizedDecision = decision == null ? "" : decision.trim().toLowerCase();
        boolean approved = "approved".equals(normalizedDecision);

        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientUid", recipientUid.trim());
        payload.put("type", approved ? "volunteer_approved" : "volunteer_rejected");
        payload.put("title", approved ? "Volunteer Request Approved" : "Volunteer Request Rejected");
        payload.put("subtitle", approved
                ? ngoName + " accepted your volunteer request."
                : buildRejectionSubtitle(ngoName, reason));
        payload.put("detailLabel", approved ? "View" : "View Reason");
        payload.put("ngoId", ngoId);
        payload.put("ngoName", ngoName);
        payload.put("volunteerName", volunteerName);
        payload.put("decision", approved ? "approved" : "rejected");
        payload.put("reason", reason == null ? "" : reason.trim());
        payload.put("applicationId", applicationId);
        payload.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection(COLLECTION_NAME).add(payload);
    }

    public static void createTaskAssignmentNotification(FirebaseFirestore db,
                                                       String recipientUid,
                                                       String ngoId,
                                                       String ngoName,
                                                       String taskId,
                                                       String taskTitle,
                                                       String taskDescription,
                                                       String assignedByUid,
                                                       String volunteerName) {
        if (db == null || recipientUid == null || recipientUid.trim().isEmpty()) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientUid", recipientUid.trim());
        payload.put("type", "task_assignment");
        payload.put("title", "New Task Assigned");
        payload.put("subtitle", firstSentence(taskTitle, taskDescription, ngoName));
        payload.put("detailLabel", "Respond");
        payload.put("ngoId", ngoId);
        payload.put("ngoName", ngoName);
        payload.put("taskId", taskId);
        payload.put("taskTitle", taskTitle);
        payload.put("taskDescription", taskDescription);
        payload.put("assignedByUid", assignedByUid);
        payload.put("volunteerUid", recipientUid.trim());
        payload.put("assignedToName", volunteerName);
        payload.put("taskStatus", "pending");
        payload.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection(COLLECTION_NAME).add(payload);
    }

    public static void createTaskCompletionNotification(FirebaseFirestore db,
                                                      String recipientUid,
                                                      String ngoId,
                                                      String ngoName,
                                                      String taskId,
                                                      String taskTitle,
                                                      String volunteerUid,
                                                      String volunteerName) {
        if (db == null || recipientUid == null || recipientUid.trim().isEmpty()) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientUid", recipientUid.trim());
        payload.put("type", "task_completed");
        payload.put("title", "Task Completed");
        payload.put("subtitle", firstNonBlank(volunteerName, "Volunteer") + " marked " + firstNonBlank(taskTitle, "a task") + " as completed.");
        payload.put("detailLabel", "View");
        payload.put("ngoId", ngoId);
        payload.put("ngoName", ngoName);
        payload.put("taskId", taskId);
        payload.put("taskTitle", taskTitle);
        payload.put("volunteerUid", volunteerUid);
        payload.put("volunteerName", volunteerName);
        payload.put("taskStatus", "completed");
        payload.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection(COLLECTION_NAME).add(payload);
    }

    public static ListenerRegistration observeNotificationsForUser(FirebaseFirestore db,
                                                                   String recipientUid,
                                                                   EventListener<QuerySnapshot> listener) {
        if (db == null || recipientUid == null || recipientUid.trim().isEmpty()) {
            return null;
        }
        return db.collection(COLLECTION_NAME)
                .whereEqualTo("recipientUid", recipientUid.trim())
                .addSnapshotListener(listener);
    }

    public static void deleteNotificationById(FirebaseFirestore db, String notificationId) {
        if (db == null || notificationId == null || notificationId.trim().isEmpty()) {
            return;
        }
        db.collection(COLLECTION_NAME).document(notificationId.trim()).delete();
    }

    public static void respondToTaskAssignment(FirebaseFirestore db,
                                               String notificationId,
                                               String taskId,
                                               String volunteerUid,
                                               boolean accepted,
                                               String rejectionReason) {
        if (db == null || taskId == null || taskId.trim().isEmpty()) {
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", accepted ? "ongoing" : "rejected");
        updates.put("respondedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        updates.put("respondedByUid", volunteerUid == null ? "" : volunteerUid.trim());
        updates.put("rejectionReason", accepted ? com.google.firebase.firestore.FieldValue.delete() : rejectionReason);

        db.collection("ngo_tasks").document(taskId.trim()).set(updates, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    if (notificationId != null && !notificationId.trim().isEmpty()) {
                        deleteNotificationById(db, notificationId);
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
        db.collection("ngo_tasks").document(taskId.trim()).set(updates, com.google.firebase.firestore.SetOptions.merge());
    }

    public static void markTaskCompletedAndNotifyNgo(FirebaseFirestore db,
                                                     String taskId,
                                                     String recipientUid,
                                                     String ngoId,
                                                     String ngoName,
                                                     String taskTitle,
                                                     String volunteerUid,
                                                     String volunteerName,
                                                     OnCompletionListener listener) {
        if (db == null || taskId == null || taskId.trim().isEmpty()) {
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "completed");
        updates.put("completedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        updates.put("completedByUid", volunteerUid == null ? "" : volunteerUid.trim());

        db.collection("ngo_tasks").document(taskId.trim()).set(updates, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    createTaskCompletionNotification(
                            db,
                            recipientUid,
                            ngoId,
                            ngoName,
                            taskId,
                            taskTitle,
                            volunteerUid,
                            volunteerName
                    );
                    if (listener != null) {
                        listener.onSuccess();
                    }
                })
                .addOnFailureListener(error -> {
                    if (listener != null) {
                        listener.onFailure(error);
                    }
                });
    }

    private static String buildRejectionSubtitle(String ngoName, String reason) {
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty()) {
            return ngoName + " rejected your volunteer request.";
        }
        return ngoName + " rejected your volunteer request. Reason: " + normalizedReason;
    }

    private static String firstSentence(String taskTitle, String taskDescription, String ngoName) {
        String title = taskTitle == null ? "" : taskTitle.trim();
        if (!title.isEmpty()) {
            return title;
        }

        String description = taskDescription == null ? "" : taskDescription.trim();
        if (!description.isEmpty()) {
            return description;
        }

        return ngoName == null || ngoName.trim().isEmpty() ? "You have a new task" : ngoName.trim() + " assigned a task";
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.trim().isEmpty() ? fallback : primary.trim();
    }

    public interface OnCompletionListener {
        void onSuccess();

        void onFailure(Exception error);
    }
}