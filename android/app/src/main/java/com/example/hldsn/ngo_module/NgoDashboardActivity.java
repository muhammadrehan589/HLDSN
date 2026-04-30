package com.example.hldsn.ngo_module;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;
import com.example.hldsn.login_module.LoginActivity;
import com.example.hldsn.notification_module.SosAlertStore;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class NgoDashboardActivity extends AppCompatActivity {

    private TextView ngoNameText;
    private TextView notificationCountText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ngo_dashboard);

        ImageView backButton = findViewById(R.id.backButton);
        ImageView notificationIcon = findViewById(R.id.notificationIcon);
        notificationCountText = findViewById(R.id.tvNotificationCount);
        ngoNameText = findViewById(R.id.ngoDashboardNameText);
        MaterialButton manageVolunteersButton = findViewById(R.id.manageVolunteerRequestsButton);
        MaterialButton addCampCenterButton = findViewById(R.id.addCampCenterButton);
        MaterialButton manageCampCenterButton = findViewById(R.id.manageCampCenterButton);
        MaterialButton logoutButton = findViewById(R.id.ngoLogoutButton);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        if (notificationIcon != null) {
            notificationIcon.setOnClickListener(v ->
                    startActivity(new Intent(this, NgoNotificationsActivity.class)));
        }

        if (manageVolunteersButton != null) {
            manageVolunteersButton.setOnClickListener(v ->
                    startActivity(new Intent(this, NgoVolunteerApprovalsActivity.class)));
        }

        if (addCampCenterButton != null) {
            addCampCenterButton.setOnClickListener(v ->
                startActivity(new Intent(this, NgoCampCenterFormActivity.class)));
        }

        if (manageCampCenterButton != null) {
            manageCampCenterButton.setOnClickListener(v ->
                startActivity(new Intent(this, NgoCampCenterManagementActivity.class)));
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

        loadNgoName();
        updateNotificationBadge();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNotificationBadge();
    }

    private void loadNgoName() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            ngoNameText.setText("NGO Dashboard");
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String ngoName = documentSnapshot.getString("ngoName");
                    if (ngoName == null || ngoName.trim().isEmpty()) {
                        ngoNameText.setText("NGO Dashboard");
                        return;
                    }
                    ngoNameText.setText(ngoName.trim());
                })
                .addOnFailureListener(error -> ngoNameText.setText("NGO Dashboard"));
    }

    private void updateNotificationBadge() {
        if (notificationCountText == null) {
            return;
        }

        int count = SosAlertStore.getUnseenCount(this);
        if (count <= 0) {
            notificationCountText.setVisibility(android.view.View.GONE);
        } else if (count >= 10) {
            notificationCountText.setText("10+");
            notificationCountText.setVisibility(android.view.View.VISIBLE);
        } else {
            notificationCountText.setText(String.valueOf(count));
            notificationCountText.setVisibility(android.view.View.VISIBLE);
        }
    }
}
