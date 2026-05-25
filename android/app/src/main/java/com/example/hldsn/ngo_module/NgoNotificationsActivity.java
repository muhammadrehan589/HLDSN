package com.example.hldsn.ngo_module;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.example.hldsn.notification_module.NotificationAdapter;
import com.example.hldsn.notification_module.NotificationItem;
import com.example.hldsn.notification_module.SosAlertRecord;
import com.example.hldsn.notification_module.SosAlertStore;
import com.example.hldsn.notification_module.UserNotificationStore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NgoNotificationsActivity extends AppCompatActivity {

    private NotificationAdapter notificationAdapter;
    private RecyclerView notificationRecyclerView;
    private View emptyStateLayout;
    private final List<NotificationItem> userNotifications = new ArrayList<>();
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String currentUserId = "";
    private ListenerRegistration userNotificationsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ngo_notifications);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        ImageView backButton = findViewById(R.id.backButton);
        notificationRecyclerView = findViewById(R.id.notificationRecyclerView);
        emptyStateLayout = findViewById(R.id.empty_state_layout);
        TextView screenTitle = findViewById(R.id.screenTitle);

        if (screenTitle != null) {
            screenTitle.setText("Notifications");
        }

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        notificationRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        notificationAdapter = new NotificationAdapter(this, this::clearNotification);
        notificationRecyclerView.setAdapter(notificationAdapter);
        attachSwipeToRevealClear();

        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
            listenForUserNotifications();
        }

        refreshNotifications();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshNotifications();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (userNotificationsListener != null) {
            userNotificationsListener.remove();
            userNotificationsListener = null;
        }
    }

    private void refreshNotifications() {
        List<SosAlertRecord> alerts = SosAlertStore.getAlerts(this);
        List<NotificationItem> items = new ArrayList<>();
        for (SosAlertRecord alert : alerts) {
            items.add(NotificationItem.fromSosAlert(alert));
        }

        items.addAll(userNotifications);
        items.sort(Comparator.comparingLong(NotificationItem::getTimestampMs).reversed());

        notificationAdapter.updateList(items);
        if (items.isEmpty()) {
            notificationRecyclerView.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            notificationRecyclerView.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
        }

        SosAlertStore.markAllSeen(this);
    }

    private void listenForUserNotifications() {
        if (currentUserId.isEmpty()) {
            return;
        }

        if (userNotificationsListener != null) {
            userNotificationsListener.remove();
        }

        userNotificationsListener = UserNotificationStore.observeNotificationsForUser(
                db,
                currentUserId,
                (snapshot, error) -> {
                    if (error != null) {
                        return;
                    }

                    userNotifications.clear();
                    if (snapshot != null) {
                        for (com.google.firebase.firestore.DocumentSnapshot documentSnapshot : snapshot.getDocuments()) {
                            userNotifications.add(NotificationItem.fromUserNotification(documentSnapshot));
                        }
                    }
                    refreshNotifications();
                }
        );
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

    private void clearNotification(NotificationItem item) {
        if (item == null) {
            return;
        }
        if (item.isSosAlert()) {
            SosAlertStore.removeAlertById(this, item.getId());
        } else if (item.isUserNotification()) {
            UserNotificationStore.deleteNotificationById(db, item.getId());
        }
        refreshNotifications();
    }
}