package com.example.hldsn.admin_module;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.example.hldsn.admin_module.adapter.AdminNgoAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

public class AdminNgoManagementActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private AdminNgoAdapter adapter;
    private ListenerRegistration ngoListener;
    private EditText searchField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_manage_ngos);

        db = FirebaseFirestore.getInstance();

        ImageView backButton = findViewById(R.id.backButton);
        searchField = findViewById(R.id.ngoSearchField);
        RecyclerView recyclerView = findViewById(R.id.ngoRecyclerView);
        MaterialButton addNgoButton = findViewById(R.id.addNgoButton);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        adapter = new AdminNgoAdapter(this::confirmDeleteNgo);
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
                    adapter.filter(s == null ? "" : s.toString());
                }
            });
        }

        if (addNgoButton != null) {
            addNgoButton.setOnClickListener(v -> showAddNgoDialog());
        }

        listenForNgos();
    }

    private void listenForNgos() {
        if (ngoListener != null) {
            ngoListener.remove();
        }

        ngoListener = db.collection("ngos")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Could not load NGOs", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snapshot == null) {
                        return;
                    }

                    adapter.submitList(snapshot.getDocuments());
                    if (searchField != null) {
                        adapter.filter(searchField.getText() == null ? "" : searchField.getText().toString());
                    }
                });
    }

    private void showAddNgoDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_ngo, null, false);

        EditText ngoNameField = view.findViewById(R.id.dialogNgoNameField);
        EditText registrationField = view.findViewById(R.id.dialogNgoRegistrationField);
        EditText cityField = view.findViewById(R.id.dialogNgoCityField);
        EditText contactField = view.findViewById(R.id.dialogNgoContactField);
        EditText descriptionField = view.findViewById(R.id.dialogNgoDescriptionField);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add NGO")
                .setView(view)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(v -> {
                String name = textOf(ngoNameField);
                String city = textOf(cityField);

                if (name.isEmpty()) {
                    ngoNameField.setError("Required");
                    ngoNameField.requestFocus();
                    return;
                }

                if (city.isEmpty()) {
                    cityField.setError("Required");
                    cityField.requestFocus();
                    return;
                }

                Map<String, Object> ngo = new HashMap<>();
                ngo.put("name", name);
                ngo.put("registrationNumber", textOf(registrationField));
                ngo.put("city", city);
                ngo.put("contactNumber", textOf(contactField));
                ngo.put("description", textOf(descriptionField));
                ngo.put("status", "active");
                ngo.put("createdAt", FieldValue.serverTimestamp());

                db.collection("ngos")
                        .add(ngo)
                        .addOnSuccessListener(documentReference -> {
                            Toast.makeText(this, "NGO added", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        })
                        .addOnFailureListener(error ->
                                Toast.makeText(this, "Could not add NGO", Toast.LENGTH_SHORT).show());
            });
        });

        dialog.show();
    }

    private void confirmDeleteNgo(com.google.firebase.firestore.DocumentSnapshot snapshot) {
        new AlertDialog.Builder(this)
                .setTitle("Delete NGO")
                .setMessage("This will permanently remove the NGO record. Continue?")
                .setPositiveButton("Delete", (dialog, which) ->
                        db.collection("ngos")
                                .document(snapshot.getId())
                                .delete()
                                .addOnSuccessListener(unused ->
                                        Toast.makeText(this, "NGO removed", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(error ->
                                        Toast.makeText(this, "Could not remove NGO", Toast.LENGTH_SHORT).show()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String textOf(EditText field) {
        return field == null || field.getText() == null ? "" : field.getText().toString().trim();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (ngoListener != null) {
            ngoListener.remove();
            ngoListener = null;
        }
    }
}
