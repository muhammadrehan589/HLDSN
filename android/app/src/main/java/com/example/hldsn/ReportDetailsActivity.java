package com.example.hldsn;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

public class ReportDetailsActivity extends AppCompatActivity {

    private TextView incidentTypeValue, locationValue, descriptionValue;
    private ImageView mediaPreview;
    private TextView mediaPlaceholderText;

    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_details);

        // Bind Views
        incidentTypeValue = findViewById(R.id.incidentTypeValue);
        locationValue = findViewById(R.id.locationValue);
        descriptionValue = findViewById(R.id.descriptionValue);
        mediaPreview = findViewById(R.id.mediaPreview);
        mediaPlaceholderText = findViewById(R.id.mediaPlaceholderText);

        db = FirebaseFirestore.getInstance();

        fetchLatestIncident();
    }

    private void fetchLatestIncident() {
        db.collection("incidents")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);

                        String type = doc.getString("incidentType");
                        String location = doc.getString("location");
                        String description = doc.getString("description");
                        String mediaUrl = doc.getString("mediaUrl");

                        incidentTypeValue.setText(type != null ? type : "N/A");
                        locationValue.setText(location != null ? location : "N/A");
                        descriptionValue.setText(description != null ? description : "No description");

                        if (mediaUrl != null && !mediaUrl.isEmpty()) {
                            mediaPlaceholderText.setVisibility(View.GONE);
                            Glide.with(this)
                                    .load(mediaUrl)
                                    .into(mediaPreview);
                        } else {
                            mediaPlaceholderText.setVisibility(View.VISIBLE);
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }
}
