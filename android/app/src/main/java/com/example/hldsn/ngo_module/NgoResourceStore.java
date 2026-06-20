package com.example.hldsn.ngo_module;

import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.DocumentReference;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.Map;

public class NgoResourceStore {
    public static final String COLLECTION_NAME = "ngo_resources";

    public static void createResource(FirebaseFirestore db, NgoResource resource, OnSuccessListener<DocumentReference> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION_NAME)
                .add(resource.toMap())
                .addOnSuccessListener(docRef -> {
                    if (resource.getId() == null) resource.setId(docRef.getId());
                    onSuccess.onSuccess(docRef);
                })
                .addOnFailureListener(onFailure);
    }

    public static void updateResource(FirebaseFirestore db, String resourceId, Map<String,Object> updates, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION_NAME)
                .document(resourceId)
                .set(updates)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public static void deductQuantity(FirebaseFirestore db, String resourceId, int amount,
                                      OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION_NAME)
                .document(resourceId)
                .update("quantity", FieldValue.increment(-amount))
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public static com.google.firebase.firestore.ListenerRegistration observeResourcesForNgo(FirebaseFirestore db, String ngoId, EventListener<QuerySnapshot> listener) {
        return db.collection(COLLECTION_NAME)
                .whereEqualTo("ngoId", ngoId)
                .addSnapshotListener(listener);
    }

}

