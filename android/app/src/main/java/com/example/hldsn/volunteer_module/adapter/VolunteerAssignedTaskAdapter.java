package com.example.hldsn.volunteer_module.adapter;

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

public class VolunteerAssignedTaskAdapter extends RecyclerView.Adapter<VolunteerAssignedTaskAdapter.TaskViewHolder> {

    public interface OnMarkDoneListener {
        void onMarkDone(DocumentSnapshot task);
    }

    private final List<DocumentSnapshot> fullList = new ArrayList<>();
    private final List<DocumentSnapshot> filteredList = new ArrayList<>();
    private final OnMarkDoneListener listener;
    private String currentStatusFilter = "all";

    public VolunteerAssignedTaskAdapter(OnMarkDoneListener listener) {
        this.listener = listener;
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
            if (matches) {
                filteredList.add(task);
            }
        }
        notifyDataSetChanged();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_volunteer_assigned_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        holder.bind(filteredList.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        private final TextView taskTitleText;
        private final TextView ngoNameText;
        private final TextView taskStatusText;
        private final TextView taskDescriptionText;
        private final TextView taskCreatedAtText;
        private final TextView taskDeadlineText;
        private final MaterialButton completeButton;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            taskTitleText = itemView.findViewById(R.id.taskTitleText);
            ngoNameText = itemView.findViewById(R.id.taskNgoText);
            taskStatusText = itemView.findViewById(R.id.taskStatusText);
            taskDescriptionText = itemView.findViewById(R.id.taskDescriptionText);
            taskCreatedAtText = itemView.findViewById(R.id.taskCreatedAtText);
            taskDeadlineText = itemView.findViewById(R.id.taskDeadlineText);
            completeButton = itemView.findViewById(R.id.completeTaskButton);
        }

        void bind(DocumentSnapshot task, OnMarkDoneListener listener) {
            String status = safe(task.getString("status"));
            taskTitleText.setText(firstNonBlank(task.getString("taskTitle"), "Assigned Task"));
            ngoNameText.setText("NGO: " + firstNonBlank(task.getString("ngoName"), "Unknown NGO"));
            taskStatusText.setText("Status: " + firstNonBlank(status, "pending"));
            taskDescriptionText.setText(firstNonBlank(task.getString("taskDescription"), "-"));
            taskCreatedAtText.setText("Created: " + formatDate(task.getTimestamp("createdAt")));

            Timestamp deadline = task.getTimestamp("deadline");
            if (deadline != null && taskDeadlineText != null) {
                taskDeadlineText.setVisibility(View.VISIBLE);
                String deadlineStr = "Deadline: " + formatDate(deadline);
                taskDeadlineText.setText(deadlineStr);
                boolean overdue = deadline.toDate().before(new java.util.Date())
                        && !"completed".equalsIgnoreCase(status);
                taskDeadlineText.setTextColor(overdue ? 0xFFCC0000 : 0xFFCC5500);
            } else if (taskDeadlineText != null) {
                taskDeadlineText.setVisibility(View.GONE);
            }

            boolean canComplete = "ongoing".equalsIgnoreCase(status);
            completeButton.setVisibility(canComplete ? View.VISIBLE : View.GONE);
            completeButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMarkDone(task);
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
