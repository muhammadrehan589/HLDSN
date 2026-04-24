package com.example.hldsn.notification_module;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.example.hldsn.incident_report_module.DisplayReportActivity;
import com.example.hldsn.sos.SosDetailActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private static final int COLOR_CARD_NORMAL = 0xFF0F1F1D;
    private static final int COLOR_CARD_SOS    = 0xFF1C0D0D;
    private static final int COLOR_TITLE_NORMAL = Color.WHITE;
    private static final int COLOR_TITLE_SOS    = 0xFFFF474C;

    private final List<NotificationItem> items = new ArrayList<>();
    private final Context context;

    public NotificationAdapter(Context context) {
        this.context = context;
    }

    public void updateList(List<NotificationItem> newList) {
        Log.d("NotificationAdapter", "updateList called with " + newList.size() + " items");
        items.clear();
        items.addAll(newList);
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
        NotificationItem item = items.get(position);

        holder.tvIncidentType.setText(item.getTitle());
        holder.tvIncidentLocation.setText(item.getSubtitle());
        holder.btnDetail.setText(item.getDetailLabel());

        if (item.isSosAlert()) {
            holder.cardView.setCardBackgroundColor(COLOR_CARD_SOS);
            holder.tvIncidentType.setTextColor(COLOR_TITLE_SOS);
            holder.tvSosUrgencyBadge.setVisibility(View.VISIBLE);
        } else {
            holder.cardView.setCardBackgroundColor(COLOR_CARD_NORMAL);
            holder.tvIncidentType.setTextColor(COLOR_TITLE_NORMAL);
            holder.tvSosUrgencyBadge.setVisibility(View.GONE);
        }

        holder.btnDetail.setOnClickListener(v -> {
            if (item.isSosAlert()) {
                String json = item.getSosAlertJson();
                if (json != null) {
                    try {
                        SosAlertRecord alert = SosAlertRecord.fromJson(new JSONObject(json));
                        openAlertLocation(alert, json);
                    } catch (JSONException e) {
                        Toast.makeText(context, "Unable to open SOS location", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(context, "Unable to open SOS location", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            Intent intent = new Intent(context, DisplayReportActivity.class);
            context.startActivity(intent);
        });
    }

    private void openAlertLocation(SosAlertRecord alert, String alertJson) {
        if (alert == null || !alert.hasLocation()) {
            Intent intent = new Intent(context, SosDetailActivity.class);
            intent.putExtra(SosDetailActivity.EXTRA_ALERT_JSON, alertJson);
            context.startActivity(intent);
            return;
        }

        double lat = alert.getLatMilli() / 1000.0d;
        double lon = alert.getLonMilli() / 1000.0d;
        Uri geoUri = Uri.parse(String.format(java.util.Locale.US,
                "geo:%f,%f?q=%f,%f(SOS Alert)", lat, lon, lat, lon));
        Intent mapsIntent = new Intent(Intent.ACTION_VIEW, geoUri);
        if (mapsIntent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(mapsIntent);
            return;
        }

        Uri browserUri = Uri.parse(String.format(java.util.Locale.US,
                "https://www.google.com/maps?q=%f,%f", lat, lon));
        context.startActivity(new Intent(Intent.ACTION_VIEW, browserUri));
    }

    public List<NotificationItem> getCurrentList() {
        return new ArrayList<>(items);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView tvIncidentType, tvIncidentLocation, btnDetail, tvSosUrgencyBadge;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (CardView) itemView;
            tvIncidentType = itemView.findViewById(R.id.tvIncidentType);
            tvIncidentLocation = itemView.findViewById(R.id.tvIncidentLocation);
            btnDetail = itemView.findViewById(R.id.btnDetail);
            tvSosUrgencyBadge = itemView.findViewById(R.id.tvSosUrgencyBadge);
        }
    }
}