package com.example.hldsn.sos;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;
import com.example.hldsn.notification_module.SosAlertRecord;
import com.google.android.material.button.MaterialButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Displays the details of a received SOS alert.
 * Launched from the notification drawer when the user taps "Details" on an SOS item.
 */
public class SosDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ALERT_JSON = "sos_alert_json";
    private static final String SOS_UI_TRACE_TAG = "SOS_UI_TRACE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sos_detail);

        String json = getIntent().getStringExtra(EXTRA_ALERT_JSON);
        if (json == null) {
            finish();
            return;
        }

        SosAlertRecord alert;
        try {
            alert = SosAlertRecord.fromJson(new JSONObject(json));
        } catch (JSONException e) {
            Toast.makeText(this, "Failed to load SOS details", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews(alert);
    }

    private void bindViews(SosAlertRecord alert) {
        TextView tvTitle    = findViewById(R.id.tvSosTitle);
        TextView tvSubtitle = findViewById(R.id.tvSosSubtitle);
        TextView tvTime     = findViewById(R.id.tvSosTime);
        TextView tvId       = findViewById(R.id.tvSosId);
        View locationSection = findViewById(R.id.locationSection);
        TextView tvCoords   = findViewById(R.id.tvSosCoords);
        MaterialButton btnOpenMaps = findViewById(R.id.btnOpenMaps);
        View btnBack        = findViewById(R.id.btnBack);

        tvTitle.setText(alert.getTitle());
        tvSubtitle.setText(alert.getSubtitle());
        tvTime.setText(new SimpleDateFormat("MMM dd, yyyy  •  hh:mm a", Locale.getDefault())
                .format(new Date(alert.getTimestampMs())));
        tvId.setText("Mesh ID: " + alert.getMessageId());

        if (alert.hasLocation()) {
            double lat = alert.getLatMilli() / 1000.0;
            double lon = alert.getLonMilli() / 1000.0;
            locationSection.setVisibility(View.VISIBLE);
            tvCoords.setText(String.format(Locale.US, "%.6f°,  %.6f°", lat, lon));
            Log.d(SOS_UI_TRACE_TAG, "DETAIL_RENDER id=" + alert.getMessageId()
                    + " sender=" + alert.getSenderName()
                    + " lat=" + lat
                    + " lon=" + lon);

            btnOpenMaps.setOnClickListener(v -> openInMaps(lat, lon));
        } else {
            locationSection.setVisibility(View.GONE);
            Log.d(SOS_UI_TRACE_TAG, "DETAIL_RENDER id=" + alert.getMessageId()
                    + " sender=" + alert.getSenderName()
                    + " hasLocation=false subtitle=" + alert.getSubtitle());
        }

        btnBack.setOnClickListener(v -> finish());
    }

    private void openInMaps(double lat, double lon) {
        Uri geoUri = Uri.parse(String.format(Locale.US,
                "geo:%f,%f?q=%f,%f(SOS Alert)", lat, lon, lat, lon));
        Intent mapsIntent = new Intent(Intent.ACTION_VIEW, geoUri);
        if (mapsIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapsIntent);
        } else {
            // Fallback to browser if no maps app is installed.
            Uri browserUri = Uri.parse(String.format(Locale.US,
                    "https://www.google.com/maps?q=%f,%f", lat, lon));
            startActivity(new Intent(Intent.ACTION_VIEW, browserUri));
        }
    }
}

