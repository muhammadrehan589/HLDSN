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
import com.example.hldsn.notification_module.NotificationDetailActivity;
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
    private static final int CLEAR_REVEAL_DP = 96;
    private static final String SOS_UI_TRACE_TAG = "SOS_UI_TRACE";

    private final List<NotificationItem> items = new ArrayList<>();
    private final Context context;
    private final OnNotificationClearListener clearListener;
    private final OnTaskActionListener taskActionListener;
    private int swipedPosition = RecyclerView.NO_POSITION;

    public interface OnNotificationClearListener {
        void onNotificationClear(NotificationItem item);
    }

    public interface OnTaskActionListener {
        void onAccept(NotificationItem item);
        void onReject(NotificationItem item);
    }

    public NotificationAdapter(Context context) {
        this(context, null, null);
    }

    public NotificationAdapter(Context context, OnNotificationClearListener clearListener) {
        this(context, clearListener, null);
    }

    public NotificationAdapter(Context context, OnNotificationClearListener clearListener, OnTaskActionListener taskActionListener) {
        this.context = context;
        this.clearListener = clearListener;
        this.taskActionListener = taskActionListener;
    }

    public void updateList(List<NotificationItem> newList) {
        int size = newList == null ? 0 : newList.size();
        Log.d("NotificationAdapter", "updateList called with " + size + " items");
        items.clear();
        if (newList != null) {
            items.addAll(newList);
        }
        swipedPosition = RecyclerView.NO_POSITION;
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
            Log.d(SOS_UI_TRACE_TAG, "BIND_CARD id=" + item.getId()
                    + " title=" + item.getTitle()
                    + " subtitle=" + item.getSubtitle());
        }

        if (item.isSosAlert()) {
            holder.cardView.setCardBackgroundColor(COLOR_CARD_SOS);
            holder.tvIncidentType.setTextColor(COLOR_TITLE_SOS);
            holder.tvSosUrgencyBadge.setVisibility(View.VISIBLE);
            holder.taskActionRow.setVisibility(View.GONE);
            holder.btnDetail.setVisibility(View.VISIBLE);
        } else if (item.isTaskAssignment()) {
            holder.cardView.setCardBackgroundColor(COLOR_CARD_NORMAL);
            holder.tvIncidentType.setTextColor(COLOR_TITLE_NORMAL);
            holder.tvSosUrgencyBadge.setVisibility(View.GONE);
            holder.btnDetail.setVisibility(View.GONE);
            holder.taskActionRow.setVisibility(View.VISIBLE);
        } else {
            holder.cardView.setCardBackgroundColor(COLOR_CARD_NORMAL);
            holder.tvIncidentType.setTextColor(COLOR_TITLE_NORMAL);
            holder.tvSosUrgencyBadge.setVisibility(View.GONE);
            holder.taskActionRow.setVisibility(View.GONE);
            holder.btnDetail.setVisibility(View.VISIBLE);
        }

        float translationX = position == swipedPosition ? -dpToPx(CLEAR_REVEAL_DP) : 0f;
        holder.cardView.setTranslationX(translationX);

        holder.btnClear.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (clearListener != null && adapterPosition != RecyclerView.NO_POSITION && adapterPosition < items.size()) {
                clearListener.onNotificationClear(items.get(adapterPosition));
            }
            clearSwipedPosition();
        });

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
            if (item.isUserNotification()) {
                Intent intent = new Intent(context, NotificationDetailActivity.class);
                intent.putExtra(NotificationDetailActivity.EXTRA_TITLE, item.getTitle());
                intent.putExtra(NotificationDetailActivity.EXTRA_SUBTITLE, item.getSubtitle());
                intent.putExtra(NotificationDetailActivity.EXTRA_DETAIL_LABEL, item.getDetailLabel());
                intent.putExtra(NotificationDetailActivity.EXTRA_TIMESTAMP_MS, item.getTimestampMs());
                context.startActivity(intent);
                return;
            }
            Intent intent = new Intent(context, DisplayReportActivity.class);
            context.startActivity(intent);
        });

        holder.btnTaskAccept.setOnClickListener(v -> {
            if (taskActionListener != null) {
                taskActionListener.onAccept(item);
            }
        });

        holder.btnTaskReject.setOnClickListener(v -> {
            if (taskActionListener != null) {
                taskActionListener.onReject(item);
            }
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

    public void setSwipedPosition(int position) {
        if (position < 0 || position >= items.size()) {
            clearSwipedPosition();
            return;
        }

        int previous = swipedPosition;
        swipedPosition = position;

        if (previous != RecyclerView.NO_POSITION && previous != swipedPosition) {
            notifyItemChanged(previous);
        }
        notifyItemChanged(swipedPosition);
    }

    public void clearSwipedPosition() {
        if (swipedPosition == RecyclerView.NO_POSITION) {
            return;
        }

        int previous = swipedPosition;
        swipedPosition = RecyclerView.NO_POSITION;
        notifyItemChanged(previous);
    }

    private float dpToPx(int dp) {
        return dp * context.getResources().getDisplayMetrics().density;
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
        TextView tvIncidentType, tvIncidentLocation, btnDetail, tvSosUrgencyBadge, btnClear;
        View taskActionRow;
        androidx.appcompat.widget.AppCompatButton btnTaskAccept, btnTaskReject;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.notificationCard);
            tvIncidentType = itemView.findViewById(R.id.tvIncidentType);
            tvIncidentLocation = itemView.findViewById(R.id.tvIncidentLocation);
            btnDetail = itemView.findViewById(R.id.btnDetail);
            tvSosUrgencyBadge = itemView.findViewById(R.id.tvSosUrgencyBadge);
            btnClear = itemView.findViewById(R.id.btnClear);
            taskActionRow = itemView.findViewById(R.id.taskActionRow);
            btnTaskAccept = itemView.findViewById(R.id.btnTaskAccept);
            btnTaskReject = itemView.findViewById(R.id.btnTaskReject);
        }
    }
}