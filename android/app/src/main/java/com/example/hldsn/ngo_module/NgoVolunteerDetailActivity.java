package com.example.hldsn.ngo_module;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;
import com.example.hldsn.ngo_module.NgoTaskCreationActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;

public class NgoVolunteerDetailActivity extends AppCompatActivity {

    public static final String EXTRA_VOLUNTEER_ID = "extra_volunteer_id";
    public static final String EXTRA_VOLUNTEER_UID = "extra_volunteer_uid";
    public static final String EXTRA_VOLUNTEER_NAME = "extra_volunteer_name";

    private FirebaseFirestore db;
    private String volunteerDocId;
    private String volunteerUid;
    private String volunteerName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ngo_volunteer_detail);

        db = FirebaseFirestore.getInstance();

        volunteerDocId = getIntent().getStringExtra(EXTRA_VOLUNTEER_ID);
        volunteerUid = getIntent().getStringExtra(EXTRA_VOLUNTEER_UID);
        volunteerName = getIntent().getStringExtra(EXTRA_VOLUNTEER_NAME);

        if (volunteerDocId == null || volunteerDocId.isEmpty()) {
            Toast.makeText(this, "Invalid volunteer", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ImageView backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        MaterialButton assignTaskButton = findViewById(R.id.assignTaskButton);
        if (assignTaskButton != null) {
            assignTaskButton.setText("Assign Task");
            assignTaskButton.setOnClickListener(v -> openTaskCreation());
        }

        loadVolunteerDetails();
    }

    private void loadVolunteerDetails() {
        db.collection("volunteer_applications")
                .document(volunteerDocId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Toast.makeText(this, "Volunteer not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    displayVolunteerInfo(documentSnapshot);
                })
                .addOnFailureListener(error -> {
                    Toast.makeText(this, "Could not load volunteer details", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void displayVolunteerInfo(com.google.firebase.firestore.DocumentSnapshot doc) {
        // Basic Info
        TextView firstNameText = findViewById(R.id.volunteerFirstNameText);
        TextView surnameText = findViewById(R.id.volunteerSurnameText);
        TextView ageText = findViewById(R.id.volunteerAgeText);
        TextView genderText = findViewById(R.id.volunteerGenderText);
        TextView phoneText = findViewById(R.id.volunteerPhoneText);
        TextView emailText = findViewById(R.id.volunteerEmailText);
        TextView addressText = findViewById(R.id.volunteerAddressText);
        TextView emergencyContactText = findViewById(R.id.volunteerEmergencyContactText);

        // Skills Info
        TextView skillsText = findViewById(R.id.volunteerSkillsText);
        TextView resourcesText = findViewById(R.id.volunteerResourcesText);
        TextView languagesText = findViewById(R.id.volunteerLanguagesText);
        TextView experienceLevelText = findViewById(R.id.volunteerExperienceLevelText);

        if (firstNameText != null) firstNameText.setText("Name: " + safe(doc.getString("firstName")));
        if (surnameText != null) surnameText.setText("Surname: " + safe(doc.getString("surname")));
        if (ageText != null) ageText.setText("Age: " + safe(doc.getString("age")));
        if (genderText != null) genderText.setText("Gender: " + safe(doc.getString("gender")));
        if (phoneText != null) phoneText.setText("Phone: " + safe(doc.getString("phoneNumber")));
        if (emailText != null) emailText.setText("Email: " + safe(doc.getString("email")));
        if (addressText != null) addressText.setText("Address: " + safe(doc.getString("fullAddress")));
        if (emergencyContactText != null) emergencyContactText.setText("Emergency Contact: " + safe(doc.getString("emergencyContact")));

        // Skills
        java.util.List<?> skillsList = (java.util.List<?>) doc.get("skills");
        if (skillsText != null) {
            skillsText.setText("Skills: " + (skillsList != null ? skillsList.toString() : "None"));
        }

        java.util.List<?> resourcesList = (java.util.List<?>) doc.get("resources");
        if (resourcesText != null) {
            resourcesText.setText("Resources: " + (resourcesList != null ? resourcesList.toString() : "None"));
        }

        java.util.List<?> languagesList = (java.util.List<?>) doc.get("languages");
        if (languagesText != null) {
            languagesText.setText("Languages: " + (languagesList != null ? languagesList.toString() : "None"));
        }

        if (experienceLevelText != null) {
            experienceLevelText.setText("Experience Level: " + safe(doc.getString("experienceLevel")));
        }
    }

    private void openTaskCreation() {
        if (volunteerUid == null || volunteerUid.isEmpty()) {
            Toast.makeText(this, "Cannot open task creation: volunteer UID missing", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, NgoTaskCreationActivity.class);
        intent.putExtra(NgoTaskCreationActivity.EXTRA_VOLUNTEER_UID, volunteerUid);
        intent.putExtra(NgoTaskCreationActivity.EXTRA_VOLUNTEER_NAME, safe(volunteerName));
        startActivity(intent);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
