package com.example.hldsn.ngo_module;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NgoCampCenterFormActivity extends AppCompatActivity {

    public static final String EXTRA_DOCUMENT_ID = "extra_document_id";
    public static final String EXTRA_LOCATION_TYPE = "extra_location_type";
    public static final String EXTRA_LOCATION_NAME = "extra_location_name";
    public static final String EXTRA_LOCATION_TEXT = "extra_location_text";
    public static final String EXTRA_SELECTED_LATITUDE = "extra_selected_latitude";
    public static final String EXTRA_SELECTED_LONGITUDE = "extra_selected_longitude";
    public static final String EXTRA_SELECTED_LABEL = "extra_selected_label";

    private static final String COLLECTION_NAME = "camp_center_locations";
    private static final double PAKISTAN_NORTH = 37.2;
    private static final double PAKISTAN_SOUTH = 23.5;
    private static final double PAKISTAN_EAST = 77.9;
    private static final double PAKISTAN_WEST = 60.8;

    private final String[] locationTypes = new String[] {"Camp", "Center"};

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private EditText nameField;
    private Spinner typeSpinner;
    private MaterialButton saveButton;
    private MaterialButton pickOnMapButton;
    private String documentId = "";
    private boolean editingExisting = false;
    private String currentNgoId = "";
    private Double selectedLatitude = null;
    private Double selectedLongitude = null;
    private String selectedLabel = "";

    private final ActivityResultLauncher<Intent> pickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }

                Intent data = result.getData();
                if (data.hasExtra(EXTRA_SELECTED_LATITUDE) && data.hasExtra(EXTRA_SELECTED_LONGITUDE)) {
                    selectedLatitude = data.getDoubleExtra(EXTRA_SELECTED_LATITUDE, 0d);
                    selectedLongitude = data.getDoubleExtra(EXTRA_SELECTED_LONGITUDE, 0d);
                    selectedLabel = safe(data.getStringExtra(EXTRA_SELECTED_LABEL));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ngo_camp_center_form);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        ImageView backButton = findViewById(R.id.backButton);
        nameField = findViewById(R.id.campCenterNameField);
        typeSpinner = findViewById(R.id.campCenterTypeSpinner);
        pickOnMapButton = findViewById(R.id.campCenterPickMapButton);
        saveButton = findViewById(R.id.campCenterSaveButton);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        typeSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                locationTypes
        ));

        applyIntentExtras();
        loadCurrentNgoId();

        if (pickOnMapButton != null) {
            pickOnMapButton.setOnClickListener(v -> openPicker());
        }

        if (saveButton != null) {
            saveButton.setOnClickListener(v -> saveLocation());
        }
    }

    private void applyIntentExtras() {
        String defaultType = safe(getIntent().getStringExtra(EXTRA_LOCATION_TYPE));
        String defaultName = safe(getIntent().getStringExtra(EXTRA_LOCATION_NAME));
        documentId = safe(getIntent().getStringExtra(EXTRA_DOCUMENT_ID));

        editingExisting = !documentId.isEmpty();
        if (editingExisting && saveButton != null) {
            saveButton.setText("Update Location");
        }

        if (!defaultName.isEmpty()) {
            nameField.setText(defaultName);
        }

        if (!defaultType.isEmpty()) {
            typeSpinner.setSelection("center".equalsIgnoreCase(defaultType) ? 1 : 0);
        }

        double defaultLatitude = getIntent().getDoubleExtra(EXTRA_SELECTED_LATITUDE, Double.NaN);
        double defaultLongitude = getIntent().getDoubleExtra(EXTRA_SELECTED_LONGITUDE, Double.NaN);
        if (!Double.isNaN(defaultLatitude) && !Double.isNaN(defaultLongitude)) {
            selectedLatitude = defaultLatitude;
            selectedLongitude = defaultLongitude;
            selectedLabel = safe(getIntent().getStringExtra(EXTRA_SELECTED_LABEL));
        }
    }

    private void saveLocation() {
        FirebaseUser currentUser = auth == null ? null : auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please sign in again to save locations", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = safeText(nameField);
        String type = locationTypes[typeSpinner.getSelectedItemPosition()].toLowerCase(Locale.US);

        if (name.isEmpty()) {
            nameField.setError("Required");
            nameField.requestFocus();
            return;
        }

        if (selectedLatitude == null || selectedLongitude == null) {
            Toast.makeText(this, "Please pick a location on the map", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isInsidePakistan(selectedLatitude, selectedLongitude)) {
            Toast.makeText(this, "Please choose a location within Pakistan", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!currentNgoId.isEmpty()) {
            persistLocation(name, type, currentNgoId, selectedLabel, selectedLatitude, selectedLongitude, selectedLabel, true);
            return;
        }

        db.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String ngoId = safe(documentSnapshot.getString("ngoId"));
                    if (ngoId.isEmpty()) {
                        Toast.makeText(this, "Your NGO profile is missing an NGO ID", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    currentNgoId = ngoId;
                    persistLocation(name, type, ngoId, selectedLabel, selectedLatitude, selectedLongitude, selectedLabel, true);
                })
                .addOnFailureListener(error -> Toast.makeText(this, "Could not load NGO profile", Toast.LENGTH_SHORT).show());
    }

    private void persistLocation(String name, String type, String ngoId, String locationText, double latitude, double longitude, String resolvedLabel, boolean cameFromPicker) {
        if (!isInsidePakistan(latitude, longitude)) {
            Toast.makeText(this, "Please enter a location within Pakistan", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("type", type);
        data.put("ngoId", ngoId);
        data.put("requesterUid", auth.getCurrentUser().getUid());
        data.put("createdByUid", auth.getCurrentUser().getUid());
        data.put("ownerUid", auth.getCurrentUser().getUid());
        data.put("locationText", locationText);
        data.put("locationLabel", resolvedLabel);
        data.put("latitude", latitude);
        data.put("longitude", longitude);
        data.put("isActive", true);
        data.put("updatedAt", FieldValue.serverTimestamp());
        if (!editingExisting) {
            data.put("createdAt", FieldValue.serverTimestamp());
        }

        if (editingExisting) {
            db.collection(COLLECTION_NAME)
                    .document(documentId)
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener(unused -> finishWithToast(cameFromPicker ? "Location updated from map" : "Location updated"))
                    .addOnFailureListener(error -> Toast.makeText(this, "Could not update location", Toast.LENGTH_SHORT).show());
        } else {
            db.collection(COLLECTION_NAME)
                    .add(data)
                    .addOnSuccessListener(documentReference -> finishWithToast(cameFromPicker ? "Location saved from map" : "Location saved"))
                    .addOnFailureListener(error -> Toast.makeText(this, "Could not save location", Toast.LENGTH_SHORT).show());
        }
    }

    private void openPicker() {
        Intent intent = new Intent(this, NgoCampCenterMapPickerActivity.class);
        if (selectedLatitude != null && selectedLongitude != null) {
            intent.putExtra(EXTRA_SELECTED_LATITUDE, selectedLatitude);
            intent.putExtra(EXTRA_SELECTED_LONGITUDE, selectedLongitude);
            intent.putExtra(EXTRA_SELECTED_LABEL, selectedLabel);
        }
        pickerLauncher.launch(intent);
    }

    private void loadCurrentNgoId() {
        FirebaseUser currentUser = auth == null ? null : auth.getCurrentUser();
        if (currentUser == null) {
            return;
        }

        db.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> currentNgoId = safe(documentSnapshot.getString("ngoId")))
                .addOnFailureListener(error -> currentNgoId = "");
    }

    private void finishWithToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }

    private boolean isInsidePakistan(double latitude, double longitude) {
        return latitude <= PAKISTAN_NORTH
                && latitude >= PAKISTAN_SOUTH
                && longitude <= PAKISTAN_EAST
                && longitude >= PAKISTAN_WEST;
    }

    private String safeText(EditText field) {
        return field == null || field.getText() == null ? "" : field.getText().toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String primary, String fallback) {
        String value = safe(primary);
        return value.isEmpty() ? safe(fallback) : value;
    }
}