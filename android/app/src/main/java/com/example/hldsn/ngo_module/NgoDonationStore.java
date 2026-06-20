package com.example.hldsn.ngo_module;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

public class NgoDonationStore {
    public static final String COLLECTION_NAME = "ngo_donations";

    public static void createDonation(FirebaseFirestore db, NgoDonation donation,
                                      OnSuccessListener<DocumentReference> onSuccess,
                                      OnFailureListener onFailure) {
        db.collection(COLLECTION_NAME)
                .add(donation.toMap())
                .addOnSuccessListener(docRef -> {
                    if (donation.getId() == null) donation.setId(docRef.getId());
                    onSuccess.onSuccess(docRef);
                })
                .addOnFailureListener(onFailure);
    }

    public static ListenerRegistration observeDonationsForNgo(FirebaseFirestore db, String ngoId,
                                                              EventListener<QuerySnapshot> listener) {
        return db.collection(COLLECTION_NAME)
                .whereEqualTo("ngoId", ngoId)
                .addSnapshotListener(listener);
    }
}
