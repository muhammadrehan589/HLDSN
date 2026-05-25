package com.example.hldsn.volunteer_module;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.List;

public class VolunteerNetworkMapActivity extends AppCompatActivity {

    private static final String TAG = "VolunteerNetworkMap";
    private static final GeoPoint PAKISTAN_CENTER = new GeoPoint(30.3753, 69.3451);
    private static final BoundingBox PAKISTAN_BOUNDS = new BoundingBox(
            37.2,
            77.9,
            23.5,
            60.8
    );

    private final List<Marker> volunteerMarkers = new ArrayList<>();

    private MapView mapView;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_volunteer_network_map);

        db = FirebaseFirestore.getInstance();

        mapView = findViewById(R.id.volunteerMapView);
        configureMapView();

        ImageView backButton = findViewById(R.id.backButton);
        MaterialButton joinUsButton = findViewById(R.id.joinUsButton);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        if (joinUsButton != null) {
            joinUsButton.setOnClickListener(v ->
                    startActivity(new Intent(this, VolunteerBasicFormActivity.class)));
        }

        loadVolunteerMarkers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        clearVolunteerMarkers();
        if (mapView != null) {
            mapView.onDetach();
        }
        super.onDestroy();
    }

    private void configureMapView() {
        if (mapView == null) {
            return;
        }

        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(false);
        mapView.setHorizontalMapRepetitionEnabled(false);
        mapView.setVerticalMapRepetitionEnabled(false);
        mapView.setScrollableAreaLimitDouble(PAKISTAN_BOUNDS);
        mapView.setMinZoomLevel(5.0);
        mapView.setMaxZoomLevel(18.0);

        IMapController mapController = mapView.getController();
        mapController.setZoom(14.0);
        mapController.setCenter(PAKISTAN_CENTER);

        mapView.post(() -> mapView.zoomToBoundingBox(PAKISTAN_BOUNDS, true));
    }

    private void loadVolunteerMarkers() {
        db.collection("volunteer_locations")
                .whereEqualTo("isActive", true)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    clearVolunteerMarkers();

                    int addedCount = 0;
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        Double latitude = toDouble(document.get("latitude"));
                        Double longitude = toDouble(document.get("longitude"));

                        if (latitude == null || longitude == null || !isInsidePakistan(latitude, longitude)) {
                            continue;
                        }

                        String volunteerName = firstNonBlank(document.getString("name"), "Volunteer Team");
                        String city = firstNonBlank(document.getString("city"), "Pakistan");
                        addVolunteerMarker(latitude, longitude, volunteerName, city);
                        addedCount++;
                    }

                    if (addedCount == 0) {
                        addFallbackMarkers();
                    }

                    mapView.invalidate();
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "Failed to load volunteer locations from Firestore", error);
                    clearVolunteerMarkers();
                    addFallbackMarkers();
                    mapView.invalidate();
                });
    }

    private void addFallbackMarkers() {
        addVolunteerMarker(33.6844, 73.0479, "Islamabad Volunteer Team", "Islamabad");
        addVolunteerMarker(31.5204, 74.3587, "Lahore Volunteer Team", "Lahore");
        addVolunteerMarker(24.8607, 67.0011, "Karachi Volunteer Team", "Karachi");
        addVolunteerMarker(30.1798, 66.9750, "Quetta Volunteer Team", "Quetta");
        addVolunteerMarker(34.0151, 71.5249, "Peshawar Volunteer Team", "Peshawar");
        addVolunteerMarker(30.1575, 71.5249, "Multan Volunteer Team", "Multan");
    }

    private void addVolunteerMarker(double latitude, double longitude, String title, String city) {
        if (mapView == null) {
            return;
        }

        Marker marker = new Marker(mapView);
        marker.setPosition(new GeoPoint(latitude, longitude));
        marker.setTitle(title);
        marker.setSubDescription("Active with HLDSN - " + city);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        mapView.getOverlays().add(marker);
        volunteerMarkers.add(marker);
    }

    private void clearVolunteerMarkers() {
        if (mapView == null) {
            return;
        }
        mapView.getOverlays().removeAll(volunteerMarkers);
        volunteerMarkers.clear();
    }

    private boolean isInsidePakistan(double latitude, double longitude) {
        return latitude <= PAKISTAN_BOUNDS.getLatNorth()
                && latitude >= PAKISTAN_BOUNDS.getLatSouth()
                && longitude <= PAKISTAN_BOUNDS.getLonEast()
                && longitude >= PAKISTAN_BOUNDS.getLonWest();
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

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.trim().isEmpty()) {
            return primary.trim();
        }
        return fallback;
    }
}
