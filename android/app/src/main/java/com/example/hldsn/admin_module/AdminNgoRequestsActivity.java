package com.example.hldsn.admin_module;

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
import com.example.hldsn.admin_module.adapter.AdminNgoRequestAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

public class AdminNgoRequestsActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private AdminNgoRequestAdapter adapter;
    private ListenerRegistration requestListener;
    private EditText searchField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_manage_ngo_requests);

        db = FirebaseFirestore.getInstance();

        ImageView backButton = findViewById(R.id.backButton);
        searchField = findViewById(R.id.ngoRequestSearchField);
        Spinner statusSpinner = findViewById(R.id.ngoRequestStatusFilterSpinner);
        RecyclerView recyclerView = findViewById(R.id.ngoRequestRecyclerView);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        adapter = new AdminNgoRequestAdapter(new AdminNgoRequestAdapter.RequestActionListener() {
            @Override
            public void onApprove(DocumentSnapshot snapshot) {
                confirmApproveRequest(snapshot);
            }

            @Override
            public void onReject(DocumentSnapshot snapshot) {
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
                    String status = statuses[position];
                    if ("All".equalsIgnoreCase(status)) {
                        adapter.setStatusFilter("all");
                    } else {
                        adapter.setStatusFilter(status.toLowerCase());
                    }
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    adapter.setStatusFilter("all");
                }
            });
        }

        listenForNgoRequests();
    }

    private void listenForNgoRequests() {
        if (requestListener != null) {
            requestListener.remove();
        }

        requestListener = db.collection("ngo_requests")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Could not load NGO requests", Toast.LENGTH_SHORT).show();
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

    private void confirmApproveRequest(DocumentSnapshot requestSnapshot) {
        String status = safe(requestSnapshot.getString("status")).toLowerCase();
        if (!"pending".equals(status)) {
            Toast.makeText(this, "Only pending requests can be approved", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Approve NGO Request")
                .setMessage("This will register the NGO and assign requester as NGO Admin. Continue?")
                .setPositiveButton("Approve", (dialog, which) -> approveNgoRequest(requestSnapshot))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void approveNgoRequest(DocumentSnapshot requestSnapshot) {
        String reviewerUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "system";

        String ngoName = firstNonBlank(requestSnapshot.getString("ngoName"), "Unnamed NGO");
        String requesterUid = safe(requestSnapshot.getString("requesterUid"));

        DocumentReference ngoRef = db.collection("ngos").document();
        DocumentReference requestRef = db.collection("ngo_requests").document(requestSnapshot.getId());

        WriteBatch batch = db.batch();

        Map<String, Object> ngoData = new HashMap<>();
        ngoData.put("name", ngoName);
        ngoData.put("registrationNumber", safe(requestSnapshot.getString("registrationNumber")));
        ngoData.put("city", safe(requestSnapshot.getString("city")));
        ngoData.put("contactNumber", safe(requestSnapshot.getString("contactNumber")));
        ngoData.put("description", safe(requestSnapshot.getString("description")));
        ngoData.put("status", "active");
        ngoData.put("createdAt", FieldValue.serverTimestamp());
        ngoData.put("createdByUid", reviewerUid);
        ngoData.put("requestId", requestSnapshot.getId());
        batch.set(ngoRef, ngoData, SetOptions.merge());

        Map<String, Object> requestUpdates = new HashMap<>();
        requestUpdates.put("status", "approved");
        requestUpdates.put("reviewedAt", FieldValue.serverTimestamp());
        requestUpdates.put("reviewedBy", reviewerUid);
        requestUpdates.put("ngoId", ngoRef.getId());
        requestUpdates.put("rejectionReason", FieldValue.delete());
        batch.set(requestRef, requestUpdates, SetOptions.merge());

        if (!requesterUid.isEmpty()) {
            Map<String, Object> userUpdates = new HashMap<>();
            userUpdates.put("role", "ngo_admin");
            userUpdates.put("ngoId", ngoRef.getId());
            userUpdates.put("ngoName", ngoName);
            userUpdates.put("ngoApprovedAt", FieldValue.serverTimestamp());
            batch.set(db.collection("users").document(requesterUid), userUpdates, SetOptions.merge());
        }

        batch.commit()
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "NGO request approved", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(error ->
                        Toast.makeText(this, "Could not approve NGO request", Toast.LENGTH_SHORT).show());
    }

    private void showRejectDialog(DocumentSnapshot requestSnapshot) {
        String status = safe(requestSnapshot.getString("status")).toLowerCase();
        if (!"pending".equals(status)) {
            Toast.makeText(this, "Only pending requests can be rejected", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText reasonField = new EditText(this);
        reasonField.setHint("Rejection reason");
        reasonField.setMinLines(3);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Reject NGO Request")
                .setMessage("Please provide reason")
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
            rejectNgoRequest(requestSnapshot, reason);
            dialog.dismiss();
        }));

        dialog.show();
    }

    private void rejectNgoRequest(DocumentSnapshot requestSnapshot, String reason) {
        String reviewerUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "system";

        Map<String, Object> requestUpdates = new HashMap<>();
        requestUpdates.put("status", "rejected");
        requestUpdates.put("reviewedAt", FieldValue.serverTimestamp());
        requestUpdates.put("reviewedBy", reviewerUid);
        requestUpdates.put("rejectionReason", reason);

        db.collection("ngo_requests")
                .document(requestSnapshot.getId())
                .set(requestUpdates, SetOptions.merge())
                .addOnSuccessListener(unused -> Toast.makeText(this, "NGO request rejected", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(error -> Toast.makeText(this, "Could not reject NGO request", Toast.LENGTH_SHORT).show());
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String value, String fallback) {
        return safe(value).isEmpty() ? fallback : safe(value);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (requestListener != null) {
            requestListener.remove();
            requestListener = null;
        }
    }
}
