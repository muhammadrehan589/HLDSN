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

import java.util.ArrayList;
import java.util.List;

public class NgoNotificationsActivity extends AppCompatActivity {

    private NotificationAdapter notificationAdapter;
    private RecyclerView notificationRecyclerView;
    private View emptyStateLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ngo_notifications);

        ImageView backButton = findViewById(R.id.backButton);
        notificationRecyclerView = findViewById(R.id.notificationRecyclerView);
        emptyStateLayout = findViewById(R.id.empty_state_layout);
        TextView screenTitle = findViewById(R.id.screenTitle);

        if (screenTitle != null) {
            screenTitle.setText("SOS Notifications");
        }

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        notificationRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        notificationAdapter = new NotificationAdapter(this, this::clearNotification);
        notificationRecyclerView.setAdapter(notificationAdapter);
        attachSwipeToRevealClear();

        loadAlerts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAlerts();
    }

    private void loadAlerts() {
        List<SosAlertRecord> alerts = SosAlertStore.getAlerts(this);
        List<NotificationItem> items = new ArrayList<>();
        for (SosAlertRecord alert : alerts) {
            items.add(NotificationItem.fromSosAlert(alert));
        }

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
        SosAlertStore.removeAlertById(this, item.getId());
        loadAlerts();
    }
}