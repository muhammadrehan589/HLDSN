package com.example.hldsn.volunteer_module;

import android.content.Intent;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;
import com.example.hldsn.notification_module.UserNotificationStore;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class VolunteerAgreementActivity extends AppCompatActivity {

    private CheckBox confirmTermsCheckbox;
    private MaterialButton submitButton;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private boolean isSubmitting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_volunteer_agreement);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        ImageView backButton = findViewById(R.id.backButton);
        confirmTermsCheckbox = findViewById(R.id.confirmTermsCheckbox);
        submitButton = findViewById(R.id.submitButton);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        updateSubmitState();

        if (confirmTermsCheckbox != null) {
            confirmTermsCheckbox.setOnCheckedChangeListener((buttonView, isChecked) ->
                    updateSubmitState());
        }

        if (submitButton != null) {
            submitButton.setOnClickListener(v -> submitVolunteerApplication());
        }
    }

    private void submitVolunteerApplication() {
        boolean agreed = confirmTermsCheckbox != null && confirmTermsCheckbox.isChecked();
        if (!agreed) {
            Toast.makeText(this, "Please accept terms first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isSubmitting) {
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please login to submit", Toast.LENGTH_SHORT).show();
            return;
        }

        String selectedNgoId = getTrimmedExtra(VolunteerFormExtras.NGO_ID);
        if (selectedNgoId.isEmpty()) {
            Toast.makeText(this, "Please select an NGO before submitting", Toast.LENGTH_SHORT).show();
            return;
        }

        isSubmitting = true;
        if (submitButton != null) {
            submitButton.setText("Submitting...");
        }
        updateSubmitState();

        Map<String, Object> payload = buildApplicationPayload(user.getUid());
        db.collection("volunteer_applications")
                .add(payload)
                .addOnSuccessListener(documentReference -> {
                    notifyNgoAdmins(user.getUid(), documentReference.getId());
                    Toast.makeText(this, "Application submitted", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, VolunteerSuccessActivity.class));
                    finish();
                })
                .addOnFailureListener(error -> {
                    isSubmitting = false;
                    if (submitButton != null) {
                        submitButton.setText("Submit");
                    }
                    updateSubmitState();
                    Toast.makeText(this, "Submission failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private Map<String, Object> buildApplicationPayload(String uid) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("uid", uid);

        payload.put("firstName", getTrimmedExtra(VolunteerFormExtras.FIRST_NAME));
        payload.put("surname", getTrimmedExtra(VolunteerFormExtras.SURNAME));
        payload.put("fullAddress", getTrimmedExtra(VolunteerFormExtras.FULL_ADDRESS));
        payload.put("age", getTrimmedExtra(VolunteerFormExtras.AGE));
        payload.put("phoneNumber", getTrimmedExtra(VolunteerFormExtras.PHONE_NUMBER));
        payload.put("emergencyContact", getTrimmedExtra(VolunteerFormExtras.EMERGENCY_CONTACT));
        payload.put("gender", getTrimmedExtra(VolunteerFormExtras.GENDER));
        payload.put("email", getTrimmedExtra(VolunteerFormExtras.EMAIL));
        payload.put("ngoId", getTrimmedExtra(VolunteerFormExtras.NGO_ID));
        payload.put("ngoName", getTrimmedExtra(VolunteerFormExtras.NGO_NAME));

        payload.put("skills", getStringListExtra(VolunteerFormExtras.SKILLS));
        payload.put("resources", getStringListExtra(VolunteerFormExtras.RESOURCES));
        payload.put("languages", getStringListExtra(VolunteerFormExtras.LANGUAGES));
        payload.put("experienceLevel", getTrimmedExtra(VolunteerFormExtras.EXPERIENCE_LEVEL));

        payload.put("termsAccepted", true);
        payload.put("status", "pending");
        payload.put("submittedAt", FieldValue.serverTimestamp());

        return payload;
    }

    private void notifyNgoAdmins(String volunteerUid, String applicationId) {
        final String ngoId = getTrimmedExtra(VolunteerFormExtras.NGO_ID);
        final String ngoName = getTrimmedExtra(VolunteerFormExtras.NGO_NAME);
        final String volunteerName = buildVolunteerName();

        db.collection("users")
                .whereEqualTo("ngoId", ngoId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
                        String role = safe(documentSnapshot.getString("role")).toLowerCase();
                        if (!"ngo_admin".equals(role)) {
                            continue;
                        }

                        UserNotificationStore.createVolunteerApplicationNotification(
                                db,
                                documentSnapshot.getId(),
                                ngoId,
                                ngoName,
                                volunteerUid,
                                volunteerName,
                                applicationId
                        );
                    }
                });
    }

    private ArrayList<String> getStringListExtra(String key) {
        ArrayList<String> values = getIntent().getStringArrayListExtra(key);
        return values != null ? values : new ArrayList<>();
    }

    private String getTrimmedExtra(String key) {
        String value = getIntent().getStringExtra(key);
        return value == null ? "" : value.trim();
    }

    private void updateSubmitState() {
        if (submitButton == null) {
            return;
        }

        boolean canSubmit = confirmTermsCheckbox != null && confirmTermsCheckbox.isChecked() && !isSubmitting;
        submitButton.setEnabled(canSubmit);
        submitButton.setAlpha(canSubmit ? 1f : 0.6f);
    }

    private String buildVolunteerName() {
        String firstName = getTrimmedExtra(VolunteerFormExtras.FIRST_NAME);
        String surname = getTrimmedExtra(VolunteerFormExtras.SURNAME);
        String fullName = (firstName + " " + surname).trim();
        return fullName.isEmpty() ? "A volunteer" : fullName;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
