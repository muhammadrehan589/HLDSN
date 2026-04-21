package com.example.hldsn.admin_module.adapter;

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

public class AdminNgoRequestAdapter extends RecyclerView.Adapter<AdminNgoRequestAdapter.RequestViewHolder> {

    public interface RequestActionListener {
        void onApprove(DocumentSnapshot snapshot);
        void onReject(DocumentSnapshot snapshot);
    }

    private final List<DocumentSnapshot> fullList = new ArrayList<>();
    private final List<DocumentSnapshot> filteredList = new ArrayList<>();
    private final RequestActionListener actionListener;

    private String currentQuery = "";
    private String currentStatusFilter = "all";

    public AdminNgoRequestAdapter(RequestActionListener actionListener) {
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

    public void setStatusFilter(String statusFilter) {
        currentStatusFilter = statusFilter == null ? "all" : statusFilter.trim().toLowerCase(Locale.US);
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
                        safe(snapshot.getString("ngoName")) + " " +
                        safe(snapshot.getString("requesterEmail")) + " " +
                        safe(snapshot.getString("city")) + " " +
                        safe(snapshot.getString("registrationNumber")) + " " +
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
    public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_ngo_request, parent, false);
        return new RequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
        DocumentSnapshot snapshot = filteredList.get(position);
        String status = firstNonBlank(snapshot.getString("status"), "pending").toLowerCase(Locale.US);

        holder.ngoName.setText(firstNonBlank(snapshot.getString("ngoName"), "Unnamed NGO"));
        holder.requesterEmail.setText("Requester: " + firstNonBlank(snapshot.getString("requesterEmail"), "-"));
        holder.registrationNo.setText("Reg #: " + firstNonBlank(snapshot.getString("registrationNumber"), "-"));
        holder.city.setText("City: " + firstNonBlank(snapshot.getString("city"), "-"));
        holder.status.setText("Status: " + status);

        Timestamp submittedAt = snapshot.getTimestamp("submittedAt");
        holder.submittedAt.setText("Submitted: " + formatDate(submittedAt));

        String rejectionReason = safe(snapshot.getString("rejectionReason"));
        if ("rejected".equals(status) && !rejectionReason.isEmpty()) {
            holder.rejectionReason.setVisibility(View.VISIBLE);
            holder.rejectionReason.setText("Reason: " + rejectionReason);
        } else {
            holder.rejectionReason.setVisibility(View.GONE);
            holder.rejectionReason.setText("");
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

    static class RequestViewHolder extends RecyclerView.ViewHolder {
        private final TextView ngoName;
        private final TextView requesterEmail;
        private final TextView registrationNo;
        private final TextView city;
        private final TextView status;
        private final TextView submittedAt;
        private final TextView rejectionReason;
        private final MaterialButton approveButton;
        private final MaterialButton rejectButton;

        RequestViewHolder(@NonNull View itemView) {
            super(itemView);
            ngoName = itemView.findViewById(R.id.requestNgoNameText);
            requesterEmail = itemView.findViewById(R.id.requesterEmailText);
            registrationNo = itemView.findViewById(R.id.requestRegistrationText);
            city = itemView.findViewById(R.id.requestCityText);
            status = itemView.findViewById(R.id.requestStatusText);
            submittedAt = itemView.findViewById(R.id.requestSubmittedAtText);
            rejectionReason = itemView.findViewById(R.id.requestRejectionReasonText);
            approveButton = itemView.findViewById(R.id.approveNgoRequestButton);
            rejectButton = itemView.findViewById(R.id.rejectNgoRequestButton);
        }
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
