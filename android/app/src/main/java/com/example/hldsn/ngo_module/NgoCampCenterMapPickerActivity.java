package com.example.hldsn.ngo_module;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.hldsn.R;

import org.osmdroid.api.IMapController;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class NgoCampCenterMapPickerActivity extends AppCompatActivity {

    private static final GeoPoint PAKISTAN_CENTER = new GeoPoint(30.3753, 69.3451);
    private static final BoundingBox PAKISTAN_BOUNDS = new BoundingBox(37.2, 77.9, 23.5, 60.8);

    private MapView mapView;
    private Geocoder geocoder;
    private Marker selectedMarker;
    private TextView selectedPointText;
    private double selectedLatitude = Double.NaN;
    private double selectedLongitude = Double.NaN;
    private String selectedLabel = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ngo_camp_center_map_picker);

        geocoder = new Geocoder(this, Locale.getDefault());
        mapView = findViewById(R.id.campCenterPickerMapView);
        selectedPointText = findViewById(R.id.campCenterPickerSelectionText);

        configureMap();

        ImageView backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        ImageView saveButton = findViewById(R.id.saveSelectionButton);
        if (saveButton != null) {
            saveButton.setOnClickListener(v -> returnSelection());
        }

        String locationText = safe(getIntent().getStringExtra(NgoCampCenterFormActivity.EXTRA_LOCATION_TEXT));
        double initialLatitude = getIntent().getDoubleExtra(NgoCampCenterFormActivity.EXTRA_SELECTED_LATITUDE, Double.NaN);
        double initialLongitude = getIntent().getDoubleExtra(NgoCampCenterFormActivity.EXTRA_SELECTED_LONGITUDE, Double.NaN);
        if (!Double.isNaN(initialLatitude) && !Double.isNaN(initialLongitude)) {
            placeSelection(initialLatitude, initialLongitude, safe(getIntent().getStringExtra(NgoCampCenterFormActivity.EXTRA_SELECTED_LABEL)));
        } else if (!locationText.isEmpty()) {
            centerOnTypedLocation(locationText);
        }

        mapView.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (p != null) {
                    placeSelection(p.getLatitude(), p.getLongitude(), "");
                }
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                if (p != null) {
                    placeSelection(p.getLatitude(), p.getLongitude(), "");
                }
                return true;
            }
        }));
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
        if (mapView != null) {
            mapView.onDetach();
        }
        super.onDestroy();
    }

    private void configureMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(false);
        mapView.setHorizontalMapRepetitionEnabled(false);
        mapView.setVerticalMapRepetitionEnabled(false);
        mapView.setScrollableAreaLimitDouble(PAKISTAN_BOUNDS);
        mapView.setMinZoomLevel(5.0);
        mapView.setMaxZoomLevel(18.0);

        IMapController controller = mapView.getController();
        controller.setZoom(14.0);
        controller.setCenter(PAKISTAN_CENTER);
    }

    private void centerOnTypedLocation(String locationText) {
        try {
            List<Address> results = geocoder.getFromLocationName(locationText + ", Pakistan", 1);
            if (results == null || results.isEmpty()) {
                results = geocoder.getFromLocationName(locationText, 1);
            }

            if (results == null || results.isEmpty()) {
                Toast.makeText(this, "Could not resolve the typed location. Tap the map to choose manually.", Toast.LENGTH_SHORT).show();
                return;
            }

            Address address = results.get(0);
            placeSelection(address.getLatitude(), address.getLongitude(), buildLabel(address));
            mapView.getController().setCenter(new GeoPoint(address.getLatitude(), address.getLongitude()));
        } catch (IOException error) {
            Toast.makeText(this, "Location search failed. Tap the map to choose manually.", Toast.LENGTH_SHORT).show();
        }
    }

    private void placeSelection(double latitude, double longitude, String label) {
        selectedLatitude = latitude;
        selectedLongitude = longitude;
        selectedLabel = safe(label);

        if (selectedMarker != null) {
            mapView.getOverlays().remove(selectedMarker);
        }

        selectedMarker = new Marker(mapView);
        selectedMarker.setPosition(new GeoPoint(latitude, longitude));
        selectedMarker.setTitle("Selected location");
        selectedMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        selectedMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_center_marker));
        mapView.getOverlays().add(selectedMarker);
        mapView.invalidate();

        String summary = String.format(Locale.US, "Selected: %.5f, %.5f", latitude, longitude);
        if (!selectedLabel.isEmpty()) {
            summary += "\n" + selectedLabel;
        }
        selectedPointText.setText(summary);
    }

    private void returnSelection() {
        if (Double.isNaN(selectedLatitude) || Double.isNaN(selectedLongitude)) {
            Toast.makeText(this, "Tap a point on the map first", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent data = new Intent();
        data.putExtra(NgoCampCenterFormActivity.EXTRA_SELECTED_LATITUDE, selectedLatitude);
        data.putExtra(NgoCampCenterFormActivity.EXTRA_SELECTED_LONGITUDE, selectedLongitude);
        data.putExtra(NgoCampCenterFormActivity.EXTRA_SELECTED_LABEL, selectedLabel);
        setResult(RESULT_OK, data);
        finish();
    }

    private String buildLabel(Address address) {
        String locality = firstNonBlank(address.getLocality(), address.getSubAdminArea());
        String admin = firstNonBlank(address.getAdminArea(), "Pakistan");
        if (!locality.isEmpty() && !admin.isEmpty()) {
            return locality + ", " + admin;
        }
        if (!locality.isEmpty()) {
            return locality;
        }
        return firstNonBlank(address.getFeatureName(), "");
    }

    private String firstNonBlank(String primary, String fallback) {
        String value = safe(primary);
        return value.isEmpty() ? safe(fallback) : value;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}