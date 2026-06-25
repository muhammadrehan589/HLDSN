package com.example.hldsn.ngo_module;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;
import com.example.hldsn.home.HomePageActivity;
import com.example.hldsn.incident_report_module.ChatsActivity;
import com.example.hldsn.login_module.LoginActivity;
import com.example.hldsn.login_module.UserProfileActivity;
import com.example.hldsn.notification_module.SosAlertStore;
import com.example.hldsn.notification_module.UserNotificationStore;
import com.example.hldsn.volunteer_module.CampLocationsMapActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NgoDashboardActivity extends AppCompatActivity {

    private static final String TAG = "NgoDashboard";
    private TextView ngoNameText;
    private TextView notificationCountText;
    private TextView tvVolunteerCount, tvResourceCount, tvCampCount;
    private TextView tvVolunteerRequestCount, tvRegisteredVolunteerCount, tvAssignedTaskCount, tvResourceItemCount, tvActiveCampCount;
    private TextView tvVolunteerTrendLabel, tvResourceTrendLabel, tvCampTrendLabel;
    private TextView tvCompletionPercent, tvOpsCompleteCount, tvSyncStatus;
    private ProgressBar pbOverallCompletion;
    private LinearLayout recentActivityContainer;
    private View offlineBanner;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String currentUserId = "";
    private String currentNgoId = "";
    private int userNotificationCount = 0;
    private ListenerRegistration userNotificationListener;
    private ListenerRegistration ngoProfileListener;
    private ListenerRegistration recentActivityListener;
    private final List<ListenerRegistration> statsListeners = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ngo_dashboard);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        initViews();
        setupClickListeners();

        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
            listenForUserNotifications();
            loadNgoData();
        }

        updateNotificationBadge();
    }

    private void initViews() {
        notificationCountText = findViewById(R.id.tvNotificationCount);
        ngoNameText = findViewById(R.id.ngoDashboardNameText);

        // Stats Overview
        tvVolunteerCount = findViewById(R.id.tvVolunteerCount);
        tvResourceCount = findViewById(R.id.tvTotalResourceCount);
        tvCampCount = findViewById(R.id.tvCampCount);

        // Quick Action Counts
        tvVolunteerRequestCount = findViewById(R.id.tvVolunteerRequestCount);
        tvRegisteredVolunteerCount = findViewById(R.id.tvRegisteredVolunteerCount);
        tvAssignedTaskCount = findViewById(R.id.tvAssignedTaskCount);
        tvResourceItemCount = findViewById(R.id.tvResourceItemCount);
        tvActiveCampCount = findViewById(R.id.tvActiveCampCount);

        // Trends
        tvVolunteerTrendLabel = findViewById(R.id.tvVolunteerTrendLabel);
        tvResourceTrendLabel = findViewById(R.id.tvResourceTrendLabel);
        tvCampTrendLabel = findViewById(R.id.tvCampTrendLabel);

        // Completion UI
        tvOpsCompleteCount = findViewById(R.id.tvOpsCompleteCount);
        tvCompletionPercent = findViewById(R.id.tvCompletionPercent);
        pbOverallCompletion = findViewById(R.id.pbOverallCompletion);
        
        recentActivityContainer = findViewById(R.id.recentActivityContainer);
        
        offlineBanner = findViewById(R.id.offlineBanner);
        tvSyncStatus = findViewById(R.id.tvSyncStatus);
        findViewById(R.id.closeBanner).setOnClickListener(v -> offlineBanner.setVisibility(View.GONE));

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                // Since everything is now live listeners, we just re-trigger the initial fetch
                // to make sure nothing is missed, though listeners should handle it.
                loadNgoData();
                new android.os.Handler().postDelayed(() -> swipeRefreshLayout.setRefreshing(false), 1500);
            });
            swipeRefreshLayout.setColorSchemeColors(0xFF00D1B2, 0xFF11998E);
        }
    }

    private void setupClickListeners() {
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.notificationIcon).setOnClickListener(v ->
                startActivity(new Intent(this, NgoNotificationsActivity.class)));

        findViewById(R.id.manageVolunteerRequestsButton).setOnClickListener(v ->
                startActivity(new Intent(this, NgoVolunteerApprovalsActivity.class)));
        findViewById(R.id.viewVolunteersButton).setOnClickListener(v ->
                startActivity(new Intent(this, NgoViewVolunteersActivity.class)));
        findViewById(R.id.createTaskButton).setOnClickListener(v ->
                startActivity(new Intent(this, NgoTaskCreationActivity.class)));
        findViewById(R.id.viewAssignedTasksButton).setOnClickListener(v ->
                startActivity(new Intent(this, NgoTaskListActivity.class)));
        findViewById(R.id.addResourceButton).setOnClickListener(v ->
                startActivity(new Intent(this, NgoResourceCreationActivity.class)));
        findViewById(R.id.viewResourcesButton).setOnClickListener(v ->
                startActivity(new Intent(this, NgoResourceListActivity.class)));
        findViewById(R.id.addCampCenterButton).setOnClickListener(v ->
                startActivity(new Intent(this, NgoCampCenterFormActivity.class)));
        findViewById(R.id.manageCampCenterButton).setOnClickListener(v ->
                startActivity(new Intent(this, NgoCampCenterManagementActivity.class)));

        findViewById(R.id.ngoLogoutButton).setOnClickListener(v -> {
            auth.signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        // Bottom Navigation Click Listeners
        findViewById(R.id.navHome).setOnClickListener(v -> {
            // Already on Dashboard Home, scroll to top
            View scrollView = findViewById(R.id.pbOverallCompletion); // Using a view near top
            if (scrollView != null) {
                scrollView.getParent().requestChildFocus(scrollView, scrollView);
            }
        });

        findViewById(R.id.navMap).setOnClickListener(v ->
                startActivity(new Intent(this, CampLocationsMapActivity.class)));

        findViewById(R.id.navAlerts).setOnClickListener(v ->
                startActivity(new Intent(this, NgoNotificationsActivity.class)));

        findViewById(R.id.navMessages).setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatsActivity.class);
            if (currentNgoId != null && !currentNgoId.isEmpty()) {
                intent.putExtra(ChatsActivity.EXTRA_VOLUNTEER_ONLY, true);
                intent.putExtra(ChatsActivity.EXTRA_NGO_ID, currentNgoId);
            }
            startActivity(intent);
        });

        findViewById(R.id.navProfile).setOnClickListener(v ->
                startActivity(new Intent(this, UserProfileActivity.class)));
    }

    private void loadNgoData() {
        if (ngoProfileListener != null) ngoProfileListener.remove();
        ngoProfileListener = db.collection("users").document(currentUserId)
                .addSnapshotListener((documentSnapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error listening for NGO profile", e);
                        return;
                    }
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        String ngoName = documentSnapshot.getString("ngoName");
                        String newNgoId = documentSnapshot.getString("ngoId");
                        if (ngoName != null) ngoNameText.setText(ngoName);
                        
                        if (newNgoId != null && !newNgoId.isEmpty() && !newNgoId.equals(currentNgoId)) {
                            currentNgoId = newNgoId;
                            startLiveStatsListener();
                            fetchRecentActivity();
                        }

                        if (tvSyncStatus != null) {
                            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.US);
                            tvSyncStatus.setText("Data synchronized at " + sdf.format(new Date()));
                        }
                    }
                });
    }

    private void startLiveStatsListener() {
        for (ListenerRegistration reg : statsListeners) if (reg != null) reg.remove();
        statsListeners.clear();

        // 1. Volunteers (USERS)
        statsListeners.add(db.collection("users")
                .whereEqualTo("role", "volunteer")
                .whereEqualTo("volunteerNgoId", currentNgoId)
                .addSnapshotListener((value, error) -> {
                    if (value != null) {
                        int count = value.size();
                        if (tvVolunteerCount != null) tvVolunteerCount.setText(String.valueOf(count));
                        if (tvRegisteredVolunteerCount != null) tvRegisteredVolunteerCount.setText(String.format(Locale.US, "%d active members", count));
                        if (tvVolunteerTrendLabel != null) tvVolunteerTrendLabel.setText(String.format(Locale.US, "+%d", count));
                    }
                }));

        // 2. Pending Requests (APPLICATIONS)
        statsListeners.add(db.collection("volunteer_applications")
                .whereEqualTo("ngoId", currentNgoId)
                .whereEqualTo("status", "pending")
                .addSnapshotListener((value, error) -> {
                    if (value != null) {
                        int count = value.size();
                        if (tvVolunteerRequestCount != null) tvVolunteerRequestCount.setText(String.format(Locale.US, "%d pending approvals", count));
                    }
                }));

        // 3. Camps (CAMP_CENTER_LOCATIONS)
        statsListeners.add(db.collection("camp_center_locations")
                .whereEqualTo("ngoId", currentNgoId)
                .addSnapshotListener((value, error) -> {
                    if (value != null) {
                        int count = value.size();
                        if (tvCampCount != null) tvCampCount.setText(String.valueOf(count));
                        if (tvActiveCampCount != null) tvActiveCampCount.setText(String.format(Locale.US, "%d active locations", count));
                        if (tvCampTrendLabel != null) tvCampTrendLabel.setText(String.format(Locale.US, "+%d", count));
                    }
                }));

        // 4. Resources (NGO_RESOURCES)
        statsListeners.add(db.collection("ngo_resources")
                .whereEqualTo("ngoId", currentNgoId)
                .addSnapshotListener((value, error) -> {
                    if (value != null) {
                        int docCount = value.size();
                        if (tvResourceCount != null) tvResourceCount.setText(formatCount(docCount));
                        if (tvResourceItemCount != null) tvResourceItemCount.setText(String.format(Locale.US, "%d items tracked", docCount));
                        if (tvResourceTrendLabel != null) tvResourceTrendLabel.setText(String.format(Locale.US, "+%d", docCount));
                    }
                }));

        // 5. Tasks & Completion (NGO_TASKS)
        statsListeners.add(db.collection("ngo_tasks")
                .whereEqualTo("ngoId", currentNgoId)
                .addSnapshotListener((value, error) -> {
                    if (value != null) {
                        int total = value.size();
                        int completedCount = 0;
                        int pending = 0;
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            if ("completed".equalsIgnoreCase(doc.getString("status"))) completedCount++;
                            else pending++;
                        }
                        if (tvAssignedTaskCount != null) tvAssignedTaskCount.setText(String.format(Locale.US, "%d tasks in progress", pending));
                        if (total > 0) {
                            int percent = (completedCount * 100) / total;
                            if (tvCompletionPercent != null) tvCompletionPercent.setText(String.format(Locale.US, "%d%%", percent));
                            if (tvOpsCompleteCount != null) tvOpsCompleteCount.setText(String.format(Locale.US, "%d%%", percent));
                            if (pbOverallCompletion != null) pbOverallCompletion.setProgress(percent);
                        }
                    }
                }));
    }

    private void fetchRecentActivity() {
        if (recentActivityListener != null) recentActivityListener.remove();
        // Fetch last 3 tasks as "Recent Activity"
        recentActivityListener = db.collection("ngo_tasks")
                .whereEqualTo("ngoId", currentNgoId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(3)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error listening for recent activity", e);
                        return;
                    }
                    if (recentActivityContainer == null || queryDocumentSnapshots == null) return;

                    recentActivityContainer.removeAllViews();
                    if (queryDocumentSnapshots.isEmpty()) {
                        View empty = LayoutInflater.from(this).inflate(R.layout.item_dashboard_empty_activity, recentActivityContainer, false);
                        recentActivityContainer.addView(empty);
                        return;
                    }
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        addActivityItem(doc.getString("title"), doc.getString("description"), doc.getDate("createdAt"));
                    }
                });
    }

    private void addActivityItem(String title, String subtitle, Date date) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity, recentActivityContainer, false);
        TextView tvTitle = view.findViewById(R.id.tvActivityTitle);
        TextView tvSub = view.findViewById(R.id.tvActivitySubtitle);
        TextView tvTime = view.findViewById(R.id.tvActivityTime);
        
        tvTitle.setText(title != null ? title : "Operation Updated");
        tvSub.setText(subtitle != null ? subtitle : "NGO management update");
        
        if (date != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.US);
            tvTime.setText(sdf.format(date));
        } else {
            tvTime.setText("Just now");
        }
        
        recentActivityContainer.addView(view);
    }

    private String formatCount(int count) {
        if (count >= 1000) return String.format(Locale.US, "%.1fk", count / 1000.0);
        return String.valueOf(count);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (userNotificationListener != null) userNotificationListener.remove();
        if (ngoProfileListener != null) ngoProfileListener.remove();
        if (recentActivityListener != null) recentActivityListener.remove();
        for (ListenerRegistration reg : statsListeners) if (reg != null) reg.remove();
        statsListeners.clear();
    }

    private void updateNotificationBadge() {
        if (notificationCountText == null) return;
        int count = SosAlertStore.getUnseenCount(this) + userNotificationCount;
        notificationCountText.setVisibility(count <= 0 ? View.GONE : View.VISIBLE);
        notificationCountText.setText(count >= 10 ? "10+" : String.valueOf(count));
    }

    private void listenForUserNotifications() {
        if (currentUserId == null || currentUserId.isEmpty()) return;
        if (userNotificationListener != null) userNotificationListener.remove();
        userNotificationListener = UserNotificationStore.observeNotificationsForUser(db, currentUserId, (snapshot, error) -> {
            if (error == null) {
                userNotificationCount = snapshot == null ? 0 : snapshot.size();
                updateNotificationBadge();
            }
        });
    }
}
