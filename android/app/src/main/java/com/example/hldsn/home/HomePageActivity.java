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
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.hldsn.R;
import com.example.hldsn.debug.CrashDebugger;
import com.example.hldsn.firstaid.EmergencyFirstAidActivity;
import com.example.hldsn.nearby.NearbyHelpActivity;
import com.example.hldsn.incident_report_module.ChatsActivity;
import com.example.hldsn.incident_report_module.DisplayReportActivity;
import com.example.hldsn.incident_report_module.IncidentModel;
import com.example.hldsn.login_module.LoginActivity;
import com.example.hldsn.login_module.SaveUserProfileActivity;
import com.example.hldsn.login_module.UserProfileActivity;
import com.example.hldsn.ngo_module.NgoRegistrationRequestActivity;
import com.example.hldsn.notification_module.NotificationAdapter;
import com.example.hldsn.notification_module.NotificationItem;
import com.example.hldsn.notification_module.SosAlertRecord;
import com.example.hldsn.notification_module.SosAlertStore;
import com.example.hldsn.hazard_module.HazardAlertMapActivity;
import com.example.hldsn.services.news.NewsActivity;
import com.example.hldsn.services.safety_tips.SafetyTipsActivity;
import com.example.hldsn.sos.SosListenerService;
import com.example.hldsn.volunteer_module.CampLocationsMapActivity;
import com.example.hldsn.volunteer_module.VolunteerNetworkMapActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.hldsn.home.NewsCarouselAdapter;
import com.example.hldsn.services.news.ApiNewsRepository;
import com.example.hldsn.services.news.NewsDetailActivity;
import com.example.hldsn.services.news.NewsItem;
import com.example.hldsn.services.news.NewsRepository;
import java.util.Timer;
import java.util.TimerTask;

public class HomePageActivity extends AppCompatActivity {

    private static final String TAG = "HomePageActivity";
    private static final String SOS_UI_TRACE_TAG = "SOS_UI_TRACE";
    private static final String SOS_SERVICE_CLASS = "com.example.hldsn.sos.SosForegroundService";
    private static final String ACTION_START_MESH = "com.example.hldsn.sos.ACTION_START_MESH";
    private static final String ACTION_TRIGGER_SOS = "com.example.hldsn.sos.ACTION_TRIGGER_SOS";
    private static final String ACTION_SOS_STATUS = "com.example.hldsn.sos.ACTION_SOS_STATUS";
    private static final String ACTION_SOS_ALERTS_UPDATED = "com.example.hldsn.sos.ACTION_SOS_ALERTS_UPDATED";
    private static final String ACTION_OPEN_NOTIFICATIONS = "com.example.hldsn.sos.ACTION_OPEN_NOTIFICATIONS";
    private static final String ACTION_MESH_MESSAGE_RECEIVED = "com.example.hldsn.sos.ACTION_MESH_MESSAGE_RECEIVED";
    private static final String EXTRA_SENDER_NAME = "extra_sender_name";
    private static final String EXTRA_SOS_STATUS = "extra_status";
    private static final String EXTRA_MESH_TEXT = "extra_mesh_text";
    private static final String PREFS_PERMISSION_GATE = "home_permission_gate";
    private static final String PREF_KEY_ALL_PERMISSIONS_PREFIX = "all_permissions_prompted_";

    private DrawerLayout drawerLayout;
    private ImageView menuIcon, notificationIcon;
    private TextView tvNotificationCount;
    private MaterialButton chatBtn, tipsBtn, newsBtn, communityChatBtn;
    private MaterialButton hazardAlertBtn;
    private View emergencyBtn;
    private MaterialButton emergencyNumbersBtn;
    private MaterialButton emergencyFirstAidBtn;
    private MaterialButton nearbyHelpBtn;

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

    private View emptyStateLayout;
    List<IncidentModel> unreadIncidents = new ArrayList<>();
    private final List<SosAlertRecord> sosAlerts = new ArrayList<>();

