package com.example.hldsn.admin_module.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.example.hldsn.incident_report_module.IncidentModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminIncidentReportAdapter
        extends RecyclerView.Adapter<AdminIncidentReportAdapter.ViewHolder> {

    public interface OnDeleteClickListener {
        void onDeleteClick(IncidentModel incident);
    }

    private final List<IncidentModel> items = new ArrayList<>();
    private final OnDeleteClickListener deleteListener;
    private final SimpleDateFormat sdf =
            new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

    public AdminIncidentReportAdapter(OnDeleteClickListener deleteListener) {
        this.deleteListener = deleteListener;
    }

    public void updateList(List<IncidentModel> newList) {
        items.clear();
        items.addAll(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_incident_report, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        IncidentModel incident = items.get(position);

        holder.type.setText(incident.getIncidentType() != null ? incident.getIncidentType() : "Unknown");
        holder.description.setText(incident.getDescription() != null ? incident.getDescription() : "No description");
        holder.location.setText("📍 " + (incident.getLocation() != null ? incident.getLocation() : "Unknown location"));

        Date createdAt = incident.getCreatedAt();
        if (createdAt != null) {
            holder.time.setText("Posted: " + sdf.format(createdAt));
        } else {
            holder.time.setText("Posted: Unknown time");
        }

        holder.deleteBtn.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && deleteListener != null) {
                deleteListener.onDeleteClick(items.get(pos));
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView type, description, location, time;
        ImageButton deleteBtn;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            type = itemView.findViewById(R.id.adminIncidentType);
            description = itemView.findViewById(R.id.adminIncidentDescription);
            location = itemView.findViewById(R.id.adminIncidentLocation);
            time = itemView.findViewById(R.id.adminIncidentTime);
            deleteBtn = itemView.findViewById(R.id.adminDeleteBtn);
        }
    }
}
