package com.example.hldsn.volunteer_module;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.hldsn.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.List;

public class VolunteerNetworkMapActivity extends AppCompatActivity {

    private static final String TAG = "VolunteerNetworkMap";

    private static final XYTileSource CARTO_LIGHT = new XYTileSource(
            "CartoLight",
            0, 19, 256, ".png",
            new String[]{
                    "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
                    "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
                    "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
                    "https://d.basemaps.cartocdn.com/rastertiles/voyager/"
            }
    );

    /** Centre of Pakistan (used when location is unavailable). */
    private static final GeoPoint PAKISTAN_CENTER = new GeoPoint(30.3753, 69.3451);

    /** Bounding box used to constrain scrolling and fall-back zoom. */
    private static final BoundingBox PAKISTAN_BOUNDS = new BoundingBox(
            37.2, 77.9, 23.5, 60.8
    );

    /** Zoom level when centred on the user's location. */
    private static final double USER_ZOOM = 13.5;

    // ── Views ─────────────────────────────────────────────────────────────
    private MapView mapView;
    private FloatingActionButton myLocationFab;

    // ── State ─────────────────────────────────────────────────────────────
    private final List<Marker> volunteerMarkers = new ArrayList<>();
    private Marker userMarker = null;
    private GeoPoint userLocation = null;        // last known user GeoPoint
    private boolean centredOnUser = false;       // animate only on first open

    // ── Firebase ──────────────────────────────────────────────────────────
    private FirebaseFirestore db;
    private ListenerRegistration volunteerListener;

    // ── Location ──────────────────────────────────────────────────────────
    private FusedLocationProviderClient fusedLocationClient;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    grants -> {
                        boolean fine = Boolean.TRUE.equals(
                                grants.get(Manifest.permission.ACCESS_FINE_LOCATION));
                        boolean coarse = Boolean.TRUE.equals(
                                grants.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                        if (fine || coarse) {
                            fetchUserLocationAndCenter();
                        } else {
                            // Permission denied — show Pakistan view
                            centerOnPakistan();
                            Toast.makeText(this,
                                    "Location permission denied. Showing all volunteers.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

    // ─────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_volunteer_network_map);

        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mapView = findViewById(R.id.volunteerMapView);
        myLocationFab = findViewById(R.id.myLocationFab);

        configureMapView();
        setupButtons();
        checkLocationPermissionAndCenter();
        startVolunteerListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        if (mapView != null) mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (volunteerListener != null) {
            volunteerListener.remove();
            volunteerListener = null;
        }
        clearVolunteerMarkers();
        if (mapView != null) mapView.onDetach();
        super.onDestroy();
    }

    // ── Setup ─────────────────────────────────────────────────────────────

    private void configureMapView() {
        if (mapView == null) return;

        mapView.setTileSource(CARTO_LIGHT);
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(false);
        mapView.setHorizontalMapRepetitionEnabled(false);
        mapView.setVerticalMapRepetitionEnabled(false);
        mapView.setScrollableAreaLimitDouble(PAKISTAN_BOUNDS);
        mapView.setMinZoomLevel(5.0);
        mapView.setMaxZoomLevel(18.0);

        // Start with Pakistan overview until we know the user's position
        IMapController ctrl = mapView.getController();
        ctrl.setZoom(6.0);
        ctrl.setCenter(PAKISTAN_CENTER);
    }

    private void setupButtons() {
        ImageView backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        MaterialButton joinUsButton = findViewById(R.id.joinUsButton);
        if (joinUsButton != null) {
            joinUsButton.setOnClickListener(v ->
                    startActivity(new Intent(this, VolunteerBasicFormActivity.class)));
        }

        if (myLocationFab != null) {
            myLocationFab.setOnClickListener(v -> onMyLocationFabClicked());
        }
    }

    // ── Location logic ────────────────────────────────────────────────────

    private void checkLocationPermissionAndCenter() {
        boolean hasFine = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (hasFine || hasCoarse) {
            fetchUserLocationAndCenter();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void fetchUserLocationAndCenter() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            centerOnPakistan();
            return;
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
                        userLocation = point;
                        centerOnUser(point, USER_ZOOM);
                        addOrUpdateUserMarker(point);
                    } else {
                        // No current location — try last known
                        fusedLocationClient.getLastLocation()
                                .addOnSuccessListener(last -> {
                                    if (last != null) {
                                        GeoPoint point = new GeoPoint(last.getLatitude(), last.getLongitude());
                                        userLocation = point;
                                        centerOnUser(point, USER_ZOOM);
                                        addOrUpdateUserMarker(point);
                                    } else {
                                        centerOnPakistan();
                                    }
                                })
                                .addOnFailureListener(e -> centerOnPakistan());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to get current location", e);
                    centerOnPakistan();
                });
    }

    /** Smoothly animate to the user's position. */
    private void centerOnUser(GeoPoint point, double zoom) {
        if (mapView == null) return;
        mapView.getController().animateTo(point, zoom, 600L);
        centredOnUser = true;
    }

    /** Fall back to full-Pakistan view when location is unavailable. */
    private void centerOnPakistan() {
        if (mapView == null) return;
        mapView.post(() -> mapView.zoomToBoundingBox(PAKISTAN_BOUNDS, true));
    }

    /** Add (or move) the blue "you are here" dot. */
    private void addOrUpdateUserMarker(GeoPoint point) {
        if (mapView == null) return;

        if (userMarker != null) {
            userMarker.setPosition(point);
            mapView.invalidate();
            return;
        }

        userMarker = new Marker(mapView);
        userMarker.setPosition(point);
        userMarker.setTitle("You are here");
        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        userMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_my_location_marker));
        userMarker.setOnMarkerClickListener((m, mv) -> {
            m.showInfoWindow();
            return true;
        });

        // Insert at index 0 so it is drawn below volunteer markers
        mapView.getOverlays().add(0, userMarker);
        mapView.invalidate();
    }

