package com.example.hldsn.notification_module;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.example.hldsn.notification_module.SosAlertRecord;
import com.example.hldsn.notification_module.SosAlertStore;

public class VolunteerNotificationsActivity extends AppCompatActivity {

    private static final String PREFS_NOTIFICATION = "notification_prefs";
    private static final String PREF_KEY_LAST_OPENED_NOTIFICATIONS_AT = "last_opened_notifications_at";

    private final List<NotificationItem> communityNotifications = new ArrayList<>();
    private final List<NotificationItem> recommendedNotifications = new ArrayList<>();

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private RecyclerView notificationRecyclerView;
    private NotificationAdapter notificationAdapter;
    private ListenerRegistration notificationsListener;
    private String currentUserId = "";
    private String currentTab = "community";
    private View emptyStateLayout;
    private View tabIndicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);
        markNotificationsOpenedNow();

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        ImageView backButton = findViewById(R.id.backButton);
        TextView screenTitle = findViewById(R.id.screenTitle);
        MaterialButton tabGeneral = findViewById(R.id.tabGeneral);
        MaterialButton tabRecommended = findViewById(R.id.tabRecommended);
        notificationRecyclerView = findViewById(R.id.notificationRecyclerView);
        emptyStateLayout = findViewById(R.id.empty_state_layout);
        tabIndicator = findViewById(R.id.tabIndicator);

        if (screenTitle != null) {
            screenTitle.setText("Notifications");
        }

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        notificationRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        notificationAdapter = new NotificationAdapter(this, this::clearNotification, new NotificationAdapter.OnTaskActionListener() {
            @Override
            public void onAccept(NotificationItem item) {
                acceptTask(item);
            }

            @Override
            public void onReject(NotificationItem item) {
                rejectTask(item);
            }
        });
        notificationRecyclerView.setAdapter(notificationAdapter);
        attachSwipeToRevealClear();

        if (tabGeneral != null) {
            tabGeneral.setText("Community");
            tabGeneral.setOnClickListener(v -> {
                currentTab = "community";
                updateTabStyles(tabGeneral, tabRecommended);
                moveIndicatorToTab(tabGeneral);
                refreshList();
            });
        }

        if (tabRecommended != null) {
            tabRecommended.setText("Recommended");
            tabRecommended.setOnClickListener(v -> {
                currentTab = "recommended";
                updateTabStyles(tabGeneral, tabRecommended);
                moveIndicatorToTab(tabRecommended);
                refreshList();
            });
        }

        updateTabStyles(tabGeneral, tabRecommended);
        // Position indicator under the initially-selected (community) tab once laid out
        if (tabGeneral != null) {
            tabGeneral.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            tabGeneral.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            moveIndicatorToTab(tabGeneral);
                        }
                    });
        }
        loadCurrentUser();
    }

    @Override
    protected void onResume() {
        super.onResume();
        markNotificationsOpenedNow();
        refreshList();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (notificationsListener != null) {
            notificationsListener.remove();
            notificationsListener = null;
        }
    }

    private void loadCurrentUser() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            finish();
            return;
        }

        currentUserId = user.getUid();
        listenForUserNotifications();
    }

    private void listenForUserNotifications() {
        if (currentUserId.isEmpty()) {
            return;
        }

        if (notificationsListener != null) {
            notificationsListener.remove();
        }

        notificationsListener = UserNotificationStore.observeNotificationsForUser(
                db,
                currentUserId,
                (snapshot, error) -> {
                    if (error != null) {
                        return;
                    }

                    communityNotifications.clear();
                    recommendedNotifications.clear();

                    if (snapshot != null) {
                        for (com.google.firebase.firestore.DocumentSnapshot documentSnapshot : snapshot.getDocuments()) {
                            NotificationItem item = NotificationItem.fromUserNotification(documentSnapshot);
                            // All Firestore user notifications go to Recommended tab
                            // (community tab only shows local SOS alerts and incident reports)
                            recommendedNotifications.add(item);
                        }
                    }

                    communityNotifications.addAll(loadSosAlerts());
                    communityNotifications.sort(Comparator.comparingLong(NotificationItem::getTimestampMs).reversed());
                    recommendedNotifications.sort(Comparator.comparingLong(NotificationItem::getTimestampMs).reversed());
                    refreshList();
                }
        );
    }

    private List<NotificationItem> loadSosAlerts() {
        List<NotificationItem> items = new ArrayList<>();
        for (SosAlertRecord alert : SosAlertStore.getAlerts(this)) {
            items.add(NotificationItem.fromSosAlert(alert));
        }
        return items;
    }

    private void refreshList() {
        List<NotificationItem> items = "recommended".equals(currentTab)
                ? new ArrayList<>(recommendedNotifications)
                : new ArrayList<>(communityNotifications);

        notificationAdapter.updateList(items);
        boolean isEmpty = items.isEmpty();
        notificationRecyclerView.setVisibility(isEmpty ? android.view.View.GONE : android.view.View.VISIBLE);
        if (emptyStateLayout != null) {
            emptyStateLayout.setVisibility(isEmpty ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    private void clearNotification(NotificationItem item) {
        if (item == null) {
            return;
        }

        if (item.isSosAlert()) {
            SosAlertStore.removeAlertById(this, item.getId());
        } else {
            UserNotificationStore.deleteNotificationById(db, item.getId());
        }
        refreshList();
    }

    private void acceptTask(NotificationItem item) {
        if (item == null || item.getTaskId().isEmpty()) {
            return;
        }

        UserNotificationStore.respondToTaskAssignment(db, item.getId(), item.getTaskId(), currentUserId, true, "");
        refreshList();
    }

    private void rejectTask(NotificationItem item) {
        if (item == null || item.getTaskId().isEmpty()) {
            return;
        }

        EditText reasonField = new EditText(this);
        reasonField.setHint("Reason for rejecting");
        reasonField.setMinLines(3);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Reject Task")
                .setView(reasonField)
                .setPositiveButton("Reject", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String reason = reasonField.getText() == null ? "" : reasonField.getText().toString().trim();
            if (reason.isEmpty()) {
                reasonField.setError("Required");
                reasonField.requestFocus();
                return;
            }

            UserNotificationStore.respondToTaskAssignment(db, item.getId(), item.getTaskId(), currentUserId, false, reason);
            dialog.dismiss();
            refreshList();
        }));

        dialog.show();
    }

    private void attachSwipeToRevealClear() {
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    notificationAdapter.setSwipedPosition(position);
                }
            }
        };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(notificationRecyclerView);
    }

    private void updateTabStyles(MaterialButton tabGeneral, MaterialButton tabRecommended) {
        if (tabGeneral == null || tabRecommended == null) {
            return;
        }

        boolean communitySelected = "community".equals(currentTab);
        tabGeneral.setTextColor(communitySelected ? 0xFFFFFFFF : 0xFF9EDCC6);
        tabRecommended.setTextColor(communitySelected ? 0xFF9EDCC6 : 0xFFFFFFFF);
    }

    /**
     * Animates the tab indicator to slide beneath the given tab button.
     * The indicator is centred horizontally under the tab.
     */
    private void moveIndicatorToTab(View tab) {
        if (tabIndicator == null || tab == null) return;

        // Target X = tab's left edge + centre offset so the indicator is centred
        float targetX = tab.getLeft() + (tab.getWidth() / 2f) - (tabIndicator.getWidth() / 2f);

        ObjectAnimator animator = ObjectAnimator.ofFloat(tabIndicator, "translationX", targetX);
        animator.setDuration(220);
        animator.start();
    }

    private void markNotificationsOpenedNow() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NOTIFICATION, MODE_PRIVATE);
        prefs.edit().putLong(PREF_KEY_LAST_OPENED_NOTIFICATIONS_AT, System.currentTimeMillis()).apply();
    }
}