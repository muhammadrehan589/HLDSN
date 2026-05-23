package com.example.hldsn.notification_module;

import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

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

    private static String buildRejectionSubtitle(String ngoName, String reason) {
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty()) {
            return ngoName + " rejected your volunteer request.";
        }
        return ngoName + " rejected your volunteer request. Reason: " + normalizedReason;
    }
}