    private void onMyLocationFabClicked() {
        if (userLocation != null) {
            centerOnUser(userLocation, USER_ZOOM);
        } else {
            // Try to fetch fresh location
            fetchUserLocationAndCenter();
        }
    }

    // ── Firestore live listener ───────────────────────────────────────────

    /**
     * Attaches a real-time snapshot listener to {@code volunteer_locations}.
     * Every time a document is added, changed, or removed in Firestore the
     * map markers are rebuilt automatically — no restart required.
     */
    private void startVolunteerListener() {
        if (volunteerListener != null) return; // already running

        volunteerListener = db.collection("volunteer_locations")
                .whereEqualTo("isActive", true)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Log.w(TAG, "Volunteer listener error", error);
                        if (volunteerMarkers.isEmpty()) {
                            addFallbackMarkers();
                            mapView.invalidate();
                        }
                        return;
                    }

                    if (querySnapshot == null) return;

                    clearVolunteerMarkers();
                    int added = 0;

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Double lat = toDouble(doc.get("latitude"));
                        Double lng = toDouble(doc.get("longitude"));

                        if (lat == null || lng == null || !isInsidePakistan(lat, lng)) {
                            continue;
                        }

                        String name = firstNonBlank(doc.getString("name"), "Volunteer Team");
                        String city = firstNonBlank(doc.getString("city"), "Pakistan");
                        addVolunteerMarker(lat, lng, name, city);
                        added++;
                    }

                    if (added == 0) {
                        addFallbackMarkers();
                    }

                    mapView.invalidate();
                });
    }

    // ── Marker helpers ────────────────────────────────────────────────────

    private void addFallbackMarkers() {
        addVolunteerMarker(33.6844, 73.0479, "Islamabad Volunteer Team", "Islamabad");
        addVolunteerMarker(31.5204, 74.3587, "Lahore Volunteer Team", "Lahore");
        addVolunteerMarker(24.8607, 67.0011, "Karachi Volunteer Team", "Karachi");
        addVolunteerMarker(30.1798, 66.9750, "Quetta Volunteer Team", "Quetta");
        addVolunteerMarker(34.0151, 71.5249, "Peshawar Volunteer Team", "Peshawar");
        addVolunteerMarker(30.1575, 71.5249, "Multan Volunteer Team", "Multan");
    }

    private void addVolunteerMarker(double lat, double lng, String title, String city) {
        if (mapView == null) return;

        Marker marker = new Marker(mapView);
        marker.setPosition(new GeoPoint(lat, lng));
        marker.setTitle(title);
        marker.setSubDescription("Active with HLDSN · " + city);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_volunteer));

        mapView.getOverlays().add(marker);
        volunteerMarkers.add(marker);
    }

    private void clearVolunteerMarkers() {
        if (mapView == null) return;
        mapView.getOverlays().removeAll(volunteerMarkers);
        volunteerMarkers.clear();
    }

    // ── Utilities ─────────────────────────────────────────────────────────

    private boolean isInsidePakistan(double lat, double lng) {
        return lat <= PAKISTAN_BOUNDS.getLatNorth()
                && lat >= PAKISTAN_BOUNDS.getLatSouth()
                && lng <= PAKISTAN_BOUNDS.getLonEast()
                && lng >= PAKISTAN_BOUNDS.getLonWest();
    }

    private Double toDouble(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
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
        if (primary != null && !primary.trim().isEmpty()) return primary.trim();
        return fallback;
    }
}
