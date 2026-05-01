package com.example.hldsn.ngo_module.adapter;

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

public class NgoViewVolunteersAdapter extends RecyclerView.Adapter<NgoViewVolunteersAdapter.VolunteerViewHolder> {

    private List<DocumentSnapshot> allVolunteers = new ArrayList<>();
    private List<DocumentSnapshot> filteredVolunteers = new ArrayList<>();
    private String searchQuery = "";
    private VolunteerClickListener listener;

    public interface VolunteerClickListener {
        void onVolunteerClicked(DocumentSnapshot snapshot);
    }

    public NgoViewVolunteersAdapter(VolunteerClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<DocumentSnapshot> volunteers) {
        this.allVolunteers = new ArrayList<>(volunteers);
        applyFilter();
    }

    public void setSearchQuery(String query) {
        this.searchQuery = query.toLowerCase().trim();
        applyFilter();
    }

    private void applyFilter() {
        filteredVolunteers = new ArrayList<>();

        for (DocumentSnapshot volunteer : allVolunteers) {
            String firstName = safe(volunteer.getString("firstName"));
            String surname = safe(volunteer.getString("surname"));
            String fullName = (firstName + " " + surname).toLowerCase();

            if (searchQuery.isEmpty() || fullName.contains(searchQuery)) {
                filteredVolunteers.add(volunteer);
            }
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VolunteerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ngo_volunteer_view, parent, false);
        return new VolunteerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VolunteerViewHolder holder, int position) {
        DocumentSnapshot volunteer = filteredVolunteers.get(position);
        holder.bind(volunteer, listener);
    }

    @Override
    public int getItemCount() {
        return filteredVolunteers.size();
    }

    static class VolunteerViewHolder extends RecyclerView.ViewHolder {

        private TextView volunteerNameText;
        private TextView volunteerEmailText;
        private MaterialButton assignTaskButton;

        public VolunteerViewHolder(@NonNull View itemView) {
            super(itemView);
            volunteerNameText = itemView.findViewById(R.id.volunteerNameText);
            volunteerEmailText = itemView.findViewById(R.id.volunteerEmailText);
            assignTaskButton = itemView.findViewById(R.id.assignTaskButton);
        }

        public void bind(DocumentSnapshot volunteer, VolunteerClickListener listener) {
            String firstName = safe(volunteer.getString("firstName"));
            String surname = safe(volunteer.getString("surname"));
            String email = safe(volunteer.getString("email"));

            if (volunteerNameText != null) {
                volunteerNameText.setText(firstName + " " + surname);
            }

            if (volunteerEmailText != null) {
                volunteerEmailText.setText(email);
            }

            if (assignTaskButton != null) {
                assignTaskButton.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onVolunteerClicked(volunteer);
                    }
                });
            }
        }

        private static String safe(String value) {
            return value == null ? "" : value.trim();
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
