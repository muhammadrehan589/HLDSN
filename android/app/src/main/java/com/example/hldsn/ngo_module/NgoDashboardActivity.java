package com.example.hldsn.ngo_module;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;
import com.example.hldsn.login_module.LoginActivity;
import com.example.hldsn.notification_module.SosAlertStore;
import com.example.hldsn.notification_module.UserNotificationStore;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class NgoDashboardActivity extends AppCompatActivity {

    private TextView ngoNameText;
    private TextView notificationCountText;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String currentUserId = "";
    private int userNotificationCount = 0;
    private ListenerRegistration userNotificationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ngo_dashboard);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        ImageView backButton = findViewById(R.id.backButton);
        ImageView notificationIcon = findViewById(R.id.notificationIcon);
        notificationCountText = findViewById(R.id.tvNotificationCount);
        ngoNameText = findViewById(R.id.ngoDashboardNameText);
        MaterialButton manageVolunteersButton = findViewById(R.id.manageVolunteerRequestsButton);
        MaterialButton viewVolunteersButton = findViewById(R.id.viewVolunteersButton);
        MaterialButton createTaskButton = findViewById(R.id.createTaskButton);
        MaterialButton viewAssignedTasksButton = findViewById(R.id.viewAssignedTasksButton);
        MaterialButton addResourceButton = findViewById(R.id.addResourceButton);
        MaterialButton viewResourcesButton = findViewById(R.id.viewResourcesButton);
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

        if (viewVolunteersButton != null) {
            viewVolunteersButton.setOnClickListener(v ->
                    startActivity(new Intent(this, NgoViewVolunteersActivity.class)));
        }

        if (createTaskButton != null) {
            createTaskButton.setOnClickListener(v ->
                startActivity(new Intent(this, NgoTaskCreationActivity.class)));
        }

        if (viewAssignedTasksButton != null) {
            viewAssignedTasksButton.setOnClickListener(v ->
                startActivity(new Intent(this, NgoTaskListActivity.class)));
        }

        if (addResourceButton != null) {
            addResourceButton.setOnClickListener(v ->
                    startActivity(new Intent(this, NgoResourceCreationActivity.class)));
        }

        if (viewResourcesButton != null) {
            viewResourcesButton.setOnClickListener(v ->
                    startActivity(new Intent(this, NgoResourceListActivity.class)));
        }

        MaterialButton viewDonationsButton = findViewById(R.id.viewDonationsButton);
        if (viewDonationsButton != null) {
            viewDonationsButton.setOnClickListener(v ->
                    startActivity(new Intent(this, NgoDonationListActivity.class)));
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

        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
            listenForUserNotifications();
        }

        loadNgoName();
        updateNotificationBadge();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNotificationBadge();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (userNotificationListener != null) {
            userNotificationListener.remove();
            userNotificationListener = null;
        }
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

        int count = SosAlertStore.getUnseenCount(this) + userNotificationCount;
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

    private void listenForUserNotifications() {
        if (currentUserId.isEmpty()) {
            return;
        }

        if (userNotificationListener != null) {
            userNotificationListener.remove();
        }

        userNotificationListener = UserNotificationStore.observeNotificationsForUser(
                db,
                currentUserId,
                (snapshot, error) -> {
                    if (error != null) {
                        return;
                    }
                    userNotificationCount = snapshot == null ? 0 : snapshot.size();
                    updateNotificationBadge();
                }
        );
    }
}
