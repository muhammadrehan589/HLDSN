package com.example.hldsn;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.ArrayList;
import java.util.List;

public class IncidentAdapter extends RecyclerView.Adapter<IncidentAdapter.IncidentViewHolder> {
    private final OnCommentClickListener commentClickListener;
    private final List<IncidentModel> incidentList = new ArrayList<>();

    public IncidentAdapter(OnCommentClickListener commentClickListener) {
        this.commentClickListener = commentClickListener;
    }

    @NonNull
    @Override
    public IncidentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_incident_report_recyclerview, parent, false);
        return new IncidentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull IncidentViewHolder holder, int position) {
        IncidentModel incident = incidentList.get(position);

        holder.incidentType.setText(incident.getIncidentType());
        holder.incidentDescription.setText(incident.getDescription());
        holder.incidentLocation.setText("Location: " + incident.getLocation());

        // Safety status
        String safetyText = "Reporter is safe: " + (incident.getSafe() ? "Yes" : "No");
        holder.reporterSafety.setText(safetyText);
        holder.reporterSafety.setTextColor(
                incident.getSafe()
                        ? holder.itemView.getContext().getColor(R.color.safe_green) // #2DD09E
                        : holder.itemView.getContext().getColor(android.R.color.holo_red_light)
        );

        // Load image with Glide (fallback to placeholder)
        if (incident.getMediaUrl() != null && !incident.getMediaUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(incident.getMediaUrl())
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .placeholder(R.drawable.ic_fire) // your placeholder
                    .error(R.drawable.ic_fire)
                    .into(holder.incidentImage);
        } else {
            holder.incidentImage.setImageResource(R.drawable.ic_fire);
        }

        holder.commentContainer.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION) {
                return;
            }
            if (commentClickListener != null) {
                commentClickListener.onCommentClicked(incidentList.get(adapterPosition));
            }
        });
    }

    @Override
    public int getItemCount() {
        return incidentList.size();
    }

    public void updateList(List<IncidentModel> newList) {
        incidentList.clear();
        incidentList.addAll(newList);
        notifyDataSetChanged();
    }

    public interface OnCommentClickListener {
        void onCommentClicked(IncidentModel incident);
    }

    static class IncidentViewHolder extends RecyclerView.ViewHolder {
        ImageView incidentImage;
        TextView incidentType, incidentDescription, incidentLocation, reporterSafety;
        LinearLayout commentContainer;

        public IncidentViewHolder(@NonNull View itemView) {
            super(itemView);
            incidentImage = itemView.findViewById(R.id.incidentImage);
            incidentType = itemView.findViewById(R.id.incidentType);
            incidentDescription = itemView.findViewById(R.id.incidentDescription);
            incidentLocation = itemView.findViewById(R.id.incidentLocation);
            reporterSafety = itemView.findViewById(R.id.reporterSafety);
            commentContainer = itemView.findViewById(R.id.commentContainer);
        }
    }
}