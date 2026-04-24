package com.example.hldsn.login_module;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.*;
import android.content.Intent;

import com.example.hldsn.R;
import com.example.hldsn.mesh.identity.MeshIdentityManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    EditText firstNameField, lastNameField, mobileNumberField, emailField, passwordField, confirmPasswordField;
    CheckBox termsCheckbox;
    Button submitButton;

    FirebaseAuth auth;
    FirebaseFirestore db;
    MeshIdentityManager meshIdentityManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        meshIdentityManager = new MeshIdentityManager(this);


        firstNameField = findViewById(R.id.firstNameField);
        lastNameField = findViewById(R.id.lastNameField);
        mobileNumberField = findViewById(R.id.mobileNumberField);
        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        confirmPasswordField = findViewById(R.id.confirmPasswordField);
        termsCheckbox = findViewById(R.id.termsCheckbox);
        submitButton = findViewById(R.id.submitButton);

        submitButton.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {

        String firstName = firstNameField.getText().toString().trim();
        String lastName = lastNameField.getText().toString().trim();
        String mobile = mobileNumberField.getText().toString().trim();
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();
        String confirmPassword = confirmPasswordField.getText().toString().trim();
        String role="user";

        // Validations
        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() ||
                mobile.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!termsCheckbox.isChecked()) {
            Toast.makeText(this, "You must agree to terms", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create user in Firebase Auth
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser currentUser = auth.getCurrentUser();
                        if (currentUser == null) {
                            Toast.makeText(this, "Signup completed but user session is missing", Toast.LENGTH_LONG).show();
                            return;
                        }

                        String uid = currentUser.getUid();
                        String displayName = (firstName + " " + lastName).trim();
                        meshIdentityManager.getOrCreateLocalIdentity(displayName);
                        meshIdentityManager.syncBestEffort(uid, displayName);

                        Map<String, Object> user = new HashMap<>();
                        user.put("firstName", firstName);
                        user.put("lastName", lastName);
                        user.put("display_name", displayName);
                        user.put("mobile", mobile);
                        user.put("email", email);
                        user.put("role",role);
                        user.put("createdAt", System.currentTimeMillis());

                        // Save user data to Firestore
                        db.collection("users")
                                .document(uid)
                                .set(user)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Signup Successful!", Toast.LENGTH_SHORT).show();

                                    // Move to Home or Login
                                    startActivity(new Intent(this, LoginActivity.class));
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "Firestore Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });

                    } else {
                        String error = task.getException() != null
                                ? task.getException().getMessage()
                                : "Unknown auth error";
                        Toast.makeText(this, "Auth Error: " + error, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
