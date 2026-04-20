package com.example.hldsn.volunteer_module;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class VolunteerBasicFormActivity extends AppCompatActivity {

    private EditText firstNameField;
    private EditText surnameField;
    private EditText fullAddressField;
    private EditText ageField;
    private EditText phoneNumberField;
    private EditText emergencyContactField;
    private AutoCompleteTextView genderField;
    private EditText emailField;
    private MaterialButton nextButton;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_volunteer_basic_form);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        bindViews();
        setupGenderDropdown();
        setupNavigation();
        setupLiveValidation();
        prefillFromProfile();
        updateNextButtonState();
    }

    private void bindViews() {
        firstNameField = findViewById(R.id.firstNameField);
        surnameField = findViewById(R.id.surnameField);
        fullAddressField = findViewById(R.id.fullAddressField);
        ageField = findViewById(R.id.ageField);
        phoneNumberField = findViewById(R.id.phoneNumberField);
        emergencyContactField = findViewById(R.id.emergencyContactField);
        genderField = findViewById(R.id.genderField);
        emailField = findViewById(R.id.emailField);
        nextButton = findViewById(R.id.nextButton);
    }

    private void setupGenderDropdown() {
        if (genderField == null) {
            return;
        }

        String[] genderOptions = new String[] {"Male", "Female", "Others"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                genderOptions
        );

        genderField.setAdapter(adapter);
        genderField.setKeyListener(null);
        genderField.setCursorVisible(false);
        genderField.setOnClickListener(v -> genderField.showDropDown());
        genderField.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                genderField.showDropDown();
            }
        });
        genderField.setOnItemClickListener((parent, view, position, id) -> updateNextButtonState());
    }

    private void setupNavigation() {
        ImageView backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        nextButton.setOnClickListener(v -> {
            EditText missingField = firstMissingField();
            if (missingField != null) {
                missingField.setError("Required");
                missingField.requestFocus();
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(this, VolunteerSkillsFormActivity.class);
            intent.putExtra(VolunteerFormExtras.FIRST_NAME, textOf(firstNameField));
            intent.putExtra(VolunteerFormExtras.SURNAME, textOf(surnameField));
            intent.putExtra(VolunteerFormExtras.FULL_ADDRESS, textOf(fullAddressField));
            intent.putExtra(VolunteerFormExtras.AGE, textOf(ageField));
            intent.putExtra(VolunteerFormExtras.PHONE_NUMBER, textOf(phoneNumberField));
            intent.putExtra(VolunteerFormExtras.EMERGENCY_CONTACT, textOf(emergencyContactField));
            intent.putExtra(VolunteerFormExtras.GENDER, textOf(genderField));
            intent.putExtra(VolunteerFormExtras.EMAIL, textOf(emailField));
            startActivity(intent);
        });
    }

    private void setupLiveValidation() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateNextButtonState();
            }
        };

        for (EditText field : requiredFields()) {
            field.addTextChangedListener(watcher);
        }
    }

    private void prefillFromProfile() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return;
        }

        setIfEmpty(emailField, user.getEmail());

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        updateNextButtonState();
                        return;
                    }

                    String profileName = safe(documentSnapshot.getString("name"));
                    String profileFirstName = safe(documentSnapshot.getString("firstName"));
                    String profileLastName = safe(documentSnapshot.getString("lastName"));

                    setIfEmpty(firstNameField, firstNonBlank(profileFirstName, extractFirstName(profileName)));
                    setIfEmpty(surnameField, firstNonBlank(profileLastName, extractSurname(profileName)));
                    setIfEmpty(fullAddressField, documentSnapshot.getString("address"));
                    setIfEmpty(ageField, documentSnapshot.getString("age"));

                    String savedPhone = firstNonBlank(
                            documentSnapshot.getString("phone"),
                            documentSnapshot.getString("mobile")
                    );
                    setIfEmpty(phoneNumberField, savedPhone);

                    String emergencyContact = firstNonBlank(
                            extractPrimaryContact(documentSnapshot.get("contacts")),
                            savedPhone
                    );
                    setIfEmpty(emergencyContactField, emergencyContact);

                    setIfEmpty(genderField, documentSnapshot.getString("gender"));
                    setIfEmpty(emailField, firstNonBlank(documentSnapshot.getString("email"), user.getEmail()));

                    updateNextButtonState();
                })
                .addOnFailureListener(e -> updateNextButtonState());
    }

    private void updateNextButtonState() {
        boolean allFilled = firstMissingField() == null;
        nextButton.setEnabled(allFilled);
        nextButton.setAlpha(allFilled ? 1f : 0.6f);
    }

    private EditText firstMissingField() {
        for (EditText field : requiredFields()) {
            if (isBlank(field)) {
                return field;
            }
        }
        return null;
    }

    private EditText[] requiredFields() {
        return new EditText[] {
                firstNameField,
                surnameField,
                fullAddressField,
                ageField,
                phoneNumberField,
                emergencyContactField,
                genderField,
            emailField
        };
    }

    private boolean isBlank(EditText field) {
        return field.getText() == null || field.getText().toString().trim().isEmpty();
    }

    private void setIfEmpty(EditText field, String value) {
        if (field == null || value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || !isBlank(field)) {
            return;
        }
        field.setText(trimmed);
    }

    private String extractFirstName(String fullName) {
        String trimmed = safe(fullName);
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] parts = trimmed.split("\\s+");
        return parts.length > 0 ? parts[0] : "";
    }

    private String extractSurname(String fullName) {
        String trimmed = safe(fullName);
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] parts = trimmed.split("\\s+", 2);
        return parts.length > 1 ? parts[1] : "";
    }

    private String extractPrimaryContact(Object contactsObject) {
        if (!(contactsObject instanceof List<?>)) {
            return "";
        }
        for (Object entry : (List<?>) contactsObject) {
            if (entry == null) {
                continue;
            }
            String value = entry.toString().trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String textOf(EditText field) {
        return field != null && field.getText() != null ? field.getText().toString().trim() : "";
    }
}
