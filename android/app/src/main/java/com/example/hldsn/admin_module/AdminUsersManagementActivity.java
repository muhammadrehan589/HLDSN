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
import com.example.hldsn.admin_module.adapter.AdminUserAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class AdminUsersManagementActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private AdminUserAdapter adapter;
    private ListenerRegistration userListener;
    private EditText searchField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_manage_users);

        db = FirebaseFirestore.getInstance();

        ImageView backButton = findViewById(R.id.backButton);
        searchField = findViewById(R.id.userSearchField);
        Spinner roleSpinner = findViewById(R.id.userRoleFilterSpinner);
        RecyclerView recyclerView = findViewById(R.id.userRecyclerView);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        adapter = new AdminUserAdapter(this::confirmDeleteUser);
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

        if (roleSpinner != null) {
            String[] roleOptions = new String[] {"All", "User", "Volunteer", "NGO Admin", "Admin"};
            roleSpinner.setAdapter(new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_dropdown_item,
                    roleOptions
            ));
            roleSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                    String selected = roleOptions[position];
                    if ("All".equalsIgnoreCase(selected)) {
                        adapter.setRoleFilter("all");
                    } else if ("NGO Admin".equalsIgnoreCase(selected)) {
                        adapter.setRoleFilter("ngo_admin");
                    } else {
                        adapter.setRoleFilter(selected.toLowerCase());
                    }
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    adapter.setRoleFilter("all");
                }
            });
        }

        listenForUsers();
    }

    private void listenForUsers() {
        if (userListener != null) {
            userListener.remove();
        }

        userListener = db.collection("users")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Could not load users", Toast.LENGTH_SHORT).show();
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

    private void confirmDeleteUser(com.google.firebase.firestore.DocumentSnapshot snapshot) {
        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";

        if (snapshot.getId().equals(currentUid)) {
            Toast.makeText(this, "You cannot delete your own account", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete User")
                .setMessage("This removes the user profile from Firestore. Auth account deletion needs a backend function.")
                .setPositiveButton("Delete", (dialog, which) ->
                        db.collection("users")
                                .document(snapshot.getId())
                                .delete()
                                .addOnSuccessListener(unused ->
                                        Toast.makeText(this, "User removed from Firestore", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(error ->
                                        Toast.makeText(this, "Could not remove user", Toast.LENGTH_SHORT).show()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
    }
}
