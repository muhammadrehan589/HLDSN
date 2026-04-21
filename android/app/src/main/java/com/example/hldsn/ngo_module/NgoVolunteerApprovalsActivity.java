package com.example.hldsn.ngo_module;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.example.hldsn.ngo_module.adapter.NgoVolunteerApplicationAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class NgoVolunteerApprovalsActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private NgoVolunteerApplicationAdapter adapter;

    private ListenerRegistration applicationListener;
    private EditText searchField;

    private String currentUid = "";
    private String currentNgoId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ngo_volunteer_approvals);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        ImageView backButton = findViewById(R.id.backButton);
        searchField = findViewById(R.id.volunteerSearchField);
        Spinner statusSpinner = findViewById(R.id.volunteerStatusFilterSpinner);
        RecyclerView recyclerView = findViewById(R.id.volunteerRequestRecyclerView);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        adapter = new NgoVolunteerApplicationAdapter(new NgoVolunteerApplicationAdapter.VolunteerActionListener() {
            @Override
            public void onApprove(com.google.firebase.firestore.DocumentSnapshot snapshot) {
                approveVolunteer(snapshot);
            }

            @Override
            public void onReject(com.google.firebase.firestore.DocumentSnapshot snapshot) {
                showRejectDialog(snapshot);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        if (searchField != null) {
            searchField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    adapter.setSearchQuery(s == null ? "" : s.toString());
                }
            });
        }

        if (statusSpinner != null) {
            String[] statuses = new String[] {"All", "Pending", "Approved", "Rejected"};
            statusSpinner.setAdapter(new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_dropdown_item,
                    statuses
            ));
            statusSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                    String selected = statuses[position];
                    if ("All".equalsIgnoreCase(selected)) {
                        adapter.setStatusFilter("all");
                    } else {
                        adapter.setStatusFilter(selected.toLowerCase());
                    }
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    adapter.setStatusFilter("all");
                }
            });
        }

        loadNgoContext();
    }

    private void loadNgoContext() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentUid = user.getUid();
        db.collection("users")
                .document(currentUid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    currentNgoId = safe(documentSnapshot.getString("ngoId"));
                    if (currentNgoId.isEmpty()) {
                        Toast.makeText(this, "No NGO assigned to this account", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    listenForVolunteerRequests();
                })
                .addOnFailureListener(error -> {
                    Toast.makeText(this, "Could not load NGO profile", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void listenForVolunteerRequests() {
        if (applicationListener != null) {
            applicationListener.remove();
        }

        applicationListener = db.collection("volunteer_applications")
                .whereEqualTo("ngoId", currentNgoId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Could not load volunteer requests", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snapshot == null) {
                        return;
                    }

                    adapter.submitList(snapshot.getDocuments());
                    if (searchField != null) {
                        adapter.setSearchQuery(searchField.getText() == null ? "" : searchField.getText().toString());
                    }
                });
    }

    private void approveVolunteer(com.google.firebase.firestore.DocumentSnapshot snapshot) {
        String status = safe(snapshot.getString("status")).toLowerCase();
        if (!"pending".equals(status)) {
            Toast.makeText(this, "Only pending requests can be approved", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Approve Volunteer")
                .setMessage("Approve this volunteer application?")
                .setPositiveButton("Approve", (dialog, which) -> {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("status", "approved");
                    updates.put("ngoReviewedAt", FieldValue.serverTimestamp());
                    updates.put("ngoReviewedBy", currentUid);
                    updates.put("rejectionReason", FieldValue.delete());

                    db.collection("volunteer_applications")
                            .document(snapshot.getId())
                            .set(updates, SetOptions.merge())
                            .addOnSuccessListener(unused -> {
                                String applicantUid = safe(snapshot.getString("uid"));
                                if (!applicantUid.isEmpty()) {
                                    Map<String, Object> userUpdates = new HashMap<>();
                                    userUpdates.put("role", "volunteer");
                                    userUpdates.put("volunteerStatus", "approved");
                                    userUpdates.put("volunteerNgoId", currentNgoId);
                                    db.collection("users").document(applicantUid)
                                            .set(userUpdates, SetOptions.merge());
                                }
                                Toast.makeText(this, "Volunteer approved", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(error ->
                                    Toast.makeText(this, "Could not approve volunteer", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRejectDialog(com.google.firebase.firestore.DocumentSnapshot snapshot) {
        String status = safe(snapshot.getString("status")).toLowerCase();
        if (!"pending".equals(status)) {
            Toast.makeText(this, "Only pending requests can be rejected", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText reasonField = new EditText(this);
        reasonField.setHint("Rejection reason");
        reasonField.setMinLines(3);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Reject Volunteer")
                .setView(reasonField)
                .setPositiveButton("Reject", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String reason = reasonField.getText() == null ? "" : reasonField.getText().toString().trim();
            if (reason.isEmpty()) {
                reasonField.setError("Required");
                reasonField.requestFocus();
                return;
            }
            rejectVolunteer(snapshot, reason);
            dialog.dismiss();
        }));

        dialog.show();
    }

    private void rejectVolunteer(com.google.firebase.firestore.DocumentSnapshot snapshot, String reason) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "rejected");
        updates.put("ngoReviewedAt", FieldValue.serverTimestamp());
        updates.put("ngoReviewedBy", currentUid);
        updates.put("rejectionReason", reason);

        db.collection("volunteer_applications")
                .document(snapshot.getId())
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    String applicantUid = safe(snapshot.getString("uid"));
                    if (!applicantUid.isEmpty()) {
                        Map<String, Object> userUpdates = new HashMap<>();
                        userUpdates.put("volunteerStatus", "rejected");
                        db.collection("users").document(applicantUid)
                                .set(userUpdates, SetOptions.merge());
                    }
                    Toast.makeText(this, "Volunteer request rejected", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(error ->
                        Toast.makeText(this, "Could not reject volunteer", Toast.LENGTH_SHORT).show());
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (applicationListener != null) {
            applicationListener.remove();
            applicationListener = null;
        }
    }
}
