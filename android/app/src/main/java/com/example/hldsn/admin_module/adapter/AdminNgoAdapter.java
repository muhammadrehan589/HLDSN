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

public class AdminNgoAdapter extends RecyclerView.Adapter<AdminNgoAdapter.NgoViewHolder> {

    public interface OnNgoDeleteClickListener {
        void onDelete(DocumentSnapshot snapshot);
    }

    private final List<DocumentSnapshot> fullList = new ArrayList<>();
    private final List<DocumentSnapshot> filteredList = new ArrayList<>();
    private final OnNgoDeleteClickListener deleteClickListener;
    private String currentQuery = "";

    public AdminNgoAdapter(OnNgoDeleteClickListener deleteClickListener) {
        this.deleteClickListener = deleteClickListener;
    }

    public void submitList(List<DocumentSnapshot> items) {
        fullList.clear();
        if (items != null) {
            fullList.addAll(items);
        }
        applyFilter();
    }

    public void filter(String query) {
        currentQuery = query == null ? "" : query.trim().toLowerCase(Locale.US);
        applyFilter();
    }

    private void applyFilter() {
        filteredList.clear();

        if (currentQuery.isEmpty()) {
            filteredList.addAll(fullList);
        } else {
            for (DocumentSnapshot snapshot : fullList) {
                String searchable = (
                        safe(snapshot.getString("name")) + " " +
                        safe(snapshot.getString("city")) + " " +
                        safe(snapshot.getString("registrationNumber")) + " " +
                        safe(snapshot.getString("contactNumber"))
                ).toLowerCase(Locale.US);

                if (searchable.contains(currentQuery)) {
                    filteredList.add(snapshot);
                }
            }
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NgoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_ngo, parent, false);
        return new NgoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NgoViewHolder holder, int position) {
        DocumentSnapshot snapshot = filteredList.get(position);

        holder.ngoName.setText(firstNonBlank(snapshot.getString("name"), "Unknown NGO"));
        holder.ngoCity.setText("City: " + firstNonBlank(snapshot.getString("city"), "-"));
        holder.ngoRegNo.setText("Reg #: " + firstNonBlank(snapshot.getString("registrationNumber"), "-"));
        holder.ngoContact.setText("Contact: " + firstNonBlank(snapshot.getString("contactNumber"), "-"));

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

    static class NgoViewHolder extends RecyclerView.ViewHolder {
        private final TextView ngoName;
        private final TextView ngoCity;
        private final TextView ngoRegNo;
        private final TextView ngoContact;
        private final MaterialButton deleteButton;

        NgoViewHolder(@NonNull View itemView) {
            super(itemView);
            ngoName = itemView.findViewById(R.id.ngoNameText);
            ngoCity = itemView.findViewById(R.id.ngoCityText);
            ngoRegNo = itemView.findViewById(R.id.ngoRegistrationText);
            ngoContact = itemView.findViewById(R.id.ngoContactText);
            deleteButton = itemView.findViewById(R.id.deleteNgoButton);
        }
    }

    private String firstNonBlank(String value, String fallback) {
        return safe(value).isEmpty() ? fallback : safe(value);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
