package com.example.hldsn.ngo_module;

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
import com.example.hldsn.ngo_module.adapter.NgoTaskAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class NgoTaskListActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private NgoTaskAdapter adapter;
    private ListenerRegistration taskListener;
    private String currentNgoId = "";
    private RecyclerView taskRecyclerView;
    private TextView emptyStateText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ngo_task_list);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        ImageView backButton = findViewById(R.id.backButton);
        Spinner statusSpinner = findViewById(R.id.taskStatusFilterSpinner);
        taskRecyclerView = findViewById(R.id.taskRecyclerView);
        emptyStateText = findViewById(R.id.tasksEmptyText);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        adapter = new NgoTaskAdapter(taskId -> NgoTaskStore.markTaskCompleted(db, taskId));
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
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    adapter.setStatusFilter("all");
                }
            });
        }

        loadNgoContext();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (taskListener != null) {
            taskListener.remove();
            taskListener = null;
        }
    }

    private void loadNgoContext() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    currentNgoId = safe(documentSnapshot.getString("ngoId"));
                    if (currentNgoId.isEmpty()) {
                        Toast.makeText(this, "No NGO assigned to this account", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    listenForTasks();
                })
                .addOnFailureListener(error -> {
                    Toast.makeText(this, "Could not load NGO profile", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void listenForTasks() {
        if (taskListener != null) {
            taskListener.remove();
        }

        taskListener = NgoTaskStore.observeTasksForNgo(db, currentNgoId, (snapshot, error) -> {
            if (error != null) {
                Toast.makeText(this, "Could not load tasks", Toast.LENGTH_SHORT).show();
                return;
            }

            if (snapshot == null) {
                adapter.submitList(java.util.Collections.emptyList());
                if (emptyStateText != null) {
                    emptyStateText.setVisibility(android.view.View.VISIBLE);
                }
                if (taskRecyclerView != null) {
                    taskRecyclerView.setVisibility(android.view.View.GONE);
                }
                return;
            }

            adapter.submitList(snapshot.getDocuments());
            if (emptyStateText != null) {
                emptyStateText.setVisibility(adapter.getItemCount() == 0 ? android.view.View.VISIBLE : android.view.View.GONE);
            }
            if (taskRecyclerView != null) {
                taskRecyclerView.setVisibility(adapter.getItemCount() == 0 ? android.view.View.GONE : android.view.View.VISIBLE);
            }
        });
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}