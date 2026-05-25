package com.example.hldsn.volunteer_module;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.hldsn.R;
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
import java.util.Locale;

public class CampLocationsMapActivity extends AppCompatActivity {

    private static final String TAG = "CampLocationsMap";
    private static final GeoPoint PAKISTAN_CENTER = new GeoPoint(30.3753, 69.3451);
    private static final BoundingBox PAKISTAN_BOUNDS = new BoundingBox(
            37.2,
            77.9,
            23.5,
            60.8
    );

    private final List<Marker> markers = new ArrayList<>();

    private MapView mapView;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camp_locations_map);

        db = FirebaseFirestore.getInstance();

        mapView = findViewById(R.id.campLocationsMapView);
        configureMapView();

        ImageView backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        loadCampMarkers();
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
        clearMarkers();
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

    private void loadCampMarkers() {
        db.collection("camp_center_locations")
                .whereEqualTo("isActive", true)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    clearMarkers();

                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        Double latitude = toDouble(document.get("latitude"));
                        Double longitude = toDouble(document.get("longitude"));
                        if (latitude == null || longitude == null || !isInsidePakistan(latitude, longitude)) {
                            continue;
                        }

                        String type = safe(document.getString("type"));
                        String name = firstNonBlank(document.getString("name"), "Camp Location");
                        String locationText = firstNonBlank(document.getString("locationText"), "Pakistan");
                        addMarker(latitude, longitude, type, name, locationText);
                    }

                    mapView.invalidate();
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "Failed to load camp locations", error);
                    Toast.makeText(this, "Could not load camp locations", Toast.LENGTH_SHORT).show();
                });
    }

    private void addMarker(double latitude, double longitude, String type, String title, String locationText) {
        if (mapView == null) {
            return;
        }

        Marker marker = new Marker(mapView);
        marker.setPosition(new GeoPoint(latitude, longitude));
        marker.setTitle(title);
        marker.setSubDescription(locationText);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setIcon(ContextCompat.getDrawable(this, "center".equalsIgnoreCase(type) ? R.drawable.ic_center_marker : R.drawable.ic_camp));
        marker.setOnMarkerClickListener((clickedMarker, clickedMapView) -> {
            showMarkerOptions(title, locationText, latitude, longitude, type);
            return true;
        });

        mapView.getOverlays().add(marker);
        markers.add(marker);
    }

    private void showMarkerOptions(String title, String locationText, double latitude, double longitude, String type) {
        String message = String.format(Locale.US, "%s\n%s", capitalize(type), locationText);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("View Location", (dialog, which) -> openGoogleMaps(latitude, longitude))
                .setNegativeButton("Close", null)
                .show();
    }

    private void openGoogleMaps(double latitude, double longitude) {
        String query = String.format(Locale.US, "%f,%f", latitude, longitude);
        Uri mapsUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + query);
        Intent intent = new Intent(Intent.ACTION_VIEW, mapsUri);
        intent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            intent.setPackage(null);
            startActivity(intent);
        }
    }

    private void clearMarkers() {
        if (mapView == null) {
            return;
        }
        mapView.getOverlays().removeAll(markers);
        markers.clear();
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
        String value = safe(primary);
        return value.isEmpty() ? fallback : value;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String capitalize(String value) {
        String text = safe(value);
        if (text.isEmpty()) {
            return "Location";
        }
        return text.substring(0, 1).toUpperCase(Locale.US) + text.substring(1).toLowerCase(Locale.US);
    }
}