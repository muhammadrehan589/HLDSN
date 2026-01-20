package com.example.hldsn;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.ArrayList;
import java.util.List;
public class IncidentAdapter extends RecyclerView.Adapter<IncidentAdapter.IncidentViewHolder> {
    private final List<IncidentModel> incidentList = new ArrayList<>();
    private final OnIncidentReactionListener reactionListener;

    public IncidentAdapter(OnIncidentReactionListener reactionListener) {
        this.reactionListener = reactionListener;
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

        // ===== OLD LOGIC (UNCHANGED) =====
        holder.incidentType.setText(incident.getIncidentType());
        holder.incidentDescription.setText(incident.getDescription());
        holder.incidentLocation.setText("Location: " + incident.getLocation());

        String safetyText = "Reporter is safe: " + (incident.getSafe() ? "Yes" : "No");
        holder.reporterSafety.setText(safetyText);
        holder.reporterSafety.setTextColor(
                incident.getSafe()
                        ? holder.itemView.getContext().getColor(R.color.safe_green)
                        : holder.itemView.getContext().getColor(android.R.color.holo_red_light)
        );

        if (incident.getMediaUrl() != null && !incident.getMediaUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(incident.getMediaUrl())
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .placeholder(R.drawable.ic_fire)
                    .error(R.drawable.ic_fire)
                    .into(holder.incidentImage);
        } else {
            holder.incidentImage.setImageResource(R.drawable.ic_fire);
        }

        boolean commentsExpanded = incident.isCommentsExpanded();
        holder.commentPreviewContainer.setVisibility(
                commentsExpanded ? View.VISIBLE : View.GONE
        );

//        holder.commentContainer.setOnClickListener(v -> {
//            int adapterPosition = holder.getBindingAdapterPosition();
//            if (adapterPosition == RecyclerView.NO_POSITION) return;
//
//            IncidentModel current = incidentList.get(adapterPosition);
//            current.setCommentsExpanded(!current.isCommentsExpanded());
//            notifyItemChanged(adapterPosition);
//        });

        // ===== LIKE / DISLIKE (NEW) =====
        holder.likeCount.setText(String.valueOf(incident.getLikes()));
        holder.dislikeCount.setText(String.valueOf(incident.getDislikes()));

        holder.likeIcon.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION && reactionListener != null) {
                reactionListener.onLikeClicked(
                        incidentList.get(adapterPosition),
                        adapterPosition
                );
            }
        });

        holder.dislikeIcon.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION && reactionListener != null) {
                reactionListener.onDislikeClicked(
                        incidentList.get(adapterPosition),
                        adapterPosition
                );
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

    // ================= VIEW HOLDER =================
    static class IncidentViewHolder extends RecyclerView.ViewHolder {

        ImageView incidentImage;
        TextView incidentType, incidentDescription, incidentLocation, reporterSafety;
        ImageView likeIcon, dislikeIcon, commentIcon;
        TextView likeCount, dislikeCount, commentCount;
        NestedScrollView commentPreviewContainer;
//        View commentContainer;

        public IncidentViewHolder(@NonNull View itemView) {
            super(itemView);

            incidentImage = itemView.findViewById(R.id.incidentImage);
            incidentType = itemView.findViewById(R.id.incidentType);
            incidentDescription = itemView.findViewById(R.id.incidentDescription);
            incidentLocation = itemView.findViewById(R.id.incidentLocation);
            reporterSafety = itemView.findViewById(R.id.reporterSafety);

//            commentContainer = itemView.findViewById(R.id.commentContainer);
            commentPreviewContainer = itemView.findViewById(R.id.commentPreviewContainer);

            likeIcon = itemView.findViewById(R.id.likeIcon);
            dislikeIcon = itemView.findViewById(R.id.dislikeIcon);
//            commentIcon = itemView.findViewById(R.id.commentIcon);

            likeCount = itemView.findViewById(R.id.likeCount);
            dislikeCount = itemView.findViewById(R.id.dislikeCount);
//            commentCount = itemView.findViewById(R.id.commentCount);
        }
    }
}
