package com.example.hldsn.ngo_module;

import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.HashMap;
import java.util.Map;

public class NgoRegistrationRequestActivity extends AppCompatActivity {

    private static final String TAG = "NgoRequestForm";

    private EditText ngoNameField;
    private EditText registrationNumberField;
    private EditText cityField;
    private EditText contactField;
    private EditText descriptionField;
    private MaterialButton submitButton;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ngo_registration_request);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        ImageView backButton = findViewById(R.id.backButton);
        ngoNameField = findViewById(R.id.ngoNameField);
        registrationNumberField = findViewById(R.id.ngoRegistrationNumberField);
        cityField = findViewById(R.id.ngoCityField);
        contactField = findViewById(R.id.ngoContactField);
        descriptionField = findViewById(R.id.ngoDescriptionField);
        submitButton = findViewById(R.id.submitNgoRequestButton);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        if (submitButton != null) {
            submitButton.setOnClickListener(v -> submitRequest());
        }
    }

    private void submitRequest() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            return;
        }

        String ngoName = textOf(ngoNameField);
        String city = textOf(cityField);
        String contact = textOf(contactField);

        if (ngoName.isEmpty()) {
            ngoNameField.setError("Required");
            ngoNameField.requestFocus();
            return;
        }

        if (city.isEmpty()) {
            cityField.setError("Required");
            cityField.requestFocus();
            return;
        }

        if (contact.isEmpty()) {
            contactField.setError("Required");
            contactField.requestFocus();
            return;
        }

        submitButton.setEnabled(false);
        submitButton.setText("Submitting...");

        Map<String, Object> payload = new HashMap<>();
        payload.put("requesterUid", user.getUid());
        payload.put("requesterEmail", user.getEmail() == null ? "" : user.getEmail().trim());
        payload.put("ngoName", ngoName);
        payload.put("registrationNumber", textOf(registrationNumberField));
        payload.put("city", city);
        payload.put("contactNumber", contact);
        payload.put("description", textOf(descriptionField));
        payload.put("status", "pending");
        payload.put("submittedAt", FieldValue.serverTimestamp());

        db.collection("ngo_requests")
                .add(payload)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "NGO request submitted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(error -> {
                    submitButton.setEnabled(true);
                    submitButton.setText("Submit Request");

                    String debugMessage = buildFailureMessage(error);
                    Log.e(TAG, "Failed to submit NGO request", error);
                    Toast.makeText(this, debugMessage, Toast.LENGTH_LONG).show();
                });
    }

    private String buildFailureMessage(Exception error) {
        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException ffError = (FirebaseFirestoreException) error;
            if (ffError.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return "Submit blocked by Firestore rules (PERMISSION_DENIED).";
            }
            return "Submit failed: " + ffError.getCode().name();
        }

        String message = error != null && error.getMessage() != null
                ? error.getMessage().trim()
                : "unknown error";
        if (message.length() > 80) {
            message = message.substring(0, 80) + "...";
        }
        return "Could not submit NGO request: " + message;
    }

    private String textOf(EditText field) {
        return field == null || field.getText() == null ? "" : field.getText().toString().trim();
    }
}