    // Carousel fields
    private ViewPager2 newsViewPager;
    private NewsCarouselAdapter newsCarouselAdapter;
    private final List<NewsItem> carouselNewsList = new ArrayList<>();
    private Timer carouselTimer;
    private final Handler carouselHandler = new Handler(Looper.getMainLooper());

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
            } else if (ACTION_MESH_MESSAGE_RECEIVED.equals(intent.getAction())) {
                String text = intent.getStringExtra(EXTRA_MESH_TEXT);
                if (text != null && !text.trim().isEmpty()) {
                    Toast.makeText(HomePageActivity.this, "Offline message: " + text, Toast.LENGTH_SHORT).show();
                }
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

        setupNewsCarousel();

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
        communityChatBtn = findViewById(R.id.btn_info_community_chat);
        newsBtn = findViewById(R.id.btn_info_news);
        hazardAlertBtn = findViewById(R.id.btn_service_alert);
        emergencyBtn = findViewById(R.id.btn_emergency);
        emergencyNumbersBtn = findViewById(R.id.btn_emergency_numbers);
        emergencyFirstAidBtn = findViewById(R.id.btn_info_first_aid);
        nearbyHelpBtn = findViewById(R.id.btn_nearby_help);
    }

    private void initNotificationDrawer() {
        notificationRecyclerView = findViewById(R.id.notificationRecyclerView);
        emptyStateLayout = findViewById(R.id.empty_state_layout);

        if (notificationRecyclerView == null) return;

        notificationRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        notificationAdapter = new NotificationAdapter(this, this::handleNotificationCleared);
        notificationRecyclerView.setAdapter(notificationAdapter);

        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder t) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (notificationAdapter != null && position != RecyclerView.NO_POSITION) {
                    notificationAdapter.setSwipedPosition(position);
                }
            }
        };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(notificationRecyclerView);

        refreshNotificationContent();
    }

    private void handleNotificationCleared(NotificationItem itemToClear) {
        if (itemToClear == null) {
            return;
        }

        if (itemToClear.isSosAlert()) {
            SosAlertStore.removeAlertById(this, itemToClear.getId());
        } else {
            seenIncidentIds.add(itemToClear.getId());
            saveSeenIncidentIds();
            unreadIncidents.removeIf(incident -> incident.getId().equals(itemToClear.getId()));
        }

        loadSosAlerts();
        refreshNotificationContent();
        Toast.makeText(this, "Notification cleared", Toast.LENGTH_SHORT).show();
    }

    private void initListeners() {
        try {
            CrashDebugger.logActivityEvent("HomePageActivity", "initListeners() START");

            // Menu button
            menuIcon.setOnClickListener(v -> {
                try {
                    CrashDebugger.logButtonClick("menuIcon", "Open drawer");
                    drawerLayout.openDrawer(GravityCompat.START);
                } catch (Exception e) {
                    CrashDebugger.logButtonClickError("menuIcon", e);
                }
            } else {
                drawerLayout.openDrawer(GravityCompat.END);
                updateNotificationBadge(0);
                if (notificationAdapter != null) {
                    notificationAdapter.clearSwipedPosition();
                }

            }
        });

        chatBtn.setOnClickListener(v -> startActivity(new Intent(this, ChatsActivity.class)));
        tipsBtn.setOnClickListener(v -> startActivity(new Intent(this, SafetyTipsActivity.class)));
        if (hazardAlertBtn != null) {
            hazardAlertBtn.setOnClickListener(v -> startActivity(new Intent(this, HazardAlertMapActivity.class)));
        }
        if (communityChatBtn != null) {
            communityChatBtn.setOnClickListener(v -> startActivity(new Intent(this, DisplayReportActivity.class)));
        }
        View campLocationsBtn = findViewById(R.id.btn_service_camp);
        if (campLocationsBtn != null) {
            campLocationsBtn.setOnClickListener(v ->
                    startActivity(new Intent(this, CampLocationsMapActivity.class)));
        }
        View volunteerNetworkBtn = findViewById(R.id.btn_service_volunteer);
        if (volunteerNetworkBtn != null) {
            volunteerNetworkBtn.setOnClickListener(v ->
                    startActivity(new Intent(this, VolunteerNetworkMapActivity.class)));
        }
        newsBtn.setOnClickListener(v -> startActivity(new Intent(this, NewsActivity.class)));
        emergencyBtn.setOnClickListener(v -> showSosConfirmDialog());

        // Profile menu example
        findViewById(R.id.profileMenuItem).setOnClickListener(v -> {
            Intent intent = new Intent(this,
                    auth.getCurrentUser() != null ? UserProfileActivity.class : SaveUserProfileActivity.class);
            startActivity(intent);
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        View ngoRegistrationItem = findViewById(R.id.ngoRegistrationMenuItem);
        if (ngoRegistrationItem != null) {
            ngoRegistrationItem.setOnClickListener(v -> {
                startActivity(new Intent(this, NgoRegistrationRequestActivity.class));
                drawerLayout.closeDrawer(GravityCompat.START);
            });

        findViewById(R.id.logoutMenuItem).setOnClickListener(v -> {
            auth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // Home menu item - return to home page
        View homeMenuItem = findViewById(R.id.homeMenuItem);
        if (homeMenuItem != null) {
            homeMenuItem.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                Toast.makeText(this, "Already on Home", Toast.LENGTH_SHORT).show();
            });
        }

        // Back button
        ImageView backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    onBackPressed();
                }
            });
        }

        // About Us menu item
        View aboutMenuItem = findViewById(R.id.aboutMenuItem);
        if (aboutMenuItem != null) {
            aboutMenuItem.setOnClickListener(v -> showAboutUsDialog());
        }
    }

    private void showAboutUsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog);
        
        // Inflate custom layout for the dialog
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_about_us, null);
        
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        
        // Handle close button
        Button closeButton = dialogView.findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> {
            dialog.dismiss();
            drawerLayout.closeDrawer(GravityCompat.START);
        });
        
        dialog.show();
        
        // Set dialog window properties for better styling
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.login_gradient);
            dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.85), 
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            // Notification button
            notificationIcon.setOnClickListener(v -> {
                try {
                    CrashDebugger.logButtonClick("notificationIcon", "Toggle notifications drawer");
                    if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                        drawerLayout.closeDrawer(GravityCompat.END);
                        markCurrentNotificationsAsSeen();
                        if (notificationAdapter != null) {
                            notificationAdapter.clearSwipedPosition();
                        }
                    } else {
                        drawerLayout.openDrawer(GravityCompat.END);
                        updateNotificationBadge(0);
                        if (notificationAdapter != null) {
                            notificationAdapter.clearSwipedPosition();
                        }
                    }
                } catch (Exception e) {
                    CrashDebugger.logButtonClickError("notificationIcon", e);
                }
            });

            // Chat button
            if (chatBtn != null) {
                chatBtn.setOnClickListener(v -> {
                    try {
                        CrashDebugger.logButtonClick("chatBtn", "Open ChatsActivity");
                        startActivity(new Intent(HomePageActivity.this, ChatsActivity.class));
                    } catch (Exception e) {
                        CrashDebugger.logButtonClickError("chatBtn", e);
                        Toast.makeText(HomePageActivity.this, "Error opening chat", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Tips button
            if (tipsBtn != null) {
                tipsBtn.setOnClickListener(v -> {
                    try {
                        CrashDebugger.logButtonClick("tipsBtn", "Open SafetyTipsActivity");
                        startActivity(new Intent(HomePageActivity.this, SafetyTipsActivity.class));
                    } catch (Exception e) {
                        CrashDebugger.logButtonClickError("tipsBtn", e);
                        Toast.makeText(HomePageActivity.this, "Error opening tips", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Community chat button
            if (communityChatBtn != null) {
                communityChatBtn.setOnClickListener(v -> {
                    try {
                        CrashDebugger.logButtonClick("communityChatBtn", "Open DisplayReportActivity");
                        startActivity(new Intent(HomePageActivity.this, DisplayReportActivity.class));
                    } catch (Exception e) {
                        CrashDebugger.logButtonClickError("communityChatBtn", e);
                        Toast.makeText(HomePageActivity.this, "Error opening community", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // News button
            if (newsBtn != null) {
                newsBtn.setOnClickListener(v -> {
                    try {
                        CrashDebugger.logButtonClick("newsBtn", "Open NewsActivity");
                        startActivity(new Intent(HomePageActivity.this, NewsActivity.class));
                    } catch (Exception e) {
                        CrashDebugger.logButtonClickError("newsBtn", e);
                        Toast.makeText(HomePageActivity.this, "Error opening news", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Emergency button
            if (emergencyBtn != null) {
                emergencyBtn.setOnClickListener(v -> {
                    try {
                        CrashDebugger.logButtonClick("emergencyBtn", "Show SOS confirm dialog");
                        showSosConfirmDialog();
                    } catch (Exception e) {
                        CrashDebugger.logButtonClickError("emergencyBtn", e);
                        Toast.makeText(HomePageActivity.this, "Error with emergency button", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Emergency numbers button
            if (emergencyNumbersBtn != null) {
                emergencyNumbersBtn.setOnClickListener(v -> {
                    try {
                        CrashDebugger.logButtonClick("emergencyNumbersBtn", "Open EmergencyNumbersActivity");
                        startActivity(new Intent(HomePageActivity.this, EmergencyNumbersActivity.class));
                    } catch (Exception e) {
                        CrashDebugger.logButtonClickError("emergencyNumbersBtn", e);
                        Toast.makeText(HomePageActivity.this, "Error opening emergency numbers", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Emergency first aid button
            if (emergencyFirstAidBtn != null) {
                emergencyFirstAidBtn.setOnClickListener(v -> {
                    try {
                        CrashDebugger.logButtonClick("emergencyFirstAidBtn", "Open EmergencyFirstAidActivity");
                        startActivity(new Intent(HomePageActivity.this, EmergencyFirstAidActivity.class));
                    } catch (Exception e) {
                        CrashDebugger.logButtonClickError("emergencyFirstAidBtn", e);
                        Toast.makeText(HomePageActivity.this, "Error opening first aid", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Nearby help button
            if (nearbyHelpBtn != null) {
                nearbyHelpBtn.setOnClickListener(v -> {
                    try {
                        CrashDebugger.logButtonClick("nearbyHelpBtn", "Open NearbyHelpActivity");
                        startActivity(new Intent(HomePageActivity.this, NearbyHelpActivity.class));
                    } catch (Exception e) {
                        CrashDebugger.logButtonClickError("nearbyHelpBtn", e);
                        Toast.makeText(HomePageActivity.this, "Error opening nearby help", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Profile menu item
            View profileMenuItem = findViewById(R.id.profileMenuItem);
            if (profileMenuItem != null) {
                profileMenuItem.setOnClickListener(v -> {
                    try {
                        CrashDebugger.logButtonClick("profileMenuItem", "Open user profile");
                        Intent intent = new Intent(HomePageActivity.this,
                                auth.getCurrentUser() != null ? UserProfileActivity.class : SaveUserProfileActivity.class);
                        startActivity(intent);
                        drawerLayout.closeDrawer(GravityCompat.START);
                    } catch (Exception e) {
                        CrashDebugger.logButtonClickError("profileMenuItem", e);
                        Toast.makeText(HomePageActivity.this, "Error opening profile", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // NGO registration menu item
            View ngoRegistrationItem = findViewById(R.id.ngoRegistrationMenuItem);
            if (ngoRegistrationItem != null) {
                ngoRegistrationItem.setOnClickListener(v -> {
                    try {
                        CrashDebugger.logButtonClick("ngoRegistrationMenuItem", "Open NGO registration");
                        startActivity(new Intent(HomePageActivity.this, NgoRegistrationRequestActivity.class));
                        drawerLayout.closeDrawer(GravityCompat.START);
                    } catch (Exception e) {
                        CrashDebugger.logButtonClickError("ngoRegistrationMenuItem", e);
                        Toast.makeText(HomePageActivity.this, "Error opening NGO registration", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Logout menu item
            View logoutMenuItem = findViewById(R.id.logoutMenuItem);
            if (logoutMenuItem != null) {
                logoutMenuItem.setOnClickListener(v -> {
                    try {
                        CrashDebugger.logButtonClick("logoutMenuItem", "User logout");
                        auth.signOut();
                        startActivity(new Intent(HomePageActivity.this, LoginActivity.class));
                        finish();
                    } catch (Exception e) {
                        CrashDebugger.logButtonClickError("logoutMenuItem", e);
                        Toast.makeText(HomePageActivity.this, "Error logging out", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            CrashDebugger.logActivityEvent("HomePageActivity", "initListeners() SUCCESS");
        } catch (Exception e) {
            CrashDebugger.logActivityError("HomePageActivity", "initListeners()", e);
        }
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
        for (SosAlertRecord alert : sosAlerts) {
            Log.d(SOS_UI_TRACE_TAG, "LOAD_ALERT id=" + alert.getMessageId()
                    + " sender=" + alert.getSenderName()
                    + " latMilli=" + alert.getLatMilli()
                    + " lonMilli=" + alert.getLonMilli()
                    + " hasLocation=" + alert.hasLocation());
        }
    }

    private List<NotificationItem> buildNotificationItems() {
        List<NotificationItem> items = new ArrayList<>();
        for (SosAlertRecord alert : sosAlerts) {
            Log.d(SOS_UI_TRACE_TAG, "BUILD_ITEM id=" + alert.getMessageId()
                    + " title=" + alert.getTitle()
                    + " subtitle=" + alert.getSubtitle());
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

    private void setupNewsCarousel() {
        newsViewPager = findViewById(R.id.news_view_pager);
        if (newsViewPager == null) return;

        newsCarouselAdapter = new NewsCarouselAdapter(this::openNewsDetail);
        newsViewPager.setAdapter(newsCarouselAdapter);

        fetchCarouselNews();
    }

    private void fetchCarouselNews() {
        new ApiNewsRepository(this).fetchNews(false, new NewsRepository.Callback() {
            @Override
            public void onSuccess(List<NewsItem> items) {
                if (items != null && !items.isEmpty()) {
                    carouselNewsList.clear();
                    // Take top 3 news
                    for (int i = 0; i < Math.min(3, items.size()); i++) {
                        carouselNewsList.add(items.get(i));
                    }
                    newsCarouselAdapter.setItems(carouselNewsList);
                    setupCarouselAutoScroll();
                    setupDots(carouselNewsList.size());
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Carousel news fetch error: " + message);
            }
        });
    }

    private void setupCarouselAutoScroll() {
        if (carouselTimer != null) carouselTimer.cancel();
        
        carouselTimer = new Timer();
        carouselTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                carouselHandler.post(() -> {
                    if (newsViewPager != null && newsCarouselAdapter != null && newsCarouselAdapter.getItemCount() > 0) {
                        int nextItem = (newsViewPager.getCurrentItem() + 1) % newsCarouselAdapter.getItemCount();
                        newsViewPager.setCurrentItem(nextItem, true);
                    }
                });
            }
        }, 4000, 4000); // Change every 4 seconds
    }

    private void setupDots(int count) {
        android.widget.LinearLayout dotsContainer = findViewById(R.id.carousel_dots_container);
        if (dotsContainer == null) return;
        dotsContainer.removeAllViews();

        ImageView[] dots = new ImageView[count];
        for (int i = 0; i < count; i++) {
            dots[i] = new ImageView(this);
            dots[i].setImageResource(i == 0 ? R.drawable.dot_active : R.drawable.dot_inactive);
            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(8, 0, 8, 0);
            dotsContainer.addView(dots[i], params);
        }

        newsViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                for (int i = 0; i < count; i++) {
                    dots[i].setImageResource(i == position ? R.drawable.dot_active : R.drawable.dot_inactive);
                }
            }
        });
    }

    private void openNewsDetail(NewsItem item) {
        Intent intent = new Intent(this, NewsDetailActivity.class);
        intent.putExtra(NewsDetailActivity.EXTRA_HEADLINE, item.getHeadline());
        intent.putExtra(NewsDetailActivity.EXTRA_DESCRIPTION, item.getDescription());
        intent.putExtra(NewsDetailActivity.EXTRA_FULL_TEXT, item.getFullText());
        intent.putExtra(NewsDetailActivity.EXTRA_LOCATION, item.getLocation());
        intent.putExtra(NewsDetailActivity.EXTRA_PUBLISHED_AT, item.getPublishedAt());
        intent.putExtra(NewsDetailActivity.EXTRA_IMAGE_URL, item.getImageUrl());
        intent.putExtra(NewsDetailActivity.EXTRA_IMAGE_RES_ID, item.getImageResId());
        startActivity(intent);
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
                .setTitle(" Send SOS Alert?")
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
        filter.addAction(ACTION_MESH_MESSAGE_RECEIVED);
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