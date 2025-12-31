package com.example.hldsn;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SaveUserProfileActivity extends AppCompatActivity {

    private EditText nameField, addressField, phoneField;
    private EditText ageField, bloodField, heightField, weightField;
    private EditText allergyField1, allergyField2, allergyField3;
    private EditText contactField1, contactField2, contactField3;
    private EditText injuryField1, injuryField2, injuryField3;
    private Button saveButton;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_save_profile);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();

        saveButton.setOnClickListener(v -> saveProfile());
    }

    private void initViews() {
        nameField = findViewById(R.id.nameField);
        addressField = findViewById(R.id.addressField);
        phoneField = findViewById(R.id.phoneField);
        ageField = findViewById(R.id.ageField);
        bloodField = findViewById(R.id.bloodField);
        heightField = findViewById(R.id.heightField);
        weightField = findViewById(R.id.weightField);

        allergyField1 = findViewById(R.id.allergyField1);
        allergyField2 = findViewById(R.id.allergyField2);
        allergyField3 = findViewById(R.id.allergyField3);

        injuryField1 = findViewById(R.id.injuryField1);
        injuryField2 = findViewById(R.id.injuryField2);
        injuryField3 = findViewById(R.id.injuryField3);

       contactField1=findViewById(R.id.contactField1);
       contactField2=findViewById(R.id.contactField2);
       contactField3=findViewById(R.id.contactField3);


        saveButton = findViewById(R.id.saveButton);
    }

    private void saveProfile() {
        String uid = auth.getCurrentUser().getUid();
        if (uid == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            return;
        }

        saveButton.setEnabled(false);
        saveButton.setText("Saving...");

        Map<String, Object> profile = new HashMap<>();
        profile.put("name", nameField.getText().toString().trim());
        profile.put("address", addressField.getText().toString().trim());
        profile.put("phone", phoneField.getText().toString().trim());
        profile.put("age", ageField.getText().toString().trim());
        profile.put("bloodGroup", bloodField.getText().toString().trim());
        profile.put("height", heightField.getText().toString().trim());
        profile.put("weight", weightField.getText().toString().trim());

        // Allergies
        List<String> allergies = new ArrayList<>();
        addIfNotEmpty(allergies, allergyField1.getText().toString());
        addIfNotEmpty(allergies, allergyField2.getText().toString());
        addIfNotEmpty(allergies, allergyField3.getText().toString());
        profile.put("allergies", allergies);

        // Injuries
        List<String> injuries = new ArrayList<>();
        addIfNotEmpty(injuries, injuryField1.getText().toString());
        addIfNotEmpty(injuries, injuryField2.getText().toString());
        addIfNotEmpty(injuries, injuryField3.getText().toString());
        profile.put("injuries", injuries);

        List<String> contacts = new ArrayList<>();
        addIfNotEmpty(contacts, contactField1.getText().toString());
        addIfNotEmpty(contacts, contactField2.getText().toString());
        addIfNotEmpty(contacts, contactField3.getText().toString());
        profile.put("contacts", contacts);

        db.collection("users")
                .document(uid)
                .set(profile)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Profile saved successfully", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(this, UserProfileActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    saveButton.setEnabled(true);
                    saveButton.setText("Save Profile");
                });
    }

    private void addIfNotEmpty(List<String> list, String text) {
        if (text != null && !text.trim().isEmpty()) {
            list.add(text.trim());
        }
    }
}
