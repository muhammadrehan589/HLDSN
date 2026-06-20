package com.example.hldsn.ngo_module;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class NgoResourceCreationActivity extends AppCompatActivity {

    private static final String[] INVENTORY_CATEGORIES = {
            "Food", "Water", "Tents", "Medical Supplies", "Clothing", "Blankets", "Others"
    };

    private Spinner spinnerCategory;
    private TextInputEditText etName, etDescription, etQuantity, etLocation;
    private MaterialButton btnSubmit;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String editingResourceId = null;
    private String selectedCategory = INVENTORY_CATEGORIES[0];

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ngo_resource_form);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        spinnerCategory = findViewById(R.id.spinnerResourceCategory);
        etName = findViewById(R.id.etResourceName);
        etDescription = findViewById(R.id.etResourceDescription);
        etQuantity = findViewById(R.id.etResourceQuantity);
        etLocation = findViewById(R.id.etResourceLocation);
        btnSubmit = findViewById(R.id.btnSubmitResource);

        // Set up category dropdown
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, INVENTORY_CATEGORIES);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);

        spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedCategory = INVENTORY_CATEGORIES[position];
                if ("Others".equals(selectedCategory)) {
                    etName.setVisibility(View.VISIBLE);
                } else {
                    etName.setVisibility(View.GONE);
                    etName.setText("");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedCategory = INVENTORY_CATEGORIES[0];
            }
        });

        if (getIntent() != null && getIntent().hasExtra("resourceId")) {
            editingResourceId = getIntent().getStringExtra("resourceId");
            loadResourceForEdit(editingResourceId);
        }

        btnSubmit.setOnClickListener(v -> submitResource());
    }

    private void loadResourceForEdit(String resourceId) {
        db.collection(NgoResourceStore.COLLECTION_NAME)
                .document(resourceId)
                .get()
                .addOnSuccessListener((DocumentSnapshot doc) -> {
                    if (doc == null || !doc.exists()) return;
                    String name = doc.getString("name");
                    // Try to match the name to a category
                    boolean foundCategory = false;
                    if (name != null) {
                        for (int i = 0; i < INVENTORY_CATEGORIES.length; i++) {
                            if (INVENTORY_CATEGORIES[i].equalsIgnoreCase(name)) {
                                spinnerCategory.setSelection(i);
                                foundCategory = true;
                                break;
                            }
                        }
                    }
                    if (!foundCategory) {
                        // Select "Others" and fill in the custom name
                        spinnerCategory.setSelection(INVENTORY_CATEGORIES.length - 1);
                        etName.setVisibility(View.VISIBLE);
                        etName.setText(name);
                    }
                    etDescription.setText(doc.getString("description"));
                    Object q = doc.get("quantity");
                    if (q != null) etQuantity.setText(String.valueOf(q));
                    etLocation.setText(doc.getString("location"));
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Unable to load inventory item", Toast.LENGTH_SHORT).show());
    }

    private void submitResource() {
        String name;
        if ("Others".equals(selectedCategory)) {
            name = etName.getText() == null ? "" : etName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a custom item name", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            name = selectedCategory;
        }

        String description = etDescription.getText() == null ? "" : etDescription.getText().toString().trim();
        String qtyText = etQuantity.getText() == null ? "" : etQuantity.getText().toString().trim();
        String location = etLocation.getText() == null ? "" : etLocation.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, "Please select an inventory type", Toast.LENGTH_SHORT).show();
            return;
        }

        int qty = 0;
        try { qty = Integer.parseInt(qtyText); } catch (Exception ignored) {}

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        // make final copies for lambda capture
        final String nameFinal = name;
        final String descriptionFinal = description;
        final int qtyFinal = qty;
        final String locationFinal = location;
        final FirebaseUser userFinal = user;

        // get ngoId from user profile
        db.collection("users").document(userFinal.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String ngoId = doc.getString("ngoId");
                    if (ngoId == null || ngoId.trim().isEmpty()) {
                        Toast.makeText(this, "NGO not configured for this account", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (editingResourceId != null) {
                        Map<String,Object> updates = new HashMap<>();
                        updates.put("name", nameFinal);
                        updates.put("description", descriptionFinal);
                        updates.put("quantity", qtyFinal);
                        updates.put("location", locationFinal);
                        updates.put("ngoId", ngoId);
                        updates.put("createdByUid", userFinal.getUid());

                        NgoResourceStore.updateResource(db, editingResourceId, updates,
                                (aVoid) -> { Toast.makeText(this, "Inventory updated", Toast.LENGTH_SHORT).show(); finish(); },
                                e -> Toast.makeText(this, "Failed to update inventory", Toast.LENGTH_SHORT).show());
                    } else {
                        NgoResource resource = new NgoResource(ngoId, nameFinal, descriptionFinal, qtyFinal, locationFinal, userFinal.getUid());
                        NgoResourceStore.createResource(db, resource,
                                (docRef) -> { Toast.makeText(this, "Inventory item created", Toast.LENGTH_SHORT).show(); finish(); },
                                e -> Toast.makeText(this, "Failed to create inventory item: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to read user profile", Toast.LENGTH_SHORT).show());
    }
}
