package com.example.hldsn.home;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
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

import com.example.hldsn.R;
import com.example.hldsn.incident_report_module.ChatsActivity;
import com.example.hldsn.incident_report_module.IncidentModel;
import com.example.hldsn.login_module.LoginActivity;
import com.example.hldsn.login_module.SaveUserProfileActivity;
import com.example.hldsn.login_module.UserProfileActivity;
import com.example.hldsn.notification_module.NotificationAdapter;
import com.example.hldsn.notification_module.NotificationItem;
import com.example.hldsn.notification_module.SosAlertRecord;
import com.example.hldsn.notification_module.SosAlertStore;
import com.example.hldsn.services.safety_tips.SafetyTipsActivity;
import com.example.hldsn.sos.SosListenerService;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HomePageActivity extends AppCompatActivity {

    private static final String TAG = "HomePageActivity";
    private static final String SOS_SERVICE_CLASS = "com.example.hldsn.sos.SosForegroundService";
    private static final String ACTION_START_MESH = "com.example.hldsn.sos.ACTION_START_MESH";
    private static final String ACTION_TRIGGER_SOS = "com.example.hldsn.sos.ACTION_TRIGGER_SOS";
    private static final String ACTION_SOS_STATUS = "com.example.hldsn.sos.ACTION_SOS_STATUS";
    private static final String ACTION_SOS_ALERTS_UPDATED = "com.example.hldsn.sos.ACTION_SOS_ALERTS_UPDATED";
    private static final String ACTION_OPEN_NOTIFICATIONS = "com.example.hldsn.sos.ACTION_OPEN_NOTIFICATIONS";
    private static final String EXTRA_SENDER_NAME = "extra_sender_name";
    private static final String EXTRA_SOS_STATUS = "extra_status";
    private static final String PREFS_PERMISSION_GATE = "home_permission_gate";
    private static final String PREF_KEY_ALL_PERMISSIONS_PREFIX = "all_permissions_prompted_";

    private DrawerLayout drawerLayout;
    private ImageView menuIcon, notificationIcon;
    private TextView tvNotificationCount;
    private MaterialButton chatBtn, tipsBtn;
    private View emergencyBtn;

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
    private final List<SosAlertRecord> sosAlerts = new ArrayList<>();

    /** Prevents the "no internet" toast from firing on every Firestore retry. */
    private boolean hasShownNetworkError = false;
    private boolean pendingSosAfterRadioEnable;

    // Permission launchers
    private final ActivityResultLauncher<String[]> allPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), results -> {
                boolean allGranted = true;
                for (Boolean granted : results.values()) {
                    if (!Boolean.TRUE.equals(granted)) {
                        allGranted = false;
                        break;
                    }
                }
                markAllPermissionsPrompted();
                if (allGranted) {
                    Toast.makeText(this, "All required permissions granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Some permissions were denied. App will run with limited features.", Toast.LENGTH_LONG).show();
                }
                requestNearbyGroupIfNeeded();
                startMeshServiceIfReady();
            });

    private final ActivityResultLauncher<String> locationLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                Log.d(TAG, "Location permission: " + isGranted);
                if (isGranted) {
                    Toast.makeText(this, "Location granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Location denied", Toast.LENGTH_LONG).show();
                }
                requestNearbyGroupIfNeeded();
                startMeshServiceIfReady();
            });

    private final ActivityResultLauncher<String[]> nearbyLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), results -> {
                boolean allGranted = results.values().stream().allMatch(Boolean::booleanValue);
                if (allGranted) {
                    Toast.makeText(this, "Nearby permissions granted", Toast.LENGTH_SHORT).show();
                    startMeshServiceIfReady();
                } else {
                    Toast.makeText(this, "Some nearby permissions denied", Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<Intent> enableBluetoothLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (isBluetoothEnabled()) {
                    continueAfterRadioReady();
                } else {
                    Toast.makeText(this, "Bluetooth is required for SOS mesh", Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<Intent> enableWifiLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (isWifiEnabled()) {
                    continueAfterRadioReady();
                } else {
                    Toast.makeText(this, "Wi-Fi is required for Wi-Fi Direct mesh", Toast.LENGTH_LONG).show();
                }
            });

    private final BroadcastReceiver sosStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            if (ACTION_SOS_STATUS.equals(intent.getAction())) {
                String status = intent.getStringExtra(EXTRA_SOS_STATUS);
                if (status != null && !status.isEmpty()) {
                    Toast.makeText(HomePageActivity.this, status, Toast.LENGTH_SHORT).show();
                }
            } else if (ACTION_SOS_ALERTS_UPDATED.equals(intent.getAction())) {
                loadSosAlerts();
                refreshNotificationContent();
            }
        }
    };

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
        loadSosAlerts();

        checkAndRequestPermissions();
        handleLaunchIntent(getIntent());
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        menuIcon = findViewById(R.id.menu_icon);
        notificationIcon = findViewById(R.id.notification_icon);
        tvNotificationCount = findViewById(R.id.tv_notification_count);
        chatBtn = findViewById(R.id.btn_service_chats);
        tipsBtn = findViewById(R.id.btn_info_safety);
        emergencyBtn = findViewById(R.id.btn_emergency);
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
        refreshNotificationContent();


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

        // SOS button
        if (emergencyBtn != null) {
            emergencyBtn.setOnClickListener(v -> showSosConfirmDialog());
        }

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

    private void loadSosAlerts() {
        sosAlerts.clear();
        sosAlerts.addAll(SosAlertStore.getAlerts(this));
    }

    private List<NotificationItem> buildNotificationItems() {
        List<NotificationItem> items = new ArrayList<>();
        for (SosAlertRecord alert : sosAlerts) {
            items.add(NotificationItem.fromSosAlert(alert));
        }
        for (IncidentModel incident : unreadIncidents) {
            items.add(NotificationItem.fromIncident(incident));
        }
        items.sort((left, right) -> Long.compare(right.getTimestampMs(), left.getTimestampMs()));
        return items;
    }

    private int getUnseenSosCount() {
        int count = 0;
        for (SosAlertRecord alert : sosAlerts) {
            if (!alert.isSeen()) {
                count++;
            }
        }
        return count;
    }

    private void refreshNotificationContent() {
        if (notificationAdapter == null) {
            return;
        }

        List<NotificationItem> items = buildNotificationItems();
        notificationAdapter.updateList(items);
        updateNotificationBadge(unreadIncidents.size() + getUnseenSosCount());

        if (items.isEmpty()) {
            notificationRecyclerView.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            notificationRecyclerView.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
        }
    }

    private void handleLaunchIntent(Intent intent) {
        if (intent == null || !ACTION_OPEN_NOTIFICATIONS.equals(intent.getAction())) {
            return;
        }
        drawerLayout.post(() -> {
            loadSosAlerts();
            SosAlertStore.markAllSeen(this);
            loadSosAlerts();
            refreshNotificationContent();
            drawerLayout.openDrawer(GravityCompat.END);
        });
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
                        // UNAVAILABLE = device is offline / DNS failure – Firestore will
                        // retry automatically; only show the toast once to avoid spam.
                        boolean isNetworkError = (error instanceof FirebaseFirestoreException)
                                && ((FirebaseFirestoreException) error).getCode()
                                        == FirebaseFirestoreException.Code.UNAVAILABLE;
                        if (!hasShownNetworkError) {
                            hasShownNetworkError = true;
                            String msg = isNetworkError
                                    ? "No internet connection – showing cached data"
                                    : "Error loading incidents";
                            runOnUiThread(() ->
                                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
                        }
                        return;
                    }

                    // Successful response – reset the error flag so the user is
                    // informed if connectivity is lost again later.
                    hasShownNetworkError = false;

                    if (value == null || value.isEmpty()) {
                        runOnUiThread(() -> {
                            unreadIncidents.clear();
                            refreshNotificationContent();
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
                        Log.d(TAG, "Refreshing notification drawer with " + unread + " incident items");
                        refreshNotificationContent();
                    });
                });
    }

    private void markCurrentNotificationsAsSeen() {
        if (notificationAdapter == null) return;

        List<NotificationItem> current = notificationAdapter.getCurrentList();
        if (current.isEmpty()) return;

        for (NotificationItem item : current) {
            if (!item.isSosAlert() && item.getId() != null) {
                seenIncidentIds.add(item.getId());
            }
        }

        saveSeenIncidentIds();
        SosAlertStore.markAllSeen(this);
        loadSosAlerts();
        unreadIncidents.clear();
        refreshNotificationContent();
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
        List<String> missingPermissions = getMissingRuntimePermissions();
        if (!isAllPermissionsPrompted() && !missingPermissions.isEmpty()) {
            allPermissionsLauncher.launch(missingPermissions.toArray(new String[0]));
            return;
        }

        if (!hasLocationPermission()) {
            showBeautifulPermissionDialog();
            return;
        }

        requestNearbyGroupIfNeeded();
        startMeshServiceIfReady();
    }

    private List<String> getMissingRuntimePermissions() {
        List<String> missing = new ArrayList<>();
        addMissingPermission(missing, Manifest.permission.ACCESS_FINE_LOCATION);
        addMissingPermission(missing, Manifest.permission.ACCESS_COARSE_LOCATION);
        addMissingPermission(missing, Manifest.permission.CAMERA);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addMissingPermission(missing, Manifest.permission.POST_NOTIFICATIONS);
            addMissingPermission(missing, Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            addMissingPermission(missing, Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addMissingPermission(missing, Manifest.permission.BLUETOOTH_SCAN);
            addMissingPermission(missing, Manifest.permission.BLUETOOTH_CONNECT);
            addMissingPermission(missing, Manifest.permission.BLUETOOTH_ADVERTISE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addMissingPermission(missing, Manifest.permission.NEARBY_WIFI_DEVICES);
        }

        return missing;
    }

    private void addMissingPermission(List<String> missing, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            missing.add(permission);
        }
    }

    private boolean isAllPermissionsPrompted() {
        SharedPreferences preferences = getSharedPreferences(PREFS_PERMISSION_GATE, MODE_PRIVATE);
        return preferences.getBoolean(PREF_KEY_ALL_PERMISSIONS_PREFIX + currentUserId, false);
    }

    private void markAllPermissionsPrompted() {
        SharedPreferences preferences = getSharedPreferences(PREFS_PERMISSION_GATE, MODE_PRIVATE);
        preferences.edit().putBoolean(PREF_KEY_ALL_PERMISSIONS_PREFIX + currentUserId, true).apply();
    }

    private boolean areNearbyPermissionsGranted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }

        boolean bluetoothGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                        == PackageManager.PERMISSION_GRANTED;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return bluetoothGranted &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                            == PackageManager.PERMISSION_GRANTED;
        }

        return bluetoothGranted;
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

    // ── SOS ───────────────────────────────────────────────────────────────────

    private void showSosConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("🊘 Send SOS Alert?")
                .setMessage("This will IMMEDIATELY alert all your contacts with your "
                        + "current location.\n\nOnly use in a real emergency.")
                .setPositiveButton("YES, SEND SOS", (dialog, which) -> triggerSos())
                .setNegativeButton("Cancel", null)
                .setCancelable(true)
                .show();
    }

    private void requestNearbyGroupIfNeeded() {
        if (areNearbyPermissionsGranted()) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }

        String[] perms;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms = new String[] {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.NEARBY_WIFI_DEVICES
            };
        } else {
            perms = new String[] {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE
            };
        }
        nearbyLauncher.launch(perms);
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasSosPermissions() {
        return hasLocationPermission() && areNearbyPermissionsGranted();
    }

    private void startMeshServiceIfReady() {
        if (!hasSosPermissions()) return;
        if (!ensureRadiosEnabled(false)) return;

        Intent intent = new Intent();
        intent.setClassName(getPackageName(), SOS_SERVICE_CLASS);
        intent.setAction(ACTION_START_MESH);
        startForegroundService(intent);
    }

    private void triggerSos() {
        if (!hasLocationPermission()) {
            showBeautifulPermissionDialog();
            return;
        }
        if (!areNearbyPermissionsGranted()) {
            requestNearbyGroupIfNeeded();
            return;
        }
        if (!ensureRadiosEnabled(true)) {
            return;
        }

        startSosNow();
    }

    private void startSosNow() {
        Intent intent = new Intent();
        intent.setClassName(getPackageName(), SOS_SERVICE_CLASS);
        intent.setAction(ACTION_TRIGGER_SOS);
        String senderHint = resolveSenderHint();
        if (!senderHint.isEmpty()) {
            intent.putExtra(EXTRA_SENDER_NAME, senderHint);
        }
        Log.d(TAG, "Triggering SOS with senderHint=" + (senderHint.isEmpty() ? "none" : senderHint));
        startForegroundService(intent);
    }

    private String resolveSenderHint() {
        FirebaseUser user = auth != null ? auth.getCurrentUser() : null;
        if (user == null) {
            return "";
        }
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            return user.getDisplayName().trim();
        }
        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            String email = user.getEmail().trim();
            int at = email.indexOf('@');
            return at > 0 ? email.substring(0, at) : email;
        }
        return "";
    }

    private boolean ensureRadiosEnabled(boolean sosRequested) {
        pendingSosAfterRadioEnable = sosRequested;

        if (!isBluetoothEnabled()) {
            requestBluetoothEnable();
            return false;
        }

        if (!isWifiEnabled()) {
            requestWifiEnable();
            return false;
        }

        return true;
    }

    private void continueAfterRadioReady() {
        if (!isBluetoothEnabled() || !isWifiEnabled()) {
            return;
        }

        if (pendingSosAfterRadioEnable) {
            pendingSosAfterRadioEnable = false;
            startSosNow();
            return;
        }

        startMeshServiceIfReady();
    }

    private void requestBluetoothEnable() {
        try {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBluetoothLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Unable to open Bluetooth enable dialog", Toast.LENGTH_LONG).show();
        }
    }

    private void requestWifiEnable() {
        try {
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                intent = new Intent(Settings.Panel.ACTION_WIFI);
            } else {
                intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            }
            enableWifiLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Unable to open Wi-Fi settings", Toast.LENGTH_LONG).show();
        }
    }

    private boolean isBluetoothEnabled() {
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        if (bluetoothManager == null) {
            return false;
        }
        BluetoothAdapter adapter = bluetoothManager.getAdapter();
        return adapter != null && adapter.isEnabled();
    }

    private boolean isWifiEnabled() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        return wifiManager != null && wifiManager.isWifiEnabled();
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
        loadSosAlerts();
        startListeningToIncidents();
        ContextCompat.registerReceiver(
                this,
                sosStatusReceiver,
                buildNotificationIntentFilter(),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        startMeshServiceIfReady();
        handleLaunchIntent(getIntent());
    }

    private IntentFilter buildNotificationIntentFilter() {
        IntentFilter filter = new IntentFilter(ACTION_SOS_STATUS);
        filter.addAction(ACTION_SOS_ALERTS_UPDATED);
        return filter;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (incidentsListener != null) {
            incidentsListener.remove();
            incidentsListener = null;
        }
        hasShownNetworkError = false;
        try {
            unregisterReceiver(sosStatusReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver may already be unregistered.
        }
    }
}