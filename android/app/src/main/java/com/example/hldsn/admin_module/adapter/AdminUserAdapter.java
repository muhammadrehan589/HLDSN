package com.example.hldsn.admin_module.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminUserAdapter extends RecyclerView.Adapter<AdminUserAdapter.UserViewHolder> {

    public interface OnUserDeleteClickListener {
        void onDelete(DocumentSnapshot snapshot);
    }

    private final List<DocumentSnapshot> fullList = new ArrayList<>();
    private final List<DocumentSnapshot> filteredList = new ArrayList<>();
    private final OnUserDeleteClickListener deleteClickListener;

    private String currentQuery = "";
    private String currentRoleFilter = "all";

    public AdminUserAdapter(OnUserDeleteClickListener deleteClickListener) {
        this.deleteClickListener = deleteClickListener;
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

    public void setRoleFilter(String roleFilter) {
        currentRoleFilter = roleFilter == null ? "all" : roleFilter.trim().toLowerCase(Locale.US);
        applyFilters();
    }

    private void applyFilters() {
        filteredList.clear();

        for (DocumentSnapshot snapshot : fullList) {
            String role = safe(snapshot.getString("role")).toLowerCase(Locale.US);
            boolean roleMatches = "all".equals(currentRoleFilter) || role.equals(currentRoleFilter);
            if (!roleMatches) {
                continue;
            }

            if (!currentQuery.isEmpty()) {
                String fullName = buildDisplayName(snapshot);
                String searchable = (
                        fullName + " " +
                        safe(snapshot.getString("email")) + " " +
                        safe(snapshot.getString("mobile")) + " " +
                        role
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
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        DocumentSnapshot snapshot = filteredList.get(position);
        String role = safe(snapshot.getString("role"));

        holder.userName.setText(buildDisplayName(snapshot));
        holder.userEmail.setText("Email: " + firstNonBlank(snapshot.getString("email"), "-"));
        holder.userMobile.setText("Mobile: " + firstNonBlank(snapshot.getString("mobile"), "-"));
        holder.userRole.setText("Role: " + (role.isEmpty() ? "user" : role));

        holder.deleteButton.setOnClickListener(v -> {
            if (deleteClickListener != null) {
                deleteClickListener.onDelete(snapshot);
            }
        });
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        private final TextView userName;
        private final TextView userEmail;
        private final TextView userMobile;
        private final TextView userRole;
        private final MaterialButton deleteButton;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            userName = itemView.findViewById(R.id.userNameText);
            userEmail = itemView.findViewById(R.id.userEmailText);
            userMobile = itemView.findViewById(R.id.userMobileText);
            userRole = itemView.findViewById(R.id.userRoleText);
            deleteButton = itemView.findViewById(R.id.deleteUserButton);
        }
    }

    private String buildDisplayName(DocumentSnapshot snapshot) {
        String fullName = safe(snapshot.getString("name"));
        if (!fullName.isEmpty()) {
            return fullName;
        }

        String firstName = safe(snapshot.getString("firstName"));
        String lastName = safe(snapshot.getString("lastName"));
        String joined = (firstName + " " + lastName).trim();
        return joined.isEmpty() ? "Unnamed User" : joined;
    }

    private String firstNonBlank(String value, String fallback) {
        return safe(value).isEmpty() ? fallback : safe(value);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
