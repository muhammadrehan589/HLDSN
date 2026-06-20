package com.example.hldsn.ngo_module;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class NgoResourceListActivity extends AppCompatActivity {

    private static final String[] FILTER_CATEGORIES = {
            "All", "Food", "Water", "Tents", "Medical Supplies", "Clothing", "Blankets", "Others"
    };

    private RecyclerView rvResources;
    private NgoResourceAdapter adapter;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration listenerRegistration;

    private EditText etSearch;
    private Spinner spinnerFilter;
    private TextView tvFilterIndicator;
    private String currentSearchQuery = "";
    private String currentFilterCategory = "All";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ngo_resource_list);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        etSearch = findViewById(R.id.etSearchInventory);
        spinnerFilter = findViewById(R.id.spinnerFilterCategory);
        tvFilterIndicator = findViewById(R.id.tvFilterIndicator);
        rvResources = findViewById(R.id.rvResources);
        rvResources.setLayoutManager(new LinearLayoutManager(this));

        adapter = new NgoResourceAdapter(this::showDonateDialog);
        rvResources.setAdapter(adapter);

        // Set up filter spinner
        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, FILTER_CATEGORIES);
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilter.setAdapter(filterAdapter);

        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentFilterCategory = FILTER_CATEGORIES[position];
                updateFilterIndicator();
                applyFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                currentFilterCategory = "All";
                updateFilterIndicator();
                applyFilters();
            }
        });

        // Set up search
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString();
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadResourcesForMyNgo();
    }

    private void updateFilterIndicator() {
        if ("All".equals(currentFilterCategory)) {
            tvFilterIndicator.setVisibility(View.GONE);
        } else {
            tvFilterIndicator.setText("Filtered by: " + currentFilterCategory);
            tvFilterIndicator.setVisibility(View.VISIBLE);
        }
    }

    private void applyFilters() {
        adapter.filter(currentSearchQuery, currentFilterCategory);
    }

    private void showDonateDialog(NgoResource resource) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);

        TextView tvAvailable = new TextView(this);
        tvAvailable.setText("Available quantity: " + resource.getQuantity());
        tvAvailable.setTextSize(14);
        layout.addView(tvAvailable);

        EditText etQty = new EditText(this);
        etQty.setHint("Donation quantity");
        etQty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(etQty);

        EditText etLocation = new EditText(this);
        etLocation.setHint("Donation location");
        etLocation.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        layout.addView(etLocation);

        new AlertDialog.Builder(this)
                .setTitle("Donate: " + resource.getName())
                .setView(layout)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    String qtyStr = etQty.getText().toString().trim();
                    String location = etLocation.getText().toString().trim();

                    if (qtyStr.isEmpty()) {
                        Toast.makeText(this, "Please enter a quantity", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int qty;
                    try {
                        qty = Integer.parseInt(qtyStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid quantity", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (qty <= 0) {
                        Toast.makeText(this, "Quantity must be greater than 0", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (qty > resource.getQuantity()) {
                        Toast.makeText(this, "Cannot donate more than available (" + resource.getQuantity() + ")", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (location.isEmpty()) {
                        Toast.makeText(this, "Please enter a donation location", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    processDonation(resource, qty, location);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void processDonation(NgoResource resource, int qty, String location) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String ngoId = doc.getString("ngoId");
                    if (ngoId == null || ngoId.trim().isEmpty()) {
                        Toast.makeText(this, "NGO not configured", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    NgoDonation donation = new NgoDonation(
                            ngoId,
                            resource.getId(),
                            resource.getName(),
                            resource.getDescription(),
                            qty,
                            location,
                            user.getUid()
                    );

                    // Create donation record
                    NgoDonationStore.createDonation(db, donation,
                            docRef -> {
                                // Deduct from inventory
                                NgoResourceStore.deductQuantity(db, resource.getId(), qty,
                                        aVoid -> Toast.makeText(this, "Donation recorded successfully", Toast.LENGTH_SHORT).show(),
                                        e -> Toast.makeText(this, "Donation created but failed to update inventory: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                                );
                            },
                            e -> Toast.makeText(this, "Failed to create donation: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to read user profile", Toast.LENGTH_SHORT).show());
    }

    private void loadResourcesForMyNgo() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show(); return; }

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String ngoId = doc.getString("ngoId");
                    if (ngoId == null || ngoId.trim().isEmpty()) { Toast.makeText(this, "No NGO found", Toast.LENGTH_SHORT).show(); return; }

                    listenerRegistration = NgoResourceStore.observeResourcesForNgo(db, ngoId, (snapshot, error) -> {
                        if (error != null) return;
                        List<NgoResource> list = new ArrayList<>();
                        if (snapshot != null) {
                            for (com.google.firebase.firestore.DocumentSnapshot ds : snapshot.getDocuments()) {
                                NgoResource r = ds.toObject(NgoResource.class);
                                if (r != null) {
                                    r.setId(ds.getId());
                                    list.add(r);
                                }
                            }
                        }
                        adapter.setItems(list);
                        applyFilters();
                    });
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Unable to read profile", Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (listenerRegistration != null) listenerRegistration.remove();
    }
}
