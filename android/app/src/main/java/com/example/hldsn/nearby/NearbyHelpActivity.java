package com.example.hldsn.nearby;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.hldsn.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Activity for displaying nearby emergency resources using OpenStreetMap
 * Fetches data from Overpass API and displays hospitals, pharmacies, police, etc.
 */
public class NearbyHelpActivity extends AppCompatActivity implements LocationListener {
    private static final String TAG = "NearbyHelpActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;

    // UI Components
    private MapView mapView;
    private EditText searchEditText;
    private ChipGroup chipGroup;
    private ChipGroup radiusChipGroup;
    private RecyclerView recyclerView;
    private LinearLayout loadingProgress;
    private LinearLayout permissionOverlay;
    private LinearLayout emptyLayout;
    private LinearLayout errorLayout;
    private ImageView backButton;
    private MaterialButton allowLocationBtn;
    private MaterialButton retryButton;
    private MaterialButton retryErrorButton;
    private MaterialButton searchLargerAreaBtn;
    private TextView emptyText;

    // Data
    private NearbyResourceAdapter adapter;
    private List<NearbyResource> allResources = new ArrayList<>();
    private final List<NearbyResource> filteredResources = new ArrayList<>();
    private final List<NearbyResource> verifiedLocalResources = new ArrayList<>();
    private LocationManager locationManager;
    private double userLatitude = 31.5204; // Default: Lahore, Pakistan
    private double userLongitude = 74.3587;
    private String selectedCategory = "All";
    private int selectedRadiusMeters = 5000;
    private String searchQuery = "";
    private boolean hasLocationPermission = false;
    private boolean isUpdatingProgrammatically = false;
    private Timer debounceTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby_help);



        initVerifiedLocalResources();
        initViews();
        initLocationManager();
        checkLocationPermission();
        setupSearchListener();
        setupChipGroupListener();
        setupRadiusListener();
    }

    private void initVerifiedLocalResources() {
        verifiedLocalResources.add(new NearbyResource("v1", "Rescue 1122 HQ", "Rescue", "Lahore, Pakistan", "1122", 31.5204, 74.3587, 0, true));
        verifiedLocalResources.add(new NearbyResource("v2", "Edhi Center", "Rescue", "Main Boulevard, Lahore", "115", 31.5100, 74.3400, 0, true));
        verifiedLocalResources.add(new NearbyResource("v3", "Chhipa Ambulance", "Rescue", "Lahore", "1020", 31.5300, 74.3600, 0, true));
    }

    private void initViews() {
        mapView = findViewById(R.id.nearby_map);
        searchEditText = findViewById(R.id.nearby_search);
        chipGroup = findViewById(R.id.nearby_chip_group);
        radiusChipGroup = findViewById(R.id.radius_chip_group);
        recyclerView = findViewById(R.id.nearby_recycler);
        loadingProgress = findViewById(R.id.loading_overlay);
        permissionOverlay = findViewById(R.id.permission_overlay);
        emptyLayout = findViewById(R.id.nearby_empty);
        emptyText = findViewById(R.id.nearby_empty_text);
        errorLayout = findViewById(R.id.nearby_error);
        backButton = findViewById(R.id.nearby_back);
        allowLocationBtn = findViewById(R.id.allow_location_btn);
        retryButton = findViewById(R.id.nearby_retry_btn);
        retryErrorButton = findViewById(R.id.nearby_retry_error_btn);
        
        searchLargerAreaBtn = new MaterialButton(this);
        searchLargerAreaBtn.setText(R.string.nearby_help_search_larger);
        searchLargerAreaBtn.setVisibility(View.GONE);
                if (emptyLayout != null) {
                    emptyLayout.addView(searchLargerAreaBtn);
                }

        adapter = new NearbyResourceAdapter(this, filteredResources);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        backButton.setOnClickListener(v -> finish());
        allowLocationBtn.setOnClickListener(v -> requestLocationPermission());
        retryButton.setOnClickListener(v -> fetchResourcesIfPermitted());
        retryErrorButton.setOnClickListener(v -> fetchResourcesIfPermitted());
        
        searchLargerAreaBtn.setOnClickListener(v -> {
            increaseRadius();
            fetchResourcesIfPermitted();
        });
    }

    private void increaseRadius() {
        if (selectedRadiusMeters < 5000) selectedRadiusMeters = 5000;
        else if (selectedRadiusMeters < 10000) selectedRadiusMeters = 10000;
        else if (selectedRadiusMeters < 20000) selectedRadiusMeters = 20000;
        else if (selectedRadiusMeters < 50000) selectedRadiusMeters = 50000;
        updateRadiusChips();
    }

    private void updateRadiusChips() {
        isUpdatingProgrammatically = true;
        if (selectedRadiusMeters == 2000) radiusChipGroup.check(R.id.radius_2km);
        else if (selectedRadiusMeters == 5000) radiusChipGroup.check(R.id.radius_5km);
        else if (selectedRadiusMeters == 10000) radiusChipGroup.check(R.id.radius_10km);
        else if (selectedRadiusMeters == 20000) radiusChipGroup.check(R.id.radius_20km);
        else if (selectedRadiusMeters == 50000) radiusChipGroup.check(R.id.radius_50km);
        isUpdatingProgrammatically = false;
    }

    private void setDefaultRadius(String category) {
        isUpdatingProgrammatically = true;
        switch (category) {
            case "Hospitals":
                selectedRadiusMeters = 5000;
                radiusChipGroup.check(R.id.radius_5km);
                break;
            case "Pharmacies":
                selectedRadiusMeters = 5000; // Reduced from 10km to 5km
                radiusChipGroup.check(R.id.radius_5km);
                break;
            case "Rescue":
                selectedRadiusMeters = 10000; // Reduced from 20km to 10km
                radiusChipGroup.check(R.id.radius_10km);
                break;
            default:
                selectedRadiusMeters = 5000;
                radiusChipGroup.check(R.id.radius_5km);
                break;
        }
        isUpdatingProgrammatically = false;
    }

    private void initLocationManager() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            hasLocationPermission = true;
            fetchUserLocation();
            fetchResourcesIfPermitted();
        } else {
            showPermissionDenied();
        }
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            hasLocationPermission = true;
            permissionOverlay.setVisibility(View.GONE);
            fetchUserLocation();
            fetchResourcesIfPermitted();
        }
    }

    private void fetchUserLocation() {
        try {
            if (locationManager == null) {
                Log.w(TAG, "LocationManager unavailable");
                return;
            }

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location == null) location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (location != null) {
                    userLatitude = location.getLatitude();
                    userLongitude = location.getLongitude();
                }

                String provider = null;
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    provider = LocationManager.GPS_PROVIDER;
                } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    provider = LocationManager.NETWORK_PROVIDER;
                }

                if (provider != null) {
                    locationManager.requestLocationUpdates(provider, 5000, 10, this);
                } else {
                    Log.w(TAG, "No location provider enabled; using fallback coordinates");
                }
            }
        } catch (IllegalArgumentException | SecurityException e) {
            Log.e(TAG, "Error getting location: " + e.getMessage());
        }
    }

    private void showPermissionDenied() {
        permissionOverlay.setVisibility(View.VISIBLE);
        loadingProgress.setVisibility(View.GONE);
        emptyLayout.setVisibility(View.GONE);
        errorLayout.setVisibility(View.GONE);
    }

    private void fetchResourcesIfPermitted() {
        if (!hasLocationPermission) {
            showPermissionDenied();
            return;
        }

        permissionOverlay.setVisibility(View.GONE);
        emptyLayout.setVisibility(View.GONE);
        errorLayout.setVisibility(View.GONE);
        loadingProgress.setVisibility(View.VISIBLE);

        OverpassApiClient.fetchNearbyResources(userLatitude, userLongitude, selectedCategory, selectedRadiusMeters,
            new OverpassApiClient.ApiCallback() {
                @Override
                public void onSuccess(List<NearbyResource> resources) {
                    runOnUiThread(() -> {
                        allResources = mergeWithVerified(resources);
                        updateMap();
                        applyFiltersAndSearch();
                        loadingProgress.setVisibility(View.GONE);
                        if (allResources.isEmpty()) showNoResultsMessage();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        loadingProgress.setVisibility(View.GONE);
                        allResources = mergeWithVerified(new ArrayList<>());
                        if (allResources.isEmpty()) errorLayout.setVisibility(View.VISIBLE);
                        else {
                            applyFiltersAndSearch();
                            Toast.makeText(NearbyHelpActivity.this, R.string.nearby_help_cached, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
    }

    private List<NearbyResource> mergeWithVerified(List<NearbyResource> osmResources) {
        List<NearbyResource> merged = new ArrayList<>(osmResources);
        for (NearbyResource v : verifiedLocalResources) {
            v.distance = calculateDistance(userLatitude, userLongitude, v.latitude, v.longitude);
            if (selectedCategory.equals("All") || v.category.equalsIgnoreCase(selectedCategory)) {
                boolean exists = false;
                for (NearbyResource osm : osmResources) {
                    if (osm.name.equalsIgnoreCase(v.name)) {
                        osm.isVerified = true;
                        exists = true;
                        break;
                    }
                }
                if (!exists) merged.add(v);
            }
        }
        merged.sort((r1, r2) -> Double.compare(r1.distance, r2.distance));
        return merged;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0] / 1000.0;
    }

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

    private void updateMap() {
        if (mapView == null) return;
        mapView.setTileSource(CARTO_LIGHT);
        GeoPoint startPoint = new GeoPoint(userLatitude, userLongitude);
        mapView.getController().setCenter(startPoint);
        mapView.getController().setZoom(14.0);
        mapView.getOverlays().clear();

        ArrayList<OverlayItem> items = new ArrayList<>();
        items.add(new OverlayItem("You", "Current location", startPoint));
        for (NearbyResource res : allResources) {
            GeoPoint point = new GeoPoint(res.latitude, res.longitude);
            items.add(new OverlayItem(res.name, res.category, point));
        }

        ItemizedIconOverlay<OverlayItem> overlay = new ItemizedIconOverlay<>(items, null, this);
        mapView.getOverlays().add(overlay);
        mapView.invalidate();
    }

    private void setupSearchListener() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (debounceTimer != null) debounceTimer.cancel();
                debounceTimer = new Timer();
                debounceTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        searchQuery = s.toString().toLowerCase(Locale.ROOT);
                        runOnUiThread(() -> applyFiltersAndSearch());
                    }
                }, 400);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupChipGroupListener() {
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                Chip chip = findViewById(checkedIds.get(0));
                selectedCategory = chip.getText().toString();
                setDefaultRadius(selectedCategory);
                fetchResourcesIfPermitted();
            }
        });
    }

    private void setupRadiusListener() {
        radiusChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (isUpdatingProgrammatically) return;
            if (!checkedIds.isEmpty()) {
                int id = checkedIds.get(0);
                if (id == R.id.radius_2km) selectedRadiusMeters = 2000;
                else if (id == R.id.radius_5km) selectedRadiusMeters = 5000;
                else if (id == R.id.radius_10km) selectedRadiusMeters = 10000;
                else if (id == R.id.radius_20km) selectedRadiusMeters = 20000;
                else if (id == R.id.radius_50km) selectedRadiusMeters = 50000;
                fetchResourcesIfPermitted();
            }
        });
    }

    private void applyFiltersAndSearch() {
        filteredResources.clear();
        String query = searchQuery.toLowerCase().trim();
        for (NearbyResource res : allResources) {
            boolean matchesSearch = query.isEmpty() || res.name.toLowerCase().contains(query) || res.category.toLowerCase().contains(query) || (res.address != null && res.address.toLowerCase().contains(query));
            if (matchesSearch) filteredResources.add(res);
        }
        adapter.setResources(filteredResources);
        if (filteredResources.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyLayout.setVisibility(View.VISIBLE);
            searchLargerAreaBtn.setVisibility(View.VISIBLE);
            if (allResources.isEmpty()) showNoResultsMessage();
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyLayout.setVisibility(View.GONE);
            searchLargerAreaBtn.setVisibility(View.GONE);
        }
    }

    private void showNoResultsMessage() {
        emptyText.setText(getString(R.string.nearby_help_no_results, selectedCategory));
        searchLargerAreaBtn.setVisibility(selectedRadiusMeters < 50000 ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        userLatitude = location.getLatitude();
        userLongitude = location.getLongitude();
    }

    @Override public void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override public void onPause() { super.onPause(); if (mapView != null) mapView.onPause(); }
    @Override protected void onDestroy() {
        super.onDestroy();
        if (debounceTimer != null) debounceTimer.cancel();
        if (mapView != null) mapView.onDetach();
    }
}
