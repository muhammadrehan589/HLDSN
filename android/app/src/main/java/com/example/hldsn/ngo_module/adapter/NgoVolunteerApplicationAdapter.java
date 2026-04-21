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
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NgoVolunteerApplicationAdapter extends RecyclerView.Adapter<NgoVolunteerApplicationAdapter.VolunteerViewHolder> {

    public interface VolunteerActionListener {
        void onApprove(DocumentSnapshot snapshot);
        void onReject(DocumentSnapshot snapshot);
    }

    private final List<DocumentSnapshot> fullList = new ArrayList<>();
    private final List<DocumentSnapshot> filteredList = new ArrayList<>();
    private final VolunteerActionListener actionListener;

    private String currentQuery = "";
    private String currentStatusFilter = "all";

    public NgoVolunteerApplicationAdapter(VolunteerActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void submitList(List<DocumentSnapshot> items) {
        fullList.clear();
        if (items != null) {
            fullList.addAll(items);
        }
        applyFilters();
    }

    public void setSearchQuery(String query) {
        currentQuery = query == null ? "" : query.trim().toLowerCase(Locale.US);
        applyFilters();
    }

    public void setStatusFilter(String status) {
        currentStatusFilter = status == null ? "all" : status.trim().toLowerCase(Locale.US);
        applyFilters();
    }

    private void applyFilters() {
        filteredList.clear();

        for (DocumentSnapshot snapshot : fullList) {
            String status = safe(snapshot.getString("status")).toLowerCase(Locale.US);
            boolean statusMatches = "all".equals(currentStatusFilter) || status.equals(currentStatusFilter);
            if (!statusMatches) {
                continue;
            }

            if (!currentQuery.isEmpty()) {
                String searchable = (
                        safe(snapshot.getString("firstName")) + " " +
                        safe(snapshot.getString("surname")) + " " +
                        safe(snapshot.getString("email")) + " " +
                        safe(snapshot.getString("phoneNumber")) + " " +
                        safe(snapshot.getString("fullAddress")) + " " +
                        status
                ).toLowerCase(Locale.US);

                if (!searchable.contains(currentQuery)) {
                    continue;
                }
            }

            filteredList.add(snapshot);
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VolunteerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ngo_volunteer_application, parent, false);
        return new VolunteerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VolunteerViewHolder holder, int position) {
        DocumentSnapshot snapshot = filteredList.get(position);

        String firstName = firstNonBlank(snapshot.getString("firstName"), "-");
        String surname = firstNonBlank(snapshot.getString("surname"), "");
        String fullName = (firstName + " " + surname).trim();

        holder.nameText.setText(fullName.isEmpty() ? "Unnamed Volunteer" : fullName);
        holder.emailText.setText("Email: " + firstNonBlank(snapshot.getString("email"), "-"));
        holder.phoneText.setText("Phone: " + firstNonBlank(snapshot.getString("phoneNumber"), "-"));
        holder.skillsText.setText("Skills: " + listAsText(snapshot.get("skills")));

        String status = firstNonBlank(snapshot.getString("status"), "pending").toLowerCase(Locale.US);
        holder.statusText.setText("Status: " + status);

        Timestamp submittedAt = snapshot.getTimestamp("submittedAt");
        holder.submittedAtText.setText("Submitted: " + formatDate(submittedAt));

        String rejectionReason = safe(snapshot.getString("rejectionReason"));
        if ("rejected".equals(status) && !rejectionReason.isEmpty()) {
            holder.rejectionReasonText.setVisibility(View.VISIBLE);
            holder.rejectionReasonText.setText("Reason: " + rejectionReason);
        } else {
            holder.rejectionReasonText.setVisibility(View.GONE);
            holder.rejectionReasonText.setText("");
        }

        boolean isPending = "pending".equals(status);
        holder.approveButton.setVisibility(isPending ? View.VISIBLE : View.GONE);
        holder.rejectButton.setVisibility(isPending ? View.VISIBLE : View.GONE);

        holder.approveButton.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onApprove(snapshot);
            }
        });

        holder.rejectButton.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onReject(snapshot);
            }
        });
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    static class VolunteerViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameText;
        private final TextView emailText;
        private final TextView phoneText;
        private final TextView skillsText;
        private final TextView statusText;
        private final TextView submittedAtText;
        private final TextView rejectionReasonText;
        private final MaterialButton approveButton;
        private final MaterialButton rejectButton;

        VolunteerViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.volunteerNameText);
            emailText = itemView.findViewById(R.id.volunteerEmailText);
            phoneText = itemView.findViewById(R.id.volunteerPhoneText);
            skillsText = itemView.findViewById(R.id.volunteerSkillsText);
            statusText = itemView.findViewById(R.id.volunteerStatusText);
            submittedAtText = itemView.findViewById(R.id.volunteerSubmittedAtText);
            rejectionReasonText = itemView.findViewById(R.id.volunteerRejectionReasonText);
            approveButton = itemView.findViewById(R.id.approveVolunteerButton);
            rejectButton = itemView.findViewById(R.id.rejectVolunteerButton);
        }
    }

    private String listAsText(Object listObject) {
        if (!(listObject instanceof List<?>)) {
            return "-";
        }
        List<?> values = (List<?>) listObject;
        List<String> converted = new ArrayList<>();
        for (Object value : values) {
            if (value != null) {
                String text = value.toString().trim();
                if (!text.isEmpty()) {
                    converted.add(text);
                }
            }
        }
        if (converted.isEmpty()) {
            return "-";
        }
        return android.text.TextUtils.join(", ", converted);
    }

    private String formatDate(Timestamp timestamp) {
        if (timestamp == null) {
            return "-";
        }
        Date date = timestamp.toDate();
        return new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(date);
    }

    private String firstNonBlank(String value, String fallback) {
        return safe(value).isEmpty() ? fallback : safe(value);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
