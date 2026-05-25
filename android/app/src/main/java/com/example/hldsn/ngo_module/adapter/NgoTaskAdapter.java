package com.example.hldsn.ngo_module.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NgoTaskAdapter extends RecyclerView.Adapter<NgoTaskAdapter.TaskViewHolder> {

    public interface TaskCompleteListener {
        void onMarkCompleted(String taskId);
    }

    private final List<DocumentSnapshot> fullList = new ArrayList<>();
    private final List<DocumentSnapshot> filteredList = new ArrayList<>();
    private final TaskCompleteListener completeListener;
    private String currentStatusFilter = "all";

    public NgoTaskAdapter(TaskCompleteListener completeListener) {
        this.completeListener = completeListener;
    }

    public void submitList(List<DocumentSnapshot> items) {
        fullList.clear();
        if (items != null) {
            fullList.addAll(items);
        }
        applyFilter();
    }

    public void setStatusFilter(String filter) {
        currentStatusFilter = filter == null ? "all" : filter.trim().toLowerCase(Locale.US);
        applyFilter();
    }

    private void applyFilter() {
        filteredList.clear();
        for (DocumentSnapshot task : fullList) {
            String status = safe(task.getString("status")).toLowerCase(Locale.US);
            boolean matches = "all".equals(currentStatusFilter) || status.equals(currentStatusFilter);
            if (!matches) {
                continue;
            }
            filteredList.add(task);
        }
        notifyDataSetChanged();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ngo_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        DocumentSnapshot task = filteredList.get(position);
        holder.bind(task, completeListener);
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        private final TextView taskTitleText;
        private final TextView taskVolunteerText;
        private final TextView taskStatusText;
        private final TextView taskDescriptionText;
        private final TextView taskCreatedAtText;
        private final MaterialButton completeButton;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            taskTitleText = itemView.findViewById(R.id.taskTitleText);
            taskVolunteerText = itemView.findViewById(R.id.taskVolunteerText);
            taskStatusText = itemView.findViewById(R.id.taskStatusText);
            taskDescriptionText = itemView.findViewById(R.id.taskDescriptionText);
            taskCreatedAtText = itemView.findViewById(R.id.taskCreatedAtText);
            completeButton = itemView.findViewById(R.id.completeTaskButton);
        }

        void bind(DocumentSnapshot task, TaskCompleteListener completeListener) {
            String status = safe(task.getString("status"));
            taskTitleText.setText(firstNonBlank(task.getString("taskTitle"), "Untitled Task"));
            taskVolunteerText.setText("Assigned to: " + firstNonBlank(task.getString("volunteerName"), "Volunteer"));
            taskStatusText.setText("Status: " + status);
            taskDescriptionText.setText(firstNonBlank(task.getString("taskDescription"), "-"));
            taskCreatedAtText.setText("Created: " + formatDate(task.getTimestamp("createdAt")));

            boolean canComplete = "ongoing".equalsIgnoreCase(status);
            completeButton.setVisibility(canComplete ? View.VISIBLE : View.GONE);
            completeButton.setOnClickListener(v -> {
                if (completeListener != null) {
                    completeListener.onMarkCompleted(task.getId());
                }
            });
        }

        private String firstNonBlank(String value, String fallback) {
            return safe(value).isEmpty() ? fallback : safe(value);
        }

        private String safe(String value) {
            return value == null ? "" : value.trim();
        }

        private String formatDate(Timestamp timestamp) {
            if (timestamp == null) {
                return "-";
            }
            return new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(timestamp.toDate());
        }
    }
}