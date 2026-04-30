package com.example.hldsn.ngo_module;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.example.hldsn.ngo_module.adapter.CampCenterLocationAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class NgoCampCenterManagementActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration campCenterListener;
    private CampCenterLocationAdapter adapter;
    private TextView emptyStateText;
    private String currentNgoId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ngo_camp_center_management);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        ImageView backButton = findViewById(R.id.backButton);
        MaterialButton addButton = findViewById(R.id.addCampCenterButton);
        RecyclerView recyclerView = findViewById(R.id.campLocationsRecyclerView);
        emptyStateText = findViewById(R.id.campLocationsEmptyText);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        adapter = new CampCenterLocationAdapter(new CampCenterLocationAdapter.CampLocationActionListener() {
            @Override
            public void onEdit(DocumentSnapshot snapshot) {
                Intent intent = new Intent(NgoCampCenterManagementActivity.this, NgoCampCenterFormActivity.class);
                intent.putExtra(NgoCampCenterFormActivity.EXTRA_DOCUMENT_ID, snapshot.getId());
                intent.putExtra(NgoCampCenterFormActivity.EXTRA_LOCATION_TYPE, snapshot.getString("type"));
                intent.putExtra(NgoCampCenterFormActivity.EXTRA_LOCATION_NAME, snapshot.getString("name"));
                intent.putExtra(NgoCampCenterFormActivity.EXTRA_LOCATION_TEXT, snapshot.getString("locationText"));
                startActivity(intent);
            }

            @Override
            public void onDelete(DocumentSnapshot snapshot) {
                confirmDelete(snapshot);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        if (addButton != null) {
            addButton.setOnClickListener(v -> startActivity(new Intent(this, NgoCampCenterFormActivity.class)));
        }

        loadCurrentNgoIdAndListen();
    }

    private void loadCurrentNgoIdAndListen() {
        if (auth == null || auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please sign in again to view locations", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users")
                .document(auth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    currentNgoId = safe(documentSnapshot.getString("ngoId"));
                    if (currentNgoId.isEmpty()) {
                        Toast.makeText(this, "Your NGO profile is missing an NGO ID", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    listenForCampCenterLocations();
                })
                .addOnFailureListener(error -> Toast.makeText(this, "Could not load NGO profile", Toast.LENGTH_SHORT).show());
    }

    private void listenForCampCenterLocations() {
        if (campCenterListener != null) {
            campCenterListener.remove();
        }

        campCenterListener = db.collection("camp_center_locations")
                .whereEqualTo("ngoId", currentNgoId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Could not load camp and center locations", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snapshot == null) {
                        return;
                    }

                    adapter.submitList(snapshot.getDocuments());
                    if (emptyStateText != null) {
                        emptyStateText.setVisibility(adapter.getItemCount() == 0 ? android.view.View.VISIBLE : android.view.View.GONE);
                    }
                });
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void confirmDelete(DocumentSnapshot snapshot) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Location")
                .setMessage("Remove this camp or center from the map?")
                .setPositiveButton("Delete", (dialog, which) ->
                        db.collection("camp_center_locations")
                                .document(snapshot.getId())
                                .delete()
                                .addOnSuccessListener(unused -> Toast.makeText(this, "Location removed", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(error -> Toast.makeText(this, "Could not remove location", Toast.LENGTH_SHORT).show()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (campCenterListener != null) {
            campCenterListener.remove();
            campCenterListener = null;
        }
    }
}