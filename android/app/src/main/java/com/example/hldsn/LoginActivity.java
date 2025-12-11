package com.example.hldsn;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    EditText emailField, passwordField;
    Button loginButton;
    TextView dontHaveAccount;

    FirebaseAuth auth;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        loginButton = findViewById(R.id.loginButton);
        dontHaveAccount = findViewById(R.id.dontHaveAccount);

        dontHaveAccount.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignupActivity.class));
        });

        loginButton.setOnClickListener(v -> {
            String email = emailField.getText().toString().trim();
            String password = passwordField.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = auth.getCurrentUser();
                            if (user != null) {
                                String uid = user.getUid(); // unique user id

                                // Fetch user's phone number from Firestore
                                DocumentReference docRef = db.collection("users").document(uid);
                                docRef.get().addOnSuccessListener(documentSnapshot -> {
                                    if (documentSnapshot.exists()) {
                                        String phone = documentSnapshot.getString("mobile");
                                        if (phone != null && !phone.isEmpty()) {
                                            // Go to OTP verification screen
                                            Intent intent = new Intent(LoginActivity.this, Main.class);
                                            intent.putExtra("PHONE_NUMBER", phone);
                                            startActivity(intent);
                                            finish();
                                        } else {
                                            Toast.makeText(this, "Phone number not found!", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }).addOnFailureListener(e -> {
                                    Toast.makeText(this, "Error fetching user data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                            }
                        } else {
                            Toast.makeText(this,
                                    "Login Failed: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }
}
