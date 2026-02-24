package com.example.hldsn.notification_module;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.example.hldsn.incident_report_module.DisplayReportActivity;
import com.example.hldsn.incident_report_module.IncidentModel;

import java.util.ArrayList;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private final List<IncidentModel> incidents = new ArrayList<>();
    private final Context context;

    public NotificationAdapter(Context context) {
        this.context = context;
    }

    public void updateList(List<IncidentModel> newList) {
        Log.d("NotificationAdapter", "updateList called with " + newList.size() + " items");
        incidents.clear();
        incidents.addAll(newList);
        Log.d("NotificationAdapter", "After update → item count now: " + incidents.size());
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        IncidentModel incident = incidents.get(position);

        holder.tvIncidentType.setText(incident.getIncidentType());
        holder.tvIncidentLocation.setText("Near " + incident.getLocation());

        holder.btnDetail.setOnClickListener(v -> {
            Intent intent = new Intent(context, DisplayReportActivity.class); // ← change if needed
//            intent.putExtra("INCIDENT_ID", incident.getId()); // assuming you have document id
            context.startActivity(intent);
        });
    }

    public List<IncidentModel> getCurrentList() {
        return new ArrayList<>(incidents); // return copy to be safe
    }

    @Override
    public int getItemCount() {
        return incidents.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvIncidentType, tvIncidentLocation, btnDetail;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIncidentType = itemView.findViewById(R.id.tvIncidentType);
            tvIncidentLocation = itemView.findViewById(R.id.tvIncidentLocation);
            btnDetail = itemView.findViewById(R.id.btnDetail);
        }
    }
}