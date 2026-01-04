package com.example.hldsn;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class HomePageActivity extends AppCompatActivity {

    private static final String TAG = "PermissionDebug";

    private DrawerLayout drawerLayout;
    private View news;
    private View menuIcon;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String currentUserId;
    MaterialButton chatBtn;


    // Separate launchers for clarity and debugging
    private final ActivityResultLauncher<String> locationLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                Log.d(TAG, "Location permission result: " + isGranted);
                if (isGranted) {
                    Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Location denied – some features limited", Toast.LENGTH_LONG).show();
                }
                requestNearbyGroupIfNeeded();  // Proceed to Nearby group
            });

    private final ActivityResultLauncher<String[]> nearbyLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), results -> {
                Log.d(TAG, "Nearby permissions result: " + results);

                boolean allGranted = true;
                for (Boolean granted : results.values()) {
                    if (!granted) {
                        allGranted = false;
                    }
                }

                if (allGranted) {
                    Toast.makeText(this, "Nearby devices permissions granted!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Some nearby permissions denied – features limited", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_side_menu);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        currentUserId = user.getUid();

        initViews();
        initListeners();

        // Start permission check with debug logs
        checkAndRequestPermissions();
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        menuIcon = findViewById(R.id.menu_icon);
      chatBtn=findViewById(R.id.btn_service_chats);
    }

    private void initListeners() {

        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(androidx.core.view.GravityCompat.START));

        // Drawer items (unchanged)
        findViewById(R.id.profileMenuItem).setOnClickListener(v -> {
            db.collection("users").document(currentUserId).get()
                    .addOnSuccessListener(snapshot -> {
                        Intent intent = snapshot.exists()
                                ? new Intent(this, UserProfileActivity.class)
                                : new Intent(this, SaveUserProfileActivity.class);
                        intent.putExtra("USER_ID", currentUserId);
                        startActivity(intent);
                        drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Error fetching profile", Toast.LENGTH_SHORT).show());
        });

        findViewById(R.id.homeMenuItem).setOnClickListener(v -> drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START));
        findViewById(R.id.settingsMenuItem).setOnClickListener(v -> {
            Toast.makeText(this, "Settings clicked", Toast.LENGTH_SHORT).show();
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
        });
        findViewById(R.id.aboutMenuItem).setOnClickListener(v -> {
            Toast.makeText(this, "About clicked", Toast.LENGTH_SHORT).show();
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
        });
        findViewById(R.id.logoutMenuItem).setOnClickListener(v -> {
            auth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
        chatBtn.setOnClickListener(v -> {
            startActivity(new Intent(this, ChatsActivity.class));
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
        });
    }

    // Main permission check with extensive debug logs
    private void checkAndRequestPermissions() {
        Log.d(TAG, "=== Starting permission check ===");

        boolean hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "Location permission granted? " + hasLocation);

        boolean hasNearby = areNearbyPermissionsGranted();
        Log.d(TAG, "Nearby group permissions all granted? " + hasNearby);
        Log.d(TAG, "BLUETOOTH_SCAN: " + (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED));
        Log.d(TAG, "BLUETOOTH_CONNECT: " + (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED));
        Log.d(TAG, "BLUETOOTH_ADVERTISE: " + (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED));
        Log.d(TAG, "NEARBY_WIFI_DEVICES: " + (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED));

        if (hasLocation && hasNearby) {
            Log.d(TAG, "All permissions already granted – nothing to do");
            return;
        }

        // Show the beautiful explanation dialog first
        showBeautifulPermissionDialog();
    }

    private boolean areNearbyPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
    }

    // Shows your beautiful custom dialog (matching dialog_permission_explain_new.xml)
    private void showBeautifulPermissionDialog() {
        Log.d(TAG, "Showing permission explanation dialog");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_permission_explain, null);

        MaterialButton btnAllow = dialogView.findViewById(R.id.btn_allow);
        Button btnDeny = dialogView.findViewById(R.id.btn_deny);

        AlertDialog dialog = builder.setView(dialogView)
                .setCancelable(false)
                .create();

        btnAllow.setOnClickListener(v -> {
            Log.d(TAG, "User clicked Allow – starting with Location permission");
            dialog.dismiss();
            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        });

        btnDeny.setOnClickListener(v -> {
            Log.d(TAG, "User clicked Deny");
            dialog.dismiss();
            Toast.makeText(this, "Permissions denied – some features will be limited", Toast.LENGTH_LONG).show();
        });

        dialog.show();
    }

    // Called after Location result
    private void requestNearbyGroupIfNeeded() {
        Log.d(TAG, "Checking if Nearby group permissions are needed");

        if (areNearbyPermissionsGranted()) {
            Log.d(TAG, "Nearby permissions already granted");
            return;
        }

        String[] nearbyPermissions = {
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.NEARBY_WIFI_DEVICES
        };

        Log.d(TAG, "Requesting Nearby devices group permissions");
        nearbyLauncher.launch(nearbyPermissions);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}