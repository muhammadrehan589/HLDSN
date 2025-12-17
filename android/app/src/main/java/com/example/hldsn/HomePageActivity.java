package com.example.hldsn;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class HomePageActivity extends AppCompatActivity {

    private static final String TAG = "HomePageActivity";
    private DrawerLayout drawerLayout;
    private ImageView menuIcon;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_side_menu);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Check current user
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(HomePageActivity.this, LoginActivity.class));
            finish();
            return;
        }
        currentUserId = user.getUid();

        drawerLayout = findViewById(R.id.drawer_layout);
        menuIcon = findViewById(R.id.menu_icon);

        menuIcon.setOnClickListener(v -> {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        // Drawer menu items
        View profileItem = findViewById(R.id.profileMenuItem);
        View homeItem = findViewById(R.id.homeMenuItem);
        View settingsItem = findViewById(R.id.settingsMenuItem);
        View aboutItem = findViewById(R.id.aboutMenuItem);
        View logoutItem = findViewById(R.id.logoutMenuItem);

        profileItem.setOnClickListener(v -> {
            // Check if profile exists **only when profile menu is clicked**
            db.collection("users").document(currentUserId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        Intent intent;
                        if (documentSnapshot.exists()) {
                            // Profile exists → show profile
                            intent = new Intent(HomePageActivity.this, UserProfileActivity.class);
                        } else {
                            // First time → add profile
                            intent = new Intent(HomePageActivity.this, SaveUserProfileActivity.class);
                        }
                        intent.putExtra("USER_ID", currentUserId);
                        startActivity(intent);
                        drawerLayout.closeDrawer(GravityCompat.START);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(HomePageActivity.this, "Error fetching profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error fetching profile", e);
                    });
        });

        homeItem.setOnClickListener(v -> drawerLayout.closeDrawer(GravityCompat.START));

        settingsItem.setOnClickListener(v -> {
            Toast.makeText(this, "Settings clicked", Toast.LENGTH_SHORT).show();
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        aboutItem.setOnClickListener(v -> {
            Toast.makeText(this, "About clicked", Toast.LENGTH_SHORT).show();
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        logoutItem.setOnClickListener(v -> {
            auth.signOut();
            startActivity(new Intent(HomePageActivity.this, LoginActivity.class));
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
