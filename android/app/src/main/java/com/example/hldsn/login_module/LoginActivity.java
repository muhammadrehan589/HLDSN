package com.example.hldsn.login_module;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.auth.RoleBasedNavigator;
import com.example.hldsn.R;
import com.example.hldsn.mesh.identity.MeshIdentityManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private EditText emailField, passwordField;
    private Button loginButton, signupButton;
    private TextView forgotPassword;

    private FirebaseAuth auth;
    private MeshIdentityManager meshIdentityManager;

    @Override
    protected void onStart() {
        super.onStart();
        if (auth != null && auth.getCurrentUser() != null) {
            ensureMeshIdentityAndSync(auth.getCurrentUser());
            RoleBasedNavigator.routeAfterLogin(this, auth.getCurrentUser());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        meshIdentityManager = new MeshIdentityManager(this);

        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        loginButton = findViewById(R.id.loginButton);
        signupButton = findViewById(R.id.signupButton);
        forgotPassword = findViewById(R.id.forgotPassword);

        // Navigate to SignupActivity
        signupButton.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignupActivity.class));
        });

        forgotPassword.setOnClickListener(v -> {
            String email = emailField.getText().toString().trim();
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email first", Toast.LENGTH_SHORT).show();
                return;
            }

            forgotPassword.setEnabled(false);
            auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        forgotPassword.setEnabled(true);
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Password reset email sent. Check your inbox and spam/junk folder.", Toast.LENGTH_LONG).show();
                        } else {
                            String error = task.getException() != null
                                    ? task.getException().getMessage()
                                    : "Unknown error";
                            Toast.makeText(this, "Could not send reset email: " + error + ". Check inbox and spam/junk if the request was accepted.", Toast.LENGTH_LONG).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        forgotPassword.setEnabled(true);
                        Toast.makeText(this, "Could not send reset email: " + e.getMessage() + ". Check inbox and spam/junk if the request was accepted.", Toast.LENGTH_LONG).show();
                    });
        });

        // Login button click
        loginButton.setOnClickListener(v -> {
            String email = emailField.getText().toString().trim();
            String password = passwordField.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            loginUser(email, password);
        });
    }

    private void loginUser(String email, String password) {
        loginButton.setEnabled(false);
        loginButton.setText("Logging in...");
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    loginButton.setEnabled(true);


                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            ensureMeshIdentityAndSync(user);
                            RoleBasedNavigator.routeAfterLogin(LoginActivity.this, user);
                        }
                    } else {
                        String error = task.getException() != null
                                ? task.getException().getMessage()
                                : "Unknown login error";
                        Toast.makeText(LoginActivity.this,
                                "Login Failed: " + error,
                                Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    loginButton.setEnabled(true);
                    Toast.makeText(LoginActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void ensureMeshIdentityAndSync(FirebaseUser user) {
        if (user == null) {
            return;
        }
        String displayName = user.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            String email = user.getEmail();
            if (email != null && !email.trim().isEmpty()) {
                int idx = email.indexOf('@');
                displayName = idx > 0 ? email.substring(0, idx) : email;
            }
        }
        meshIdentityManager.getOrCreateLocalIdentity(displayName);
        meshIdentityManager.syncBestEffort(user.getUid(), displayName);
    }
}
