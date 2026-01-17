package com.example.hldsn;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HomePageActivity extends AppCompatActivity {

    private static final String TAG = "HomePageActivity";

    private DrawerLayout drawerLayout;
    private ImageView menuIcon, notificationIcon;
    private TextView tvNotificationCount;
    private MaterialButton chatBtn, tipsBtn;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String currentUserId;

    // Notification drawer
    private RecyclerView notificationRecyclerView;
    private NotificationAdapter notificationAdapter;

    // Firestore listener
    private ListenerRegistration incidentsListener;

    // Track seen incidents
    private Set<String> seenIncidentIds = new HashSet<>();

    // Badge counter
    private int unreadCount = 0;
    private View emptyStateLayout;
    List<IncidentModel> unreadIncidents = new ArrayList<>();

    // Permission launchers
    private final ActivityResultLauncher<String> locationLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                Log.d(TAG, "Location permission: " + isGranted);
                if (isGranted) {
                    Toast.makeText(this, "Location granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Location denied", Toast.LENGTH_LONG).show();
                }
                requestNearbyGroupIfNeeded();
            });

    private final ActivityResultLauncher<String[]> nearbyLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), results -> {
                boolean allGranted = results.values().stream().allMatch(Boolean::booleanValue);
                if (allGranted) {
                    Toast.makeText(this, "Nearby permissions granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Some nearby permissions denied", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_side_menu);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        currentUserId = auth.getCurrentUser().getUid();

        initViews();
        initNotificationDrawer();
        initListeners();
        loadSeenIncidentIds();

        checkAndRequestPermissions();
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        menuIcon = findViewById(R.id.menu_icon);
        notificationIcon = findViewById(R.id.notification_icon);
        tvNotificationCount = findViewById(R.id.tv_notification_count);
        chatBtn = findViewById(R.id.btn_service_chats);
        tipsBtn = findViewById(R.id.btn_info_safety);
    }

    private void initNotificationDrawer() {
        notificationRecyclerView = findViewById(R.id.notificationRecyclerView);
        emptyStateLayout = findViewById(R.id.empty_state_layout);
        if (notificationRecyclerView == null) {
            Log.e(TAG, "notificationRecyclerView not found in layout!");
            return;
        }

        // TEMP: Force red background to see if RecyclerView is visible

        notificationRecyclerView.setVisibility(View.VISIBLE);

        notificationRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        notificationAdapter = new NotificationAdapter(this);
        notificationRecyclerView.setAdapter(notificationAdapter);


    }

    private void initListeners() {
        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        notificationIcon.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.closeDrawer(GravityCompat.END);
                markCurrentNotificationsAsSeen();
            } else {
                drawerLayout.openDrawer(GravityCompat.END);
                updateNotificationBadge(0);

            }
        });

        chatBtn.setOnClickListener(v -> startActivity(new Intent(this, ChatsActivity.class)));
        tipsBtn.setOnClickListener(v -> startActivity(new Intent(this, SafetyTipsActivity.class)));

        // Profile menu example
        findViewById(R.id.profileMenuItem).setOnClickListener(v -> {
            Intent intent = new Intent(this,
                    auth.getCurrentUser() != null ? UserProfileActivity.class : SaveUserProfileActivity.class);
            startActivity(intent);
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        findViewById(R.id.logoutMenuItem).setOnClickListener(v -> {
            auth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void loadSeenIncidentIds() {
        SharedPreferences prefs = getSharedPreferences("notification_prefs", MODE_PRIVATE);
        seenIncidentIds = new HashSet<>(prefs.getStringSet("seen_incident_ids", new HashSet<>()));
        Log.d(TAG, "Loaded " + seenIncidentIds.size() + " seen incident IDs");
    }

    private void saveSeenIncidentIds() {
        SharedPreferences prefs = getSharedPreferences("notification_prefs", MODE_PRIVATE);
        prefs.edit().putStringSet("seen_incident_ids", seenIncidentIds).apply();
        Log.d(TAG, "Saved " + seenIncidentIds.size() + " seen IDs");
    }

    private void startListeningToIncidents() {
        Log.d(TAG, "===== Starting incidents listener =====");

        if (incidentsListener != null) {
            incidentsListener.remove();
        }

        incidentsListener = db.collection("incidents")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listener error:", error);
                        runOnUiThread(() -> Toast.makeText(this, "Error loading incidents", Toast.LENGTH_SHORT).show());
                        return;
                    }

                    if (value == null || value.isEmpty()) {
                        runOnUiThread(() -> {
                            updateNotificationBadge(0);
                        });
                        return;

                    }

                    unreadIncidents.clear();  // Clear before adding new

                    for (DocumentSnapshot doc : value.getDocuments()) {
                        String id = doc.getId();
                        if (!seenIncidentIds.contains(id)) {
                            IncidentModel incident = doc.toObject(IncidentModel.class);
                            if (incident != null) {
                                incident.setId(id);
                                unreadIncidents.add(incident);
                            }
                        }
                    }

                    final int unread = unreadIncidents.size();
                    Log.d(TAG, "Unread incidents count: " + unread);

                    runOnUiThread(() -> {
                        updateNotificationBadge(unread);
                        if (notificationAdapter != null) {
                            Log.d(TAG, "Calling updateList with " + unread + " items");
                            notificationAdapter.updateList(unreadIncidents);

                            if (unreadIncidents.isEmpty()) {
                                notificationRecyclerView.setVisibility(View.GONE);
                                emptyStateLayout.setVisibility(View.VISIBLE);
                            } else {
                                notificationRecyclerView.setVisibility(View.VISIBLE);
                                emptyStateLayout.setVisibility(View.GONE);
                            }

                        }
                    });
                });
    }

    private void markCurrentNotificationsAsSeen() {
        if (notificationAdapter == null) return;

        List<IncidentModel> current = notificationAdapter.getCurrentList();
        if (current.isEmpty()) return;

        for (IncidentModel incident : current) {
            if (incident.getId() != null) {
                seenIncidentIds.add(incident.getId());
            }
        }

        saveSeenIncidentIds();
        updateNotificationBadge(0);

        // Refresh list to show empty after marking seen
        if (notificationAdapter != null) {
            notificationAdapter.updateList(new ArrayList<>());
            notificationRecyclerView.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        }
    }

    private void updateNotificationBadge(int count) {
        if (tvNotificationCount == null) return;

        runOnUiThread(() -> {
            if (count <= 0) {
                tvNotificationCount.setVisibility(View.GONE);
            } else if (count >= 10) {
                tvNotificationCount.setText("10+");
                tvNotificationCount.setVisibility(View.VISIBLE);
            } else {
                tvNotificationCount.setText(String.valueOf(count));
                tvNotificationCount.setVisibility(View.VISIBLE);
            }
        });
    }

    // Permission methods (kept mostly same)
    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            showBeautifulPermissionDialog();
        }
    }

    private boolean areNearbyPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                        == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private void showBeautifulPermissionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_permission_explain, null);

        MaterialButton btnAllow = dialogView.findViewById(R.id.btn_allow);
        android.widget.Button btnDeny = dialogView.findViewById(R.id.btn_deny);

        AlertDialog dialog = builder.setView(dialogView).setCancelable(false).create();

        btnAllow.setOnClickListener(v -> {
            dialog.dismiss();
            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        });

        btnDeny.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Permissions denied", Toast.LENGTH_LONG).show();
        });

        dialog.show();
    }

    private void requestNearbyGroupIfNeeded() {
        if (areNearbyPermissionsGranted()) return;
        String[] perms = {
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.NEARBY_WIFI_DEVICES
        };
        nearbyLauncher.launch(perms);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END);
            markCurrentNotificationsAsSeen();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        startListeningToIncidents();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (incidentsListener != null) {
            incidentsListener.remove();
            incidentsListener = null;
        }
    }
}