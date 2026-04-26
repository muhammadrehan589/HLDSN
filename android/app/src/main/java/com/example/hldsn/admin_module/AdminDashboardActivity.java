package com.example.hldsn.admin_module;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;
import com.example.hldsn.login_module.LoginActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

public class AdminDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        ImageView backButton = findViewById(R.id.backButton);
        MaterialButton manageNgosButton = findViewById(R.id.manageNgosButton);
        MaterialButton manageUsersButton = findViewById(R.id.manageUsersButton);
        MaterialButton manageNgoRequestsButton = findViewById(R.id.manageNgoRequestsButton);
        MaterialButton logoutButton = findViewById(R.id.adminLogoutButton);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        if (manageNgosButton != null) {
            manageNgosButton.setOnClickListener(v ->
                    startActivity(new Intent(this, AdminNgoManagementActivity.class)));
        }

        if (manageUsersButton != null) {
            manageUsersButton.setOnClickListener(v ->
                    startActivity(new Intent(this, AdminUsersManagementActivity.class)));
        }

        if (manageNgoRequestsButton != null) {
            manageNgoRequestsButton.setOnClickListener(v ->
                    startActivity(new Intent(this, AdminNgoRequestsActivity.class)));
        }

        if (logoutButton != null) {
            logoutButton.setOnClickListener(v -> {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }
    }
}
