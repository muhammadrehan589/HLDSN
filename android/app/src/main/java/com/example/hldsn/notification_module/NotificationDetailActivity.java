package com.example.hldsn.notification_module;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;

import java.util.Date;

public class NotificationDetailActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_SUBTITLE = "extra_subtitle";
    public static final String EXTRA_DETAIL_LABEL = "extra_detail_label";
    public static final String EXTRA_TIMESTAMP_MS = "extra_timestamp_ms";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_detail);

        ImageView backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        TextView screenTitle = findViewById(R.id.screenTitle);
        TextView notificationTitle = findViewById(R.id.notificationTitle);
        TextView notificationSubtitle = findViewById(R.id.notificationSubtitle);
        TextView notificationMeta = findViewById(R.id.notificationMeta);
        TextView detailPill = findViewById(R.id.detailPill);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String subtitle = getIntent().getStringExtra(EXTRA_SUBTITLE);
        String detailLabel = getIntent().getStringExtra(EXTRA_DETAIL_LABEL);
        long timestampMs = getIntent().getLongExtra(EXTRA_TIMESTAMP_MS, 0L);

        if (screenTitle != null) {
            screenTitle.setText("Notification");
        }
        if (notificationTitle != null) {
            notificationTitle.setText(firstNonBlank(title, "Notification"));
        }
        if (notificationSubtitle != null) {
            notificationSubtitle.setText(firstNonBlank(subtitle, "Details not available."));
        }
        if (detailPill != null) {
            detailPill.setText(firstNonBlank(detailLabel, "Details"));
        }
        if (notificationMeta != null) {
            notificationMeta.setText(formatTimestamp(timestampMs));
        }
    }

    private String formatTimestamp(long timestampMs) {
        if (timestampMs <= 0L) {
            return "";
        }
        return DateFormat.format("dd MMM yyyy, hh:mm a", new Date(timestampMs)).toString();
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}