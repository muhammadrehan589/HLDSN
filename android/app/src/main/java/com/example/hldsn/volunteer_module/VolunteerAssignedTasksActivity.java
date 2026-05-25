package com.example.hldsn.volunteer_module;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.example.hldsn.notification_module.UserNotificationStore;
import com.example.hldsn.volunteer_module.adapter.VolunteerAssignedTaskAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class VolunteerAssignedTasksActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration taskListener;
    private VolunteerAssignedTaskAdapter adapter;
    private RecyclerView taskRecyclerView;
    private TextView emptyStateText;
    private String currentUserId = "";
    private String currentVolunteerName = "";
    private final List<DocumentSnapshot> tasks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_volunteer_assigned_tasks);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        ImageView backButton = findViewById(R.id.backButton);
        Spinner statusSpinner = findViewById(R.id.taskStatusFilterSpinner);
        taskRecyclerView = findViewById(R.id.taskRecyclerView);
        emptyStateText = findViewById(R.id.tasksEmptyText);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        adapter = new VolunteerAssignedTaskAdapter(this::markTaskDone);
        taskRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        taskRecyclerView.setAdapter(adapter);

        if (statusSpinner != null) {
            String[] statuses = new String[] {"All", "Ongoing", "Completed"};
            statusSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, statuses));
            statusSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                    String selected = statuses[position];
                    adapter.setStatusFilter("All".equalsIgnoreCase(selected) ? "all" : selected.toLowerCase());
                    updateEmptyState();
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    adapter.setStatusFilter("all");
                    updateEmptyState();
                }
            });
        }

        loadCurrentUser();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCurrentList();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (taskListener != null) {
            taskListener.remove();
            taskListener = null;
        }
    }

    private void loadCurrentUser() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentUserId = user.getUid();
        db.collection("users")
                .document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    currentVolunteerName = firstNonBlank(documentSnapshot.getString("name"), documentSnapshot.getString("fullName"));
                    listenForTasks();
                })
                .addOnFailureListener(error -> listenForTasks());
    }

    private void listenForTasks() {
        if (currentUserId.isEmpty()) {
            return;
        }

        if (taskListener != null) {
            taskListener.remove();
        }

        taskListener = db.collection("ngo_tasks")
                .whereEqualTo("volunteerUid", currentUserId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Could not load assigned tasks", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    tasks.clear();
                    if (snapshot != null) {
                        tasks.addAll(snapshot.getDocuments());
                        tasks.sort(Comparator.comparingLong(this::getTaskTimestampMs).reversed());
                    }

                    refreshCurrentList();
                });
    }

    private void refreshCurrentList() {
        adapter.submitList(tasks);
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean isEmpty = adapter.getItemCount() == 0;
        taskRecyclerView.setVisibility(isEmpty ? android.view.View.GONE : android.view.View.VISIBLE);
        if (emptyStateText != null) {
            emptyStateText.setVisibility(isEmpty ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    private void markTaskDone(DocumentSnapshot task) {
        if (task == null) {
            return;
        }

        String status = safe(task.getString("status"));
        if (!"ongoing".equalsIgnoreCase(status)) {
            Toast.makeText(this, "Only ongoing tasks can be marked done", Toast.LENGTH_SHORT).show();
            return;
        }

        String taskId = task.getId();
        String recipientUid = safe(task.getString("createdByUid"));
        String ngoId = safe(task.getString("ngoId"));
        String ngoName = firstNonBlank(task.getString("ngoName"), "NGO");
        String taskTitle = firstNonBlank(task.getString("taskTitle"), "Task");
        String volunteerName = firstNonBlank(task.getString("volunteerName"), currentVolunteerName);

        UserNotificationStore.markTaskCompletedAndNotifyNgo(
                db,
                taskId,
                recipientUid,
                ngoId,
                ngoName,
                taskTitle,
                currentUserId,
                volunteerName,
                new UserNotificationStore.OnCompletionListener() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(VolunteerAssignedTasksActivity.this, "Task marked as completed", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(Exception error) {
                        Toast.makeText(VolunteerAssignedTasksActivity.this, "Could not mark task done", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private long getTaskTimestampMs(DocumentSnapshot task) {
        Timestamp timestamp = task.getTimestamp("createdAt");
        return timestamp == null ? 0L : timestamp.toDate().getTime();
    }

    private String firstNonBlank(String primary, String fallback) {
        return safe(primary).isEmpty() ? safe(fallback) : safe(primary);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
