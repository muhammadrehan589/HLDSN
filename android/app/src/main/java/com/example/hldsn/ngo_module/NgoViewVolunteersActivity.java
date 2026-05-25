package com.example.hldsn.ngo_module;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.example.hldsn.ngo_module.adapter.NgoViewVolunteersAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class NgoViewVolunteersActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private NgoViewVolunteersAdapter adapter;
    private ListenerRegistration volunteersListener;
    private EditText searchField;
    private TextView emptyStateText;
    private String currentNgoId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ngo_view_volunteers);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        ImageView backButton = findViewById(R.id.backButton);
        searchField = findViewById(R.id.volunteerSearchField);
        RecyclerView recyclerView = findViewById(R.id.volunteersRecyclerView);
        emptyStateText = findViewById(R.id.volunteersEmptyText);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        adapter = new NgoViewVolunteersAdapter(documentSnapshot -> {
            Intent intent = new Intent(NgoViewVolunteersActivity.this, NgoTaskCreationActivity.class);
            intent.putExtra(NgoTaskCreationActivity.EXTRA_VOLUNTEER_UID, documentSnapshot.getString("uid"));
            intent.putExtra(NgoTaskCreationActivity.EXTRA_VOLUNTEER_NAME,
                safe(documentSnapshot.getString("firstName")) + " " + safe(documentSnapshot.getString("surname")));
            startActivity(intent);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        if (searchField != null) {
            searchField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    adapter.setSearchQuery(s == null ? "" : s.toString());
                }
            });
        }

        loadCurrentNgoAndVolunteers();
    }

    private void loadCurrentNgoAndVolunteers() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please sign in again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    currentNgoId = safe(documentSnapshot.getString("ngoId"));
                    if (currentNgoId.isEmpty()) {
                        Toast.makeText(this, "No NGO assigned to this account", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    listenForApprovedVolunteers();
                })
                .addOnFailureListener(error -> {
                    Toast.makeText(this, "Could not load NGO profile", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void listenForApprovedVolunteers() {
        if (volunteersListener != null) {
            volunteersListener.remove();
        }

        volunteersListener = db.collection("volunteer_applications")
                .whereEqualTo("ngoId", currentNgoId)
                .whereEqualTo("status", "approved")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Could not load volunteers", Toast.LENGTH_SHORT).show();
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

    @Override
    protected void onStop() {
        super.onStop();
        if (volunteersListener != null) {
            volunteersListener.remove();
            volunteersListener = null;
        }
    }
}
