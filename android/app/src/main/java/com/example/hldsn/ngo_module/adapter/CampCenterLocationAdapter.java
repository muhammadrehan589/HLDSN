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
import java.util.Locale;

public class CampCenterLocationAdapter extends RecyclerView.Adapter<CampCenterLocationAdapter.LocationViewHolder> {

    public interface CampLocationActionListener {
        void onEdit(DocumentSnapshot snapshot);
        void onDelete(DocumentSnapshot snapshot);
    }

    private final List<DocumentSnapshot> items = new ArrayList<>();
    private final CampLocationActionListener actionListener;

    public CampCenterLocationAdapter(CampLocationActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void submitList(List<DocumentSnapshot> snapshots) {
        items.clear();
        if (snapshots != null) {
            items.addAll(snapshots);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LocationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_camp_center_location, parent, false);
        return new LocationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LocationViewHolder holder, int position) {
        DocumentSnapshot snapshot = items.get(position);

        String type = safe(snapshot.getString("type"));
        String name = firstNonBlank(snapshot.getString("name"), "Unnamed Location");
        String locationText = firstNonBlank(snapshot.getString("locationText"), "No address saved");
        Double latitude = toDouble(snapshot.get("latitude"));
        Double longitude = toDouble(snapshot.get("longitude"));

        holder.typeText.setText(capitalize(type));
        holder.nameText.setText(name);
        holder.locationText.setText(locationText);
        holder.coordinatesText.setText(latitude == null || longitude == null
                ? "Coordinates unavailable"
                : String.format(Locale.US, "%.5f, %.5f", latitude, longitude));

        holder.editButton.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onEdit(snapshot);
            }
        });

        holder.deleteButton.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onDelete(snapshot);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class LocationViewHolder extends RecyclerView.ViewHolder {
        private final TextView typeText;
        private final TextView nameText;
        private final TextView locationText;
        private final TextView coordinatesText;
        private final MaterialButton editButton;
        private final MaterialButton deleteButton;

        LocationViewHolder(@NonNull View itemView) {
            super(itemView);
            typeText = itemView.findViewById(R.id.campLocationTypeText);
            nameText = itemView.findViewById(R.id.campLocationNameText);
            locationText = itemView.findViewById(R.id.campLocationAddressText);
            coordinatesText = itemView.findViewById(R.id.campLocationCoordinatesText);
            editButton = itemView.findViewById(R.id.editCampLocationButton);
            deleteButton = itemView.findViewById(R.id.deleteCampLocationButton);
        }
    }

    private Double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String capitalize(String value) {
        String text = safe(value);
        if (text.isEmpty()) {
            return "Location";
        }
        return text.substring(0, 1).toUpperCase(Locale.US) + text.substring(1).toLowerCase(Locale.US);
    }

    private String firstNonBlank(String value, String fallback) {
        String safeValue = safe(value);
        return safeValue.isEmpty() ? fallback : safeValue;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}