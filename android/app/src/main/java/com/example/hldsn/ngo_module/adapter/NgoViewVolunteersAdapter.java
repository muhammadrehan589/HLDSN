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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class NgoViewVolunteersAdapter extends RecyclerView.Adapter<NgoViewVolunteersAdapter.VolunteerViewHolder> {

    private List<DocumentSnapshot> allVolunteers = new ArrayList<>();
    private List<DocumentSnapshot> filteredVolunteers = new ArrayList<>();
    private String searchQuery = "";
    private VolunteerClickListener assignListener;
    private VolunteerClickListener detailListener;

    public interface VolunteerClickListener {
        void onVolunteerClicked(DocumentSnapshot snapshot);
    }

    public NgoViewVolunteersAdapter(VolunteerClickListener assignListener, VolunteerClickListener detailListener) {
        this.assignListener = assignListener;
        this.detailListener = detailListener;
    }

    public void submitList(List<DocumentSnapshot> volunteers) {
        // Deduplicate by UID — keep only the first application per volunteer
        allVolunteers = new ArrayList<>();
        Set<String> seenUids = new HashSet<>();
        for (DocumentSnapshot doc : volunteers) {
            String uid = safe(doc.getString("uid"));
            if (uid.isEmpty() || seenUids.contains(uid)) {
                continue;
            }
            seenUids.add(uid);
            allVolunteers.add(doc);
        }
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
            String fullName = (firstName + " " + surname).toLowerCase(Locale.US);
            String address = safe(volunteer.getString("fullAddress")).toLowerCase(Locale.US);

            if (searchQuery.isEmpty()
                    || fullName.contains(searchQuery)
                    || address.contains(searchQuery)) {
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
        holder.bind(volunteer, assignListener, detailListener);
    }

    @Override
    public int getItemCount() {
        return filteredVolunteers.size();
    }

    static class VolunteerViewHolder extends RecyclerView.ViewHolder {

        private final TextView volunteerNameText;
        private final TextView volunteerEmailText;
        private final TextView volunteerLocationText;
        private final MaterialButton assignTaskButton;

        public VolunteerViewHolder(@NonNull View itemView) {
            super(itemView);
            volunteerNameText = itemView.findViewById(R.id.volunteerNameText);
            volunteerEmailText = itemView.findViewById(R.id.volunteerEmailText);
            volunteerLocationText = itemView.findViewById(R.id.volunteerLocationText);
            assignTaskButton = itemView.findViewById(R.id.assignTaskButton);
        }

        public void bind(DocumentSnapshot volunteer, VolunteerClickListener assignListener, VolunteerClickListener detailListener) {
            String firstName = safe(volunteer.getString("firstName"));
            String surname = safe(volunteer.getString("surname"));
            String email = safe(volunteer.getString("email"));
            String address = safe(volunteer.getString("fullAddress"));

            if (volunteerNameText != null) {
                volunteerNameText.setText(firstName + " " + surname);
            }

            if (volunteerEmailText != null) {
                volunteerEmailText.setText(email);
            }

            if (volunteerLocationText != null) {
                if (address.isEmpty()) {
                    volunteerLocationText.setVisibility(View.GONE);
                } else {
                    volunteerLocationText.setVisibility(View.VISIBLE);
                    volunteerLocationText.setText("📍 " + address);
                }
            }

            if (assignTaskButton != null) {
                assignTaskButton.setOnClickListener(v -> {
                    if (assignListener != null) {
                        assignListener.onVolunteerClicked(volunteer);
                    }
                });
            }

            // Clicking anywhere on the card (except the button) opens the detail view
            itemView.setOnClickListener(v -> {
                if (detailListener != null) {
                    detailListener.onVolunteerClicked(volunteer);
                }
            });
        }

        private static String safe(String value) {
            return value == null ? "" : value.trim();
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
