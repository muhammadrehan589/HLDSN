package com.example.hldsn.hazard_module;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.hldsn.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class HazardAlertMapActivity extends AppCompatActivity {

    private static final String TAG = "HazardAlertMap";
    private static final String HAZARD_COLLECTION = "hazard_alert_zones";
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000L;
    private static final long ZONE_EXPIRY_MS = 48 * 60 * 60 * 1000L; // 48 hours
    private static final String NOTIFICATION_CHANNEL_ID = "hazard_alerts";
    private static final String PREFS_NAME = "hazard_alert_prefs";
    private static final String KEY_NOTIFIED_ZONE_IDS = "notified_zone_ids";
    private static final int MAX_NOTIFIED_IDS = 200;

    // Live feed URLs
    private static final String USGS_EARTHQUAKE_URL = "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&minlatitude=23.5&maxlatitude=37.2&minlongitude=60.8&maxlongitude=77.9&orderby=time&limit=25";
    private static final String EONET_FLOOD_URL = "https://eonet.gsfc.nasa.gov/api/v3/events?status=open&category=floods&limit=50";
    private static final String EONET_LANDSLIDE_URL = "https://eonet.gsfc.nasa.gov/api/v3/events?status=open&category=landslides&limit=50";
    private static final String EONET_WILDFIRE_URL = "https://eonet.gsfc.nasa.gov/api/v3/events?status=open&category=wildfires&limit=50";

    // Pakistan bounds with 1-degree buffer for border regions
    private static final GeoPoint PAKISTAN_CENTER = new GeoPoint(30.3753, 69.3451);
    private static final double PAKISTAN_LAT_NORTH = 37.2;
    private static final double PAKISTAN_LAT_SOUTH = 23.5;
    private static final double PAKISTAN_LON_EAST = 77.9;
    private static final double PAKISTAN_LON_WEST = 60.8;
    private static final double BUFFER_DEGREES = 1.0;
    private static final BoundingBox PAKISTAN_BOUNDS = new BoundingBox(
            PAKISTAN_LAT_NORTH, PAKISTAN_LON_EAST,
            PAKISTAN_LAT_SOUTH, PAKISTAN_LON_WEST
    );

    private final List<Marker> hazardMarkers = new ArrayList<>();
    private final List<Polygon> hazardPolygons = new ArrayList<>();

    private MapView mapView;
    private FirebaseFirestore db;
    private MaterialCardView headerCard;
    private TextView selectedTypeChip;
    private TextView headerTitle;
    private TextView headerSubtitle;
    private TextView liveBadge;
    private TextView selectedTitle;
    private TextView selectedSummary;
    private TextView selectedDisclaimer;
    private TextView selectedTiming;
    private TextView selectedStatus;
    private TextView selectedConfidence;
    private TextView selectedDetails;
    private LinearLayout affectedAreasList;
    private FrameLayout loadingOverlay;
    private LinearLayout errorOverlay;
    private MaterialButton retryButton;
    private FloatingActionButton myLocationFab;
    private boolean headerExpanded = true;
    private float headerTouchStartX;
    private float headerTouchStartY;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService networkExecutor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private MyLocationNewOverlay locationOverlay;
    private GeoPoint userLocation;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    grants -> {
                        if (grants.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)) {
                            enableUserLocation();
                        }
                    }
            );

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            loadHazardZones();
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hazard_alert_map);

        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mapView = findViewById(R.id.hazardMapView);
        headerCard = findViewById(R.id.headerCard);
        headerTitle = findViewById(R.id.headerTitle);
        headerSubtitle = findViewById(R.id.headerSubtitle);
        liveBadge = findViewById(R.id.liveBadge);
        selectedTypeChip = findViewById(R.id.selectedHazardTypeChip);
        selectedTitle = findViewById(R.id.selectedHazardTitle);
        selectedSummary = findViewById(R.id.selectedHazardSummary);
        selectedDisclaimer = findViewById(R.id.selectedHazardDisclaimer);
        selectedTiming = findViewById(R.id.selectedHazardTiming);
        selectedStatus = findViewById(R.id.selectedHazardStatus);
        selectedConfidence = findViewById(R.id.selectedHazardConfidence);
        selectedDetails = findViewById(R.id.selectedHazardDetails);
        affectedAreasList = findViewById(R.id.affectedAreasList);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        errorOverlay = findViewById(R.id.errorOverlay);
        retryButton = findViewById(R.id.retryButton);
        myLocationFab = findViewById(R.id.myLocationFab);

        configureMapView();
        configureHeaderCardGestures();
        createNotificationChannel();

        ImageView backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        if (retryButton != null) {
            retryButton.setOnClickListener(v -> {
                hideError();
                showLoading();
                loadHazardZones();
            });
        }

        if (myLocationFab != null) {
            myLocationFab.setOnClickListener(v -> {
                if (userLocation != null) {
                    mapView.getController().animateTo(userLocation);
                    mapView.getController().setZoom(Math.max(mapView.getZoomLevelDouble(), 7.0));
                } else {
                    requestLocationPermission();
                }
            });
        }

        requestLocationPermission();
        showLoading();
        loadHazardZones();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        if (locationOverlay != null) {
            locationOverlay.enableMyLocation();
        }
        startRefreshLoop();
    }

    @Override
    protected void onPause() {
        stopRefreshLoop();
        if (locationOverlay != null) {
            locationOverlay.disableMyLocation();
        }
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopRefreshLoop();
        networkExecutor.shutdownNow();
        clearHazards();
        if (mapView != null) {
            mapView.onDetach();
        }
        super.onDestroy();
    }

    // ── Location ──────────────────────────────────────────────────────────

    private void requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            enableUserLocation();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void enableUserLocation() {
        if (mapView == null) return;

        // Add osmdroid blue dot overlay
        locationOverlay = new MyLocationNewOverlay(mapView);
        locationOverlay.enableMyLocation();
        mapView.getOverlays().add(locationOverlay);

        // Get current location and center map
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            userLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
                            mapView.getController().animateTo(userLocation);
                            mapView.getController().setZoom(Math.max(mapView.getZoomLevelDouble(), 6.5));
                        }
                    });
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.hazard_notification_channel),
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for new hazard alerts in Pakistan");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void sendHazardNotification(HazardZone zone) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_hazard)
                .setContentTitle(getString(R.string.hazard_notification_title))
                .setContentText(zone.typeLabel + ": " + zone.title + " — " + zone.area)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(zone.summary + "\n\nAffected: " + zone.area))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat.from(this).notify(zone.hashCode(), builder.build());
    }

    private void checkAndNotifyNewHazards(List<HazardZone> zones) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> notifiedIds = new LinkedHashSet<>(
                prefs.getStringSet(KEY_NOTIFIED_ZONE_IDS, new LinkedHashSet<>()));

        Set<String> newNotifiedIds = new LinkedHashSet<>(notifiedIds);
        boolean changed = false;

        for (HazardZone zone : zones) {
            if (zone.confidence >= 80) {
                String zoneKey = zone.type + ":" + zone.title + ":" + zone.area;
                if (!newNotifiedIds.contains(zoneKey)) {
                    sendHazardNotification(zone);
                    newNotifiedIds.add(zoneKey);
                    changed = true;
                }
            }
        }

        // Cap the set size to avoid unbounded growth
        if (newNotifiedIds.size() > MAX_NOTIFIED_IDS) {
            List<String> list = new ArrayList<>(newNotifiedIds);
            newNotifiedIds = new LinkedHashSet<>(list.subList(list.size() - MAX_NOTIFIED_IDS, list.size()));
            changed = true;
        }

        if (changed) {
            prefs.edit().putStringSet(KEY_NOTIFIED_ZONE_IDS, newNotifiedIds).apply();
        }
    }

    // ── UI State ──────────────────────────────────────────────────────────

    private void showLoading() {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
        if (errorOverlay != null) errorOverlay.setVisibility(View.GONE);
    }

    private void hideLoading() {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
    }

    private void showError(String message) {
        hideLoading();
        if (errorOverlay != null) errorOverlay.setVisibility(View.VISIBLE);
        TextView errorMsg = findViewById(R.id.errorMessage);
        if (errorMsg != null && message != null) {
            errorMsg.setText(message);
        }
    }

    private void hideError() {
        if (errorOverlay != null) errorOverlay.setVisibility(View.GONE);
    }

    // ── Map Setup ─────────────────────────────────────────────────────────

    /**
     * Carto's free tile CDN - no API key required, explicitly permits app usage.
     * Serves the same OpenStreetMap data as MAPNIK but through Carto's infrastructure,
     * which does not block apps the way OSM's volunteer tile servers do.
     */
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

        IMapController mapController = mapView.getController();
        mapController.setZoom(5.8);
        mapController.setCenter(PAKISTAN_CENTER);

        mapView.post(() -> mapView.zoomToBoundingBox(PAKISTAN_BOUNDS, true));
    }

    private void configureHeaderCardGestures() {
        if (headerCard == null) return;

        headerCard.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    headerTouchStartX = event.getX();
                    headerTouchStartY = event.getY();
                    return true;
                case MotionEvent.ACTION_UP:
                    float deltaX = event.getX() - headerTouchStartX;
                    float deltaY = event.getY() - headerTouchStartY;
                    float density = getResources().getDisplayMetrics().density;
                    float swipeThreshold = 28f * density;

                    if (Math.abs(deltaY) > swipeThreshold && Math.abs(deltaY) > Math.abs(deltaX)) {
                        if (deltaY < 0) {
                            setHeaderExpanded(false);
                        } else {
                            setHeaderExpanded(true);
                        }
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        });

        setHeaderExpanded(true);
    }

    private void setHeaderExpanded(boolean expanded) {
        headerExpanded = expanded;
        if (headerSubtitle != null) {
            headerSubtitle.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
        if (liveBadge != null) {
            liveBadge.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
        if (headerTitle != null) {
            headerTitle.setText(expanded ? R.string.hazard_alert_title : R.string.hazard_collapsed_title);
        }
    }

    // ── Data Loading (Parallel) ───────────────────────────────────────────

    private void loadHazardZones() {
        List<HazardZone>[] results = new List[4]; // earthquake, flood, landslide, fire
        CountDownLatch latch = new CountDownLatch(4);

        // Fetch earthquake zones
        networkExecutor.execute(() -> {
            results[0] = fetchEarthquakeZonesSynchronous();
            latch.countDown();
        });

        // Fetch flood zones (EONET)
        networkExecutor.execute(() -> {
            results[1] = fetchGenericZonesSynchronous(EONET_FLOOD_URL, "flood");
            latch.countDown();
        });

        // Fetch landslide zones (EONET)
        networkExecutor.execute(() -> {
            results[2] = fetchGenericZonesSynchronous(EONET_LANDSLIDE_URL, "landslide");
            latch.countDown();
        });

        // Fetch fire zones (EONET)
        networkExecutor.execute(() -> {
            results[3] = fetchGenericZonesSynchronous(EONET_WILDFIRE_URL, "fire");
            latch.countDown();
        });

        // Wait for all feeds on background thread, then fetch Firestore
        networkExecutor.execute(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while waiting for feeds", e);
            }

            List<HazardZone> liveEarthquakeZones = results[0] != null ? results[0] : new ArrayList<>();
            List<HazardZone> floodZones = results[1] != null ? results[1] : new ArrayList<>();
            List<HazardZone> landslideZones = results[2] != null ? results[2] : new ArrayList<>();
            List<HazardZone> fireZones = results[3] != null ? results[3] : new ArrayList<>();

            Log.d(TAG, "Fetched: eq=" + liveEarthquakeZones.size()
                    + " flood=" + floodZones.size()
                    + " landslide=" + landslideZones.size()
                    + " fire=" + fireZones.size());

            // Fetch Firestore zones
            db.collection(HAZARD_COLLECTION)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        List<HazardZone> firestoreZones = new ArrayList<>();
                        List<HazardZone> firestoreEarthquakeFallback = new ArrayList<>();

                        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                            HazardZone zone = parseZone(document);
                            if (zone == null || !zone.isActive) continue;

                            // Skip zones older than 48 hours
                            if (zone.eventTime > 0 && (System.currentTimeMillis() - zone.eventTime) > ZONE_EXPIRY_MS) {
                                Log.d(TAG, "Skipping expired zone: " + zone.title);
                                continue;
                            }

                            if ("earthquake".equals(zone.type)) {
                                firestoreEarthquakeFallback.add(zone);
                            } else {
                                firestoreZones.add(zone);
                            }
                        }

                        List<HazardZone> zones = mergeZonesForDisplay(
                                liveEarthquakeZones, floodZones, landslideZones, fireZones,
                                firestoreZones, firestoreEarthquakeFallback);

                        Log.d(TAG, "Merged zones: " + zones.size() + " total");
                        mainHandler.post(() -> onZonesLoaded(zones));
                    })
                    .addOnFailureListener(error -> {
                        Log.w(TAG, "Failed to load Firestore hazard zones", error);
                        List<HazardZone> zones = mergeZonesForDisplay(
                                liveEarthquakeZones, floodZones, landslideZones, fireZones,
                                new ArrayList<>(), new ArrayList<>());
                        mainHandler.post(() -> onZonesLoaded(zones));
                    });
        });
    }

    private void onZonesLoaded(List<HazardZone> zones) {
        hideLoading();

        if (zones.isEmpty()) {
            // Use fallback demo zones
            zones = buildFallbackZones();
            if (zones.isEmpty()) {
                showError("No hazard data available. Live feeds may be temporarily unreachable.");
                return;
            }
        }

        renderZones(zones);
        checkAndNotifyNewHazards(zones);
    }

    // ── Synchronous Feed Fetchers ─────────────────────────────────────────

    private List<HazardZone> fetchEarthquakeZonesSynchronous() {
        List<HazardZone> zones = new ArrayList<>();
        HttpURLConnection connection = null;
        try {
            URL url = new URL(USGS_EARTHQUAKE_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                String responseBody = readStream(connection.getInputStream());
                zones = parseUsgsEarthquakeZones(responseBody);
            } else {
                Log.w(TAG, "USGS earthquake feed returned HTTP " + responseCode);
            }
        } catch (IOException | JSONException error) {
            Log.w(TAG, "Failed to fetch live earthquake zones", error);
        } finally {
            if (connection != null) connection.disconnect();
        }
        Log.d(TAG, "Fetched " + zones.size() + " earthquake zones from USGS");
        return zones;
    }

    private List<HazardZone> fetchGenericZonesSynchronous(String url, String type) {
        if (url == null || url.trim().isEmpty()) return new ArrayList<>();

        List<HazardZone> zones = new ArrayList<>();
        HttpURLConnection connection = null;
        try {
            URL u = new URL(url);
            connection = (HttpURLConnection) u.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                String responseBody = readStream(connection.getInputStream());
                try {
                    zones = parseGenericGeoJsonZones(responseBody, type);
                } catch (JSONException je) {
                    Log.w(TAG, "Failed to parse " + type + " geojson", je);
                }
            } else {
                Log.w(TAG, "Feed returned HTTP " + responseCode + " for " + type);
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to fetch " + type + " zones", e);
        } finally {
            if (connection != null) connection.disconnect();
        }
        Log.d(TAG, "Fetched " + zones.size() + " " + type + " zones from live feed");
        return zones;
    }

    // ── Zone Merging ──────────────────────────────────────────────────────

    private List<HazardZone> mergeZonesForDisplay(List<HazardZone> liveEarthquakeZones,
                                                  List<HazardZone> floodZones,
                                                  List<HazardZone> landslideZones,
                                                  List<HazardZone> fireZones,
                                                  List<HazardZone> firestoreZones,
                                                  List<HazardZone> firestoreEarthquakeFallback) {
        List<HazardZone> zones = new ArrayList<>();
        List<HazardZone> liveZones = new ArrayList<>();
        liveZones.addAll(liveEarthquakeZones);
        liveZones.addAll(floodZones);
        liveZones.addAll(landslideZones);
        liveZones.addAll(fireZones);
        zones.addAll(limitMostRecentPerType(liveZones, 3));

        for (HazardZone zone : firestoreZones) {
            if (zone != null) zones.add(zone);
        }

        if (zones.isEmpty()) {
            zones.addAll(firestoreEarthquakeFallback);
        }

        // Sort by confidence (highest first) before dedup/cap
        zones.sort((a, b) -> Integer.compare(b.confidence, a.confidence));

        return dedupeAndCapByType(zones, 4);
    }

    private List<HazardZone> limitMostRecentPerType(List<HazardZone> zones, int limitPerType) {
        Map<String, List<HazardZone>> grouped = new LinkedHashMap<>();
        for (HazardZone zone : zones) {
            if (zone == null) continue;
            List<HazardZone> bucket = grouped.get(zone.type);
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(zone.type, bucket);
            }
            if (bucket.size() < limitPerType) {
                bucket.add(zone);
            }
        }

        List<HazardZone> result = new ArrayList<>();
        for (List<HazardZone> bucket : grouped.values()) {
            result.addAll(bucket);
        }
        return result;
    }

    private List<HazardZone> dedupeAndCapByType(List<HazardZone> zones, int capPerType) {
        Map<String, List<HazardZone>> grouped = new LinkedHashMap<>();
        for (HazardZone zone : zones) {
            if (zone == null) continue;
            List<HazardZone> bucket = grouped.get(zone.type);
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(zone.type, bucket);
            }
            if (bucket.size() < capPerType) {
                boolean duplicate = false;
                for (HazardZone existing : bucket) {
                    if (safe(existing.title).equalsIgnoreCase(safe(zone.title))
                            && safe(existing.area).equalsIgnoreCase(safe(zone.area))) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    bucket.add(zone);
                }
            }
        }

        List<HazardZone> result = new ArrayList<>();
        for (List<HazardZone> bucket : grouped.values()) {
            result.addAll(bucket);
        }
        return result;
    }

    // ── Parsers ───────────────────────────────────────────────────────────

    private List<HazardZone> parseGenericGeoJsonZones(String responseBody, String type) throws JSONException {
        List<HazardZone> zones = new ArrayList<>();
        JSONObject root = new JSONObject(responseBody);

        // Standard GeoJSON 'features' array
        JSONArray features = root.optJSONArray("features");
        if (features != null) {
            for (int i = 0; i < features.length(); i++) {
                JSONObject feature = features.optJSONObject(i);
                if (feature == null) continue;
                JSONObject geometry = feature.optJSONObject("geometry");
                JSONArray coords = geometry != null ? geometry.optJSONArray("coordinates") : null;
                if (coords == null || coords.length() < 2) continue;

                double lon = coords.optDouble(0, Double.NaN);
                double lat = coords.optDouble(1, Double.NaN);
                if (Double.isNaN(lat) || Double.isNaN(lon)) continue;

                JSONObject props = feature.optJSONObject("properties");
                String title = props != null ? (props.optString("title", props.optString("place", ""))) : "";
                String place = props != null ? props.optString("place", title) : title;
                String countryHint = props != null ? props.optString("country", "") : "";
                String summary = props != null ? props.optString("summary", props.optString("description", "")) : "";
                double magnitude = props != null ? props.optDouble("mag", 0.0) : 0.0;
                Double rainfallMm = extractRainfallMm(props, summary);

                boolean inPakistan = isInsidePakistan(lat, lon);
                String combined = (title + " " + place + " " + countryHint + " " + summary).toLowerCase(Locale.US);
                boolean mentionsPakistan = combined.contains("pakistan");
                if (!(inPakistan || mentionsPakistan)) continue;

                GeoPoint center = new GeoPoint(lat, lon);
                int confidence = calculateConfidence(type, magnitude, rainfallMm);
                double radiusKm = calculateRadiusKm(type, magnitude);

                // Extract event time from properties
                long eventTime = extractEventTime(props);

                if (rainfallMm != null) {
                    summary = String.format(Locale.US, "Rainfall recorded: %.1f mm. %s", rainfallMm, summary);
                }

                zones.add(new HazardZone(
                        type, formatTypeLabel(type),
                        title.isEmpty() ? (type + " event") : title,
                        place,
                        summary.isEmpty() ? defaultSummary(type) : summary,
                        "Live feed", "External feed",
                        center, radiusKm, 0, confidence,
                        colorForType(type), markerIconForType(type),
                        new ArrayList<>(), buildAffectedAreasFromPlace(place),
                        true, eventTime
                ));
            }
            return zones;
        }

        // EONET 'events' format: parse events -> geometry
        JSONArray events = root.optJSONArray("events");
        if (events != null) {
            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.optJSONObject(i);
                if (event == null) continue;
                String title = event.optString("title", "");
                String summary = event.optString("description", "");

                JSONArray geoms = event.optJSONArray("geometry");
                if (geoms == null || geoms.length() == 0) continue;
                JSONObject lastGeom = geoms.optJSONObject(geoms.length() - 1);
                if (lastGeom == null) continue;
                Object coordsObj = lastGeom.opt("coordinates");
                double lat = Double.NaN, lon = Double.NaN;

                if (coordsObj instanceof JSONArray) {
                    JSONArray coords = (JSONArray) coordsObj;
                    if (coords.length() >= 2 && coords.opt(0) instanceof Number && coords.opt(1) instanceof Number) {
                        lon = coords.optDouble(0, Double.NaN);
                        lat = coords.optDouble(1, Double.NaN);
                    } else if (coords.length() > 0 && coords.opt(0) instanceof JSONArray) {
                        JSONArray first = coords.optJSONArray(0);
                        if (first != null && first.length() >= 2) {
                            lon = first.optDouble(0, Double.NaN);
                            lat = first.optDouble(1, Double.NaN);
                        }
                    }
                }

                if (Double.isNaN(lat) || Double.isNaN(lon)) continue;

                boolean inPakistan = isInsidePakistan(lat, lon);
                String combined = (title + " " + summary).toLowerCase(Locale.US);
                boolean mentionsPakistan = combined.contains("pakistan");
                if (!(inPakistan || mentionsPakistan)) continue;

                GeoPoint center = new GeoPoint(lat, lon);
                int confidence = calculateConfidence(type, 0, null);
                double radiusKm = calculateRadiusKm(type, 0);

                // Extract event time from EONET geometry
                long eventTime = 0;
                String dateStr = lastGeom.optString("date", "");
                if (!dateStr.isEmpty()) {
                    eventTime = parseIsoDate(dateStr);
                }

                zones.add(new HazardZone(
                        type, formatTypeLabel(type),
                        title.isEmpty() ? (type + " event") : title,
                        title,
                        summary.isEmpty() ? defaultSummary(type) : summary,
                        "Live feed", "EONET",
                        center, radiusKm, 0, confidence,
                        colorForType(type), markerIconForType(type),
                        new ArrayList<>(), buildAffectedAreasFromPlace(title),
                        true, eventTime
                ));
            }
            return zones;
        }

        return zones;
    }

    private List<HazardZone> parseUsgsEarthquakeZones(String responseBody) throws JSONException {
        List<HazardZone> zones = new ArrayList<>();
        JSONObject root = new JSONObject(responseBody);
        JSONArray features = root.optJSONArray("features");
        if (features == null) return zones;

        for (int index = 0; index < features.length(); index++) {
            JSONObject feature = features.optJSONObject(index);
            if (feature == null) continue;

            JSONObject properties = feature.optJSONObject("properties");
            JSONObject geometry = feature.optJSONObject("geometry");
            JSONArray coordinates = geometry != null ? geometry.optJSONArray("coordinates") : null;
            if (properties == null || coordinates == null || coordinates.length() < 2) continue;

            double longitude = coordinates.optDouble(0, Double.NaN);
            double latitude = coordinates.optDouble(1, Double.NaN);
            double depthKm = coordinates.length() >= 3 ? coordinates.optDouble(2, Double.NaN) : Double.NaN;
            if (Double.isNaN(latitude) || Double.isNaN(longitude)) continue;

            if (!isInsidePakistan(latitude, longitude)) continue;

            double magnitude = properties.optDouble("mag", 0.0);
            String place = properties.optString("place", "Pakistan earthquake event");
            long eventTime = properties.optLong("time", System.currentTimeMillis());

            GeoPoint center = new GeoPoint(latitude, longitude);
            List<String> affectedAreas = buildAffectedAreasFromPlace(place);
            int confidence = calculateConfidence("earthquake", magnitude, null);
            double radiusKm = calculateRadiusKm("earthquake", magnitude);

            zones.add(new HazardZone(
                    "earthquake", formatTypeLabel("earthquake"),
                    buildEarthquakeTitle(place, magnitude),
                    place,
                    buildEarthquakeSummary(magnitude, depthKm, eventTime),
                    "Live earthquake event", "USGS Earthquake Hazards Program",
                    center, radiusKm, 0, confidence,
                    colorForType("earthquake"), markerIconForType("earthquake"),
                    new ArrayList<>(), affectedAreas,
                    true, eventTime
            ));
        }

        return zones;
    }

    private Double extractRainfallMm(JSONObject properties, String text) {
        if (properties != null) {
            Double direct = firstNonNullDouble(
                    toDouble(properties.opt("rainfallMm")),
                    toDouble(properties.opt("rainfall_mm")),
                    toDouble(properties.opt("rainfall")),
                    toDouble(properties.opt("precipitationMm")),
                    toDouble(properties.opt("precipitation_mm")),
                    toDouble(properties.opt("precipitation")),
                    null
            );
            if (direct != null) return direct;
        }

        String safeText = safe(text).toLowerCase(Locale.US);
        int mmIndex = safeText.indexOf("mm");
        if (mmIndex > 0) {
            int start = mmIndex - 1;
            while (start >= 0 && (Character.isDigit(safeText.charAt(start)) || safeText.charAt(start) == '.')) {
                start--;
            }
            String number = safeText.substring(start + 1, mmIndex).trim();
            try {
                return Double.parseDouble(number);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private long extractEventTime(JSONObject properties) {
        if (properties == null) return 0;
        // Try common time fields
        long time = properties.optLong("time", 0);
        if (time > 0) return time;
        time = properties.optLong("eventTime", 0);
        if (time > 0) return time;
        time = properties.optLong("date", 0);
        if (time > 0) return time;
        // Try ISO date string
        String dateStr = properties.optString("date", "");
        if (!dateStr.isEmpty()) return parseIsoDate(dateStr);
        dateStr = properties.optString("eventTime", "");
        if (!dateStr.isEmpty()) return parseIsoDate(dateStr);
        return 0;
    }

    private long parseIsoDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return 0;
        try {
            // Try ISO 8601 format: 2025-06-14T10:30:00Z
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            Date date = sdf.parse(dateStr.replace("Z", "").replace("+00:00", ""));
            return date != null ? date.getTime() : 0;
        } catch (Exception e) {
            try {
                // Try date-only format: 2025-06-14
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                Date date = sdf.parse(dateStr);
                return date != null ? date.getTime() : 0;
            } catch (Exception e2) {
                return 0;
            }
        }
    }

    // ── Confidence & Radius Calculation ───────────────────────────────────

    private int calculateConfidence(String type, double magnitude, Double rainfallMm) {
        switch (normalizeType(type)) {
            case "earthquake":
                // Logarithmic scale: M2=72, M3=78, M4=84, M5=90, M6=95 (capped)
                return (int) Math.min(95, 60 + (Math.log10(Math.max(magnitude, 1.0) + 1) * 18));
            case "flood":
                int base = 70;
                if (rainfallMm != null && rainfallMm > 0) base += 10;
                return base;
            case "landslide":
                return 68;
            case "fire":
                return 65;
            default:
                return 70;
        }
    }

    private double calculateRadiusKm(String type, double magnitude) {
        switch (normalizeType(type)) {
            case "earthquake":
                return Math.max(18.0, 12.0 + (magnitude * 14.0));
            case "flood":
                return 30.0;
            case "landslide":
                return 20.0;
            case "fire":
                return 25.0;
            default:
                return 25.0;
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────

    private void renderZones(List<HazardZone> zones) {
        clearHazards();

        for (HazardZone zone : zones) {
            addZoneOverlay(zone);
        }

        if (!zones.isEmpty()) {
            setSelectedZone(zones.get(0), false);
        }

        if (mapView != null) {
            mapView.invalidate();
        }
    }

    private void addZoneOverlay(HazardZone zone) {
        if (mapView == null) return;

        if (zone.polygonPoints.size() >= 3) {
            addPolygonLayer(zone, zone.polygonPoints, 150, 84, 4f);
            addPolygonLayer(zone, scalePolygon(zone.polygonPoints, zone.center, 0.78), 185, 104, 3f);
            addPolygonLayer(zone, scalePolygon(zone.polygonPoints, zone.center, 0.58), 220, 140, 2f);
        } else {
            addRingPolygon(zone, zone.radiusKm * 1.25, 145, 72, 4f);
            addRingPolygon(zone, zone.radiusKm * 0.85, 180, 96, 3f);
            addRingPolygon(zone, zone.radiusKm * 0.42, 230, 128, 2f);
        }

        Marker marker = new Marker(mapView);
        marker.setPosition(zone.center);
        marker.setTitle(zone.title);
        marker.setSubDescription(zone.summary);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setIcon(ContextCompat.getDrawable(this, zone.markerIconRes));
        marker.setOnMarkerClickListener((clickedMarker, clickedMapView) -> {
            setSelectedZone(zone, true);
            return true;
        });

        mapView.getOverlays().add(marker);
        hazardMarkers.add(marker);
    }

    private void addRingPolygon(HazardZone zone, double radiusKm, int fillAlpha, int strokeAlpha, float strokeWidth) {
        Polygon polygon = new Polygon(mapView);
        polygon.setPoints(buildCircle(zone.center.getLatitude(), zone.center.getLongitude(), radiusKm));
        polygon.setFillColor(applyAlpha(zone.color, fillAlpha));
        polygon.setStrokeColor(applyAlpha(zone.color, strokeAlpha));
        polygon.setStrokeWidth(strokeWidth);
        polygon.setOnClickListener((poly, clickedMap, eventPos) -> {
            setSelectedZone(zone, true);
            return true;
        });

        mapView.getOverlays().add(polygon);
        hazardPolygons.add(polygon);
    }

    private void addPolygonLayer(HazardZone zone, List<GeoPoint> points, int fillAlpha, int strokeAlpha, float strokeWidth) {
        if (points.size() < 3) return;

        Polygon polygon = new Polygon(mapView);
        polygon.setPoints(closePolygon(points));
        polygon.setFillColor(applyAlpha(zone.color, fillAlpha));
        polygon.setStrokeColor(applyAlpha(zone.color, strokeAlpha));
        polygon.setStrokeWidth(strokeWidth);
        polygon.setOnClickListener((poly, clickedMap, eventPos) -> {
            setSelectedZone(zone, true);
            return true;
        });

        mapView.getOverlays().add(polygon);
        hazardPolygons.add(polygon);
    }

    private List<GeoPoint> buildCircle(double latitude, double longitude, double radiusKm) {
        List<GeoPoint> points = new ArrayList<>();
        double radiusMeters = radiusKm * 1000.0;
        double latitudeRadians = Math.toRadians(latitude);
        double latitudeDelta = radiusMeters / 111320.0;
        double longitudeDelta = radiusMeters / (111320.0 * Math.max(Math.cos(latitudeRadians), 0.2));

        int steps = 48;
        for (int index = 0; index <= steps; index++) {
            double angle = (2.0 * Math.PI * index) / steps;
            double pointLatitude = latitude + (latitudeDelta * Math.sin(angle));
            double pointLongitude = longitude + (longitudeDelta * Math.cos(angle));
            points.add(new GeoPoint(pointLatitude, pointLongitude));
        }
        return points;
    }

    // ── Selected Zone Detail Panel ────────────────────────────────────────

    private void setSelectedZone(HazardZone zone, boolean animateMap) {
        boolean isLiveZone = safe(zone.source).toLowerCase(Locale.US).contains("usgs")
                || safe(zone.source).toLowerCase(Locale.US).contains("eonet")
                || safe(zone.source).toLowerCase(Locale.US).contains("live");

        if (selectedTypeChip != null) {
            selectedTypeChip.setText(zone.typeLabel.toUpperCase(Locale.US));
            selectedTypeChip.setBackgroundTintList(ColorStateList.valueOf(zone.color));
        }
        if (selectedTitle != null) {
            selectedTitle.setText(zone.title);
        }
        if (selectedSummary != null) {
            selectedSummary.setText(zone.area + " • " + zone.summary);
        }
        if (selectedDisclaimer != null) {
            selectedDisclaimer.setVisibility(View.GONE);
            selectedDisclaimer.setText("");
        }
        if (selectedTiming != null) {
            selectedTiming.setText(buildTimingLabel(zone));
        }
        if (selectedStatus != null) {
            selectedStatus.setText(isLiveZone ? "Live data" : zone.status);
        }
        if (selectedConfidence != null) {
            selectedConfidence.setText(String.format(Locale.US, "%d%% confidence", zone.confidence));
        }
        if (selectedDetails != null) {
            selectedDetails.setText(buildDetailText(zone));
        }

        // Populate affected areas list
        if (affectedAreasList != null) {
            affectedAreasList.removeAllViews();
            if (!zone.affectedAreas.isEmpty()) {
                for (String area : zone.affectedAreas) {
                    TextView areaView = new TextView(this);
                    areaView.setText("• " + area);
                    areaView.setTextColor(ContextCompat.getColor(HazardAlertMapActivity.this, R.color.text_secondary));
                    areaView.setTextSize(11);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    params.setMargins(0, 4, 0, 4);
                    areaView.setLayoutParams(params);
                    affectedAreasList.addView(areaView);
                }
            } else {
                TextView noAreasView = new TextView(this);
                noAreasView.setText("No specific areas identified");
                noAreasView.setTextColor(ContextCompat.getColor(HazardAlertMapActivity.this, R.color.text_tertiary));
                noAreasView.setTextSize(11);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(0, 4, 0, 4);
                noAreasView.setLayoutParams(params);
                affectedAreasList.addView(noAreasView);
            }
        }

        if (animateMap && mapView != null) {
            mapView.getController().animateTo(zone.center);
            mapView.getController().setZoom(Math.max(mapView.getZoomLevelDouble(), 6.5));
            showZoneDialog(zone);
        }
    }

    private String buildTimingLabel(HazardZone zone) {
        if (zone == null) return "Updated live";

        // If we have an actual event timestamp, show it
        if (zone.eventTime > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US);
            return "Occurred: " + sdf.format(new Date(zone.eventTime));
        }

        // Try to extract from summary
        String summary = safe(zone.summary);
        String updatedAt = extractTimeMarker(summary, "Updated");
        if (!updatedAt.isEmpty()) return "Updated " + updatedAt;

        String observedAt = extractTimeMarker(summary, "Observed");
        if (!observedAt.isEmpty()) return "Observed " + observedAt;

        if (zone.minutesUntil > 0) {
            return String.format(Locale.US, "%d min", zone.minutesUntil);
        }

        return "Observed live";
    }

    private String buildDetailText(HazardZone zone) {
        if (zone == null) return "Source: live feed";

        String source = safe(zone.source).isEmpty() ? "live feed" : zone.source;
        String summary = safe(zone.summary);

        if (zone.type.equals("earthquake")) {
            String depth = extractMeasurement(summary, "Depth");
            if (!depth.isEmpty()) {
                return "Origin depth: " + depth + " • Source: " + source;
            }
        }

        if (zone.type.equals("flood")) {
            String rain = extractMeasurement(summary, "Rainfall recorded");
            if (rain.isEmpty()) rain = extractMeasurement(summary, "Rainfall");
            if (!rain.isEmpty()) {
                return "Rainfall: " + rain + " • Source: " + source;
            }
        }

        return "Source: " + source;
    }

    private String extractMeasurement(String text, String label) {
        String safeText = safe(text);
        String marker = label + ":";
        int index = safeText.indexOf(marker);
        if (index >= 0) {
            String tail = safeText.substring(index + marker.length()).trim();
            int endIndex = tail.indexOf(".");
            if (endIndex >= 0) tail = tail.substring(0, endIndex).trim();
            return tail;
        }
        return "";
    }

    private String extractTimeMarker(String text, String label) {
        String safeText = safe(text);
        String marker = label + " ";
        int index = safeText.indexOf(marker);
        if (index >= 0) {
            String tail = safeText.substring(index + marker.length()).trim();
            int endIndex = tail.indexOf(".");
            if (endIndex >= 0) tail = tail.substring(0, endIndex).trim();
            return tail;
        }
        return "";
    }

    // ── Zone Dialog ───────────────────────────────────────────────────────

    private void showZoneDialog(HazardZone zone) {
        String timing;
        if (zone.eventTime > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US);
            timing = "Occurred: " + sdf.format(new Date(zone.eventTime));
        } else if (zone.minutesUntil <= 0) {
            timing = "Ongoing now";
        } else {
            timing = String.format(Locale.US, "Likely in %d minutes", zone.minutesUntil);
        }

        String message = zone.area + "\n\n"
                + zone.summary + "\n\n"
                + timing + "\n"
                + zone.status + "\n"
                + zone.source;

        new AlertDialog.Builder(this)
                .setTitle(zone.title)
                .setMessage(message)
                .setPositiveButton("Center map", (dialog, which) -> focusOnZone(zone))
                .setNeutralButton("Open in Maps", (dialog, which) -> openInMaps(zone))
                .setNegativeButton("Close", null)
                .show();
    }

    private void focusOnZone(HazardZone zone) {
        if (mapView == null) return;
        mapView.getController().animateTo(zone.center);
        mapView.getController().setZoom(Math.max(mapView.getZoomLevelDouble(), 7.0));
    }

    private void openInMaps(HazardZone zone) {
        String query = String.format(Locale.US, "%f,%f", zone.center.getLatitude(), zone.center.getLongitude());
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

    // ── Fallback Zones ────────────────────────────────────────────────────

    private void clearHazards() {
        if (mapView == null) return;
        mapView.getOverlays().removeAll(hazardMarkers);
        mapView.getOverlays().removeAll(hazardPolygons);
        hazardMarkers.clear();
        hazardPolygons.clear();
    }

    private HazardZone parseZone(DocumentSnapshot document) {
        String type = normalizeType(firstNonBlank(
                toStringValue(document.get("type")),
                toStringValue(document.get("hazardType")),
                toStringValue(getNestedValue(document, "hazard", "type")),
                "earthquake"
        ));

        String title = firstNonBlank(
                toStringValue(document.get("title")),
                toStringValue(getNestedValue(document, "properties", "title")),
                defaultTitle(type)
        );
        String area = firstNonBlank(
                toStringValue(document.get("area")),
                toStringValue(document.get("district")),
                toStringValue(getNestedValue(document, "properties", "area")),
                toStringValue(getNestedValue(document, "properties", "district")),
                defaultArea(type)
        );
        String summary = firstNonBlank(
                toStringValue(document.get("summary")),
                toStringValue(document.get("details")),
                toStringValue(getNestedValue(document, "properties", "summary")),
                defaultSummary(type)
        );

        String status = firstNonBlank(
                toStringValue(document.get("status")),
                toStringValue(getNestedValue(document, "forecast", "status")),
                defaultStatus(type)
        );
        String source = firstNonBlank(
                toStringValue(document.get("source")),
                toStringValue(getNestedValue(document, "forecast", "source")),
                "Model overlay"
        );

        Integer minutesUntil = firstNonNullInt(
                toInt(document.get("minutesUntil")),
                toInt(getNestedValue(document, "forecast", "minutesUntil")),
                toInt(getNestedValue(document, "forecast", "etaMinutes")),
                0
        );
        Integer confidence = firstNonNullInt(
                toInt(document.get("confidence")),
                toInt(getNestedValue(document, "forecast", "confidence")),
                70
        );

        List<String> affectedAreas = parseAffectedAreas(
                document.get("affectedAreas"),
                document.get("affectedDistricts"),
                getNestedValue(document, "properties", "affectedAreas"),
                getNestedValue(document, "properties", "affectedDistricts")
        );

        boolean isActive = toBoolean(document.get("isActive"), true)
                && !toBoolean(document.get("isArchived"), false)
                && !"resolved".equalsIgnoreCase(status)
                && !"expired".equalsIgnoreCase(status);

        // Extract event time from Firestore
        long eventTime = 0;
        Object eventTimeObj = document.get("eventTime");
        if (eventTimeObj instanceof Number) {
            eventTime = ((Number) eventTimeObj).longValue();
        }
        if (eventTime == 0) {
            Object createdAtObj = document.get("createdAt");
            if (createdAtObj instanceof Number) {
                eventTime = ((Number) createdAtObj).longValue();
            } else if (createdAtObj instanceof com.google.firebase.Timestamp) {
                eventTime = ((com.google.firebase.Timestamp) createdAtObj).toDate().getTime();
            }
        }

        GeoPoint center = extractCenter(document);
        List<GeoPoint> polygonPoints = extractPolygon(
                document.get("polygonPoints"),
                getNestedValue(document, "geometry", "polygon"),
                getNestedValue(document, "geometry", "points"),
                getNestedValue(document, "affectedArea", "points")
        );

        if (center == null && !polygonPoints.isEmpty()) {
            center = centroid(polygonPoints);
        }
        if (center == null || !isInsidePakistan(center.getLatitude(), center.getLongitude())) {
            return null;
        }

        double radiusKm = firstNonNullDouble(
                toDouble(document.get("radiusKm")),
                toDouble(getNestedValue(document, "geometry", "radiusKm")),
                toDouble(getNestedValue(document, "geometry", "radius")),
                estimateRadiusKm(center, polygonPoints),
                30.0
        );

        return new HazardZone(
                type, formatTypeLabel(type), title, area, summary, status, source,
                center, radiusKm, minutesUntil, confidence,
                colorForType(type), markerIconForType(type),
                polygonPoints, affectedAreas, isActive, eventTime
        );
    }

    private List<String> parseAffectedAreas(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate instanceof List<?>) {
                List<?> listCand = (List<?>) candidate;
                List<String> areas = new ArrayList<>();
                for (Object item : listCand) {
                    String area = toStringValue(item);
                    if (area != null && !area.isEmpty()) areas.add(area);
                }
                if (!areas.isEmpty()) return areas;
            }
        }
        return new ArrayList<>();
    }

    private List<HazardZone> buildFallbackZones() {
        List<HazardZone> zones = new ArrayList<>();
        zones.add(new HazardZone(
                "earthquake", formatTypeLabel("earthquake"),
                "Northern seismic watch", "Gilgit-Baltistan and upper valleys",
                "Stronger aftershocks are most likely near active fault lines and steep slopes.",
                "Developing seismic pressure", "Demo preview",
                new GeoPoint(35.852, 74.640), 84, 42, 78,
                colorForType("earthquake"), markerIconForType("earthquake"),
                new ArrayList<>(), arrayListOf("Gilgit", "Hunza", "Skardu", "Diamer"),
                true, System.currentTimeMillis()
        ));
        zones.add(new HazardZone(
                "flood", formatTypeLabel("flood"),
                "Sindh flood corridor", "Lower Sindh river plains",
                "Low-lying districts may see runoff, waterlogging, and rising channels after heavy rain.",
                "Active rain-fed flood risk", "Demo preview",
                new GeoPoint(25.580, 68.470), 105, 24, 82,
                colorForType("flood"), markerIconForType("flood"),
                new ArrayList<>(), arrayListOf("Hyderabad", "Thatta", "Badin", "Sukkur"),
                true, System.currentTimeMillis()
        ));
        zones.add(new HazardZone(
                "fire", formatTypeLabel("fire"),
                "Forest fire watch", "Margalla hills and nearby forest belts",
                "Dry winds and high heat can keep smoke and flame spread active in exposed ridges.",
                "Ongoing fire spread", "Demo preview",
                new GeoPoint(33.732, 73.079), 38, 0, 75,
                colorForType("fire"), markerIconForType("fire"),
                new ArrayList<>(), arrayListOf("Islamabad", "Rawalpindi", "Murree"),
                true, System.currentTimeMillis()
        ));
        zones.add(new HazardZone(
                "landslide", formatTypeLabel("landslide"),
                "Mountain slope instability", "Khyber Pakhtunkhwa and Karakoram routes",
                "Rain-soaked slopes may fail on sharp turns, cut roads, or block relief access.",
                "High slope instability", "Demo preview",
                new GeoPoint(35.260, 75.650), 72, 55, 69,
                colorForType("landslide"), markerIconForType("landslide"),
                new ArrayList<>(), arrayListOf("Chitral", "Swat", "Kohistan", "Mansehra"),
                true, System.currentTimeMillis()
        ));
        return zones;
    }

    private List<String> arrayListOf(String... items) {
        List<String> list = new ArrayList<>();
        for (String item : items) list.add(item);
        return list;
    }

    // ── Geo Utilities ─────────────────────────────────────────────────────

    private GeoPoint extractCenter(DocumentSnapshot document) {
        Object centerRoot = document.get("center");
        GeoPoint center = toGeoPoint(centerRoot);
        if (center != null) return center;

        center = toGeoPoint(getNestedValue(document, "geometry", "center"));
        if (center != null) return center;

        Double latitude = firstNonNullDouble(
                toDouble(document.get("latitude")),
                toDouble(document.get("lat")),
                toDouble(getNestedValue(document, "geometry", "latitude")),
                toDouble(getNestedValue(document, "geometry", "lat")),
                toDouble(getNestedValue(document, "center", "latitude")),
                toDouble(getNestedValue(document, "center", "lat")),
                null
        );
        Double longitude = firstNonNullDouble(
                toDouble(document.get("longitude")),
                toDouble(document.get("lng")),
                toDouble(document.get("lon")),
                toDouble(getNestedValue(document, "geometry", "longitude")),
                toDouble(getNestedValue(document, "geometry", "lng")),
                toDouble(getNestedValue(document, "center", "longitude")),
                toDouble(getNestedValue(document, "center", "lng")),
                null
        );

        if (latitude == null || longitude == null) return null;
        return new GeoPoint(latitude, longitude);
    }

    private List<GeoPoint> extractPolygon(Object... candidates) {
        List<GeoPoint> points = new ArrayList<>();
        for (Object candidate : candidates) {
            points = parsePoints(candidate);
            if (points.size() >= 3) return points;
        }
        return new ArrayList<>();
    }

    private List<GeoPoint> parsePoints(Object rawPoints) {
        List<GeoPoint> points = new ArrayList<>();
        if (!(rawPoints instanceof List<?>)) return points;

        for (Object item : (List<?>) rawPoints) {
            GeoPoint point = toGeoPoint(item);
            if (point != null && isInsidePakistan(point.getLatitude(), point.getLongitude())) {
                points.add(point);
            }
        }
        return points;
    }

    private GeoPoint toGeoPoint(Object rawValue) {
        if (rawValue instanceof com.google.firebase.firestore.GeoPoint) {
            com.google.firebase.firestore.GeoPoint point = (com.google.firebase.firestore.GeoPoint) rawValue;
            return new GeoPoint(point.getLatitude(), point.getLongitude());
        }
        if (rawValue instanceof GeoPoint) return (GeoPoint) rawValue;
        if (!(rawValue instanceof Map<?, ?>)) return null;

        Map<?, ?> map = (Map<?, ?>) rawValue;
        Double latitude = firstNonNullDouble(
                toDouble(map.get("latitude")),
                toDouble(map.get("lat")),
                null
        );
        Double longitude = firstNonNullDouble(
                toDouble(map.get("longitude")),
                toDouble(map.get("lng")),
                toDouble(map.get("lon")),
                null
        );

        if (latitude == null || longitude == null) return null;
        return new GeoPoint(latitude, longitude);
    }

    private List<GeoPoint> scalePolygon(List<GeoPoint> points, GeoPoint center, double factor) {
        List<GeoPoint> scaled = new ArrayList<>();
        for (GeoPoint point : points) {
            double scaledLatitude = center.getLatitude() + ((point.getLatitude() - center.getLatitude()) * factor);
            double scaledLongitude = center.getLongitude() + ((point.getLongitude() - center.getLongitude()) * factor);
            scaled.add(new GeoPoint(scaledLatitude, scaledLongitude));
        }
        return scaled;
    }

    private List<GeoPoint> closePolygon(List<GeoPoint> points) {
        List<GeoPoint> closed = new ArrayList<>(points);
        if (!closed.isEmpty()) {
            GeoPoint first = closed.get(0);
            GeoPoint last = closed.get(closed.size() - 1);
            if (first.getLatitude() != last.getLatitude() || first.getLongitude() != last.getLongitude()) {
                closed.add(first);
            }
        }
        return closed;
    }

    private GeoPoint centroid(List<GeoPoint> points) {
        if (points.isEmpty()) return null;
        double sumLatitude = 0;
        double sumLongitude = 0;
        for (GeoPoint point : points) {
            sumLatitude += point.getLatitude();
            sumLongitude += point.getLongitude();
        }
        return new GeoPoint(sumLatitude / points.size(), sumLongitude / points.size());
    }

    private double estimateRadiusKm(GeoPoint center, List<GeoPoint> polygonPoints) {
        if (center == null || polygonPoints.isEmpty()) return 0;
        double maxDistance = 0;
        for (GeoPoint point : polygonPoints) {
            maxDistance = Math.max(maxDistance, distanceKm(center, point));
        }
        return maxDistance;
    }

    private double distanceKm(GeoPoint a, GeoPoint b) {
        double earthRadiusKm = 6371.0;
        double latDistance = Math.toRadians(b.getLatitude() - a.getLatitude());
        double lonDistance = Math.toRadians(b.getLongitude() - a.getLongitude());
        double startLat = Math.toRadians(a.getLatitude());
        double endLat = Math.toRadians(b.getLatitude());

        double haversine = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(startLat) * Math.cos(endLat)
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double centralAngle = 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
        return earthRadiusKm * centralAngle;
    }

    // ── Type Helpers ──────────────────────────────────────────────────────

    private int markerIconForType(String type) {
        switch (normalizeType(type)) {
            case "flood": return R.drawable.ic_hazard_marker_flood;
            case "fire": return R.drawable.ic_hazard_marker_fire;
            case "landslide": return R.drawable.ic_hazard_marker_landslide;
            case "earthquake":
            default: return R.drawable.ic_hazard_marker_earthquake;
        }
    }

    private int colorForType(String type) {
        switch (normalizeType(type)) {
            case "flood": return Color.parseColor("#3D8DFF");
            case "fire": return Color.parseColor("#F29A3F");
            case "landslide": return Color.parseColor("#8B6A4B");
            case "earthquake":
            default: return Color.parseColor("#E75B5B");
        }
    }

    private String defaultTitle(String type) {
        switch (normalizeType(type)) {
            case "flood": return "Flood warning zone";
            case "fire": return "Forest fire watch";
            case "landslide": return "Landslide watch zone";
            case "earthquake":
            default: return "Earthquake watch zone";
        }
    }

    private String defaultArea(String type) {
        switch (normalizeType(type)) {
            case "flood": return "Pakistan floodplain";
            case "fire": return "Forest belt";
            case "landslide": return "Mountain corridor";
            case "earthquake":
            default: return "Seismic corridor";
        }
    }

    private String defaultSummary(String type) {
        switch (normalizeType(type)) {
            case "flood": return "Forecast rain bands and river runoff may affect nearby settlements and roads.";
            case "fire": return "Dry conditions and heat can keep an active fire front moving through forest edges.";
            case "landslide": return "Steep and wet slopes may fail if the current rain pattern continues.";
            case "earthquake":
            default: return "Seismic activity is being watched for a likely event window and aftershock spread.";
        }
    }

    private String defaultStatus(String type) {
        switch (normalizeType(type)) {
            case "flood": return "Ongoing flood risk";
            case "fire": return "Active fire spread";
            case "landslide": return "Developing landslide risk";
            case "earthquake":
            default: return "Forecast watch";
        }
    }

    private String normalizeType(String rawType) {
        String normalized = safe(rawType).toLowerCase(Locale.US).replace(' ', '_').replace('-', '_');
        if (normalized.contains("earthquake") || normalized.equals("quake") || normalized.equals("seismic")) return "earthquake";
        if (normalized.contains("flood") || normalized.contains("monsoon")) return "flood";
        if (normalized.contains("fire") || normalized.contains("wildfire") || normalized.contains("forest_fire")) return "fire";
        if (normalized.contains("landslide") || normalized.contains("mudslide") || normalized.contains("rockfall")) return "landslide";
        return "earthquake";
    }

    private String formatTypeLabel(String type) {
        switch (normalizeType(type)) {
            case "flood": return "Flood";
            case "fire": return "Forest Fire";
            case "landslide": return "Landslide";
            case "earthquake":
            default: return "Earthquake";
        }
    }

    // ── General Utilities ─────────────────────────────────────────────────

    private Object getNestedValue(DocumentSnapshot document, String... path) {
        if (path.length == 0) return null;
        Object current = document.get(path[0]);
        for (int index = 1; index < path.length; index++) {
            if (!(current instanceof Map<?, ?>)) return null;
            current = ((Map<?, ?>) current).get(path[index]);
        }
        return current;
    }

    private int applyAlpha(int baseColor, int alpha) {
        int boundedAlpha = Math.max(0, Math.min(255, alpha));
        return Color.argb(boundedAlpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
    }

    private boolean isInsidePakistan(double latitude, double longitude) {
        return latitude <= (PAKISTAN_LAT_NORTH + BUFFER_DEGREES)
                && latitude >= (PAKISTAN_LAT_SOUTH - BUFFER_DEGREES)
                && longitude <= (PAKISTAN_LON_EAST + BUFFER_DEGREES)
                && longitude >= (PAKISTAN_LON_WEST - BUFFER_DEGREES);
    }

    private String readStream(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private void startRefreshLoop() {
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    private void stopRefreshLoop() {
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private Double toDouble(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try { return Double.parseDouble(((String) value).trim()); }
            catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private Integer toInt(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt(((String) value).trim()); }
            catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private boolean toBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) {
            String normalized = ((String) value).trim();
            if ("true".equalsIgnoreCase(normalized)) return true;
            if ("false".equalsIgnoreCase(normalized)) return false;
        }
        return fallback;
    }

    private String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Double firstNonNullDouble(Double... values) {
        for (Double value : values) { if (value != null) return value; }
        return null;
    }

    private Integer firstNonNullInt(Integer first, Integer second, Integer fallback) {
        if (first != null) return first;
        if (second != null) return second;
        return fallback;
    }

    private Integer firstNonNullInt(Integer first, Integer second, Integer third, Integer fallback) {
        if (first != null) return first;
        if (second != null) return second;
        if (third != null) return third;
        return fallback;
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            String value = safe(candidate);
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildEarthquakeTitle(String place, double magnitude) {
        String areaLabel = place == null || place.trim().isEmpty() ? "Pakistan" : place.trim();
        return String.format(Locale.US, "M%.1f earthquake near %s", magnitude, areaLabel);
    }

    private String buildEarthquakeSummary(double magnitude, double depthKm, long eventTime) {
        String magnitudeText = String.format(Locale.US, "Magnitude %.1f", magnitude);
        String depthText = Double.isNaN(depthKm) ? "Depth not reported" : String.format(Locale.US, "Depth %.1f km", depthKm);
        String timeText = String.format(Locale.US, "Updated %tR", eventTime);
        return magnitudeText + ". " + depthText + ". " + timeText + ".";
    }

    private List<String> buildAffectedAreasFromPlace(String place) {
        List<String> areas = new ArrayList<>();
        if (place == null || place.trim().isEmpty()) return areas;
        areas.add(place.trim());
        int ofIndex = place.toLowerCase(Locale.US).indexOf(" of ");
        if (ofIndex >= 0 && ofIndex + 4 < place.length()) {
            areas.add(place.substring(ofIndex + 4).trim());
        }
        return areas;
    }

    // ── HazardZone Model ──────────────────────────────────────────────────

    private static final class HazardZone {
        final String type;
        final String typeLabel;
        final String title;
        final String area;
        final String summary;
        final String status;
        final String source;
        final GeoPoint center;
        final double radiusKm;
        final int minutesUntil;
        final int confidence;
        final int color;
        final int markerIconRes;
        final List<GeoPoint> polygonPoints;
        final List<String> affectedAreas;
        final boolean isActive;
        final long eventTime;

        HazardZone(String type, String typeLabel, String title, String area, String summary,
                   String status, String source, GeoPoint center, double radiusKm,
                   int minutesUntil, int confidence, int color, int markerIconRes,
                   List<GeoPoint> polygonPoints, List<String> affectedAreas,
                   boolean isActive, long eventTime) {
            this.type = type;
            this.typeLabel = typeLabel;
            this.title = title;
            this.area = area;
            this.summary = summary;
            this.status = status;
            this.source = source;
            this.center = center;
            this.radiusKm = radiusKm;
            this.minutesUntil = minutesUntil;
            this.confidence = confidence;
            this.color = color;
            this.markerIconRes = markerIconRes;
            this.polygonPoints = polygonPoints;
            this.affectedAreas = affectedAreas;
            this.isActive = isActive;
            this.eventTime = eventTime;
        }
    }
}
