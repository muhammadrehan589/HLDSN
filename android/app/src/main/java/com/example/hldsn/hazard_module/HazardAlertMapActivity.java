package com.example.hldsn.hazard_module;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.hldsn.R;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HazardAlertMapActivity extends AppCompatActivity {

    private static final String TAG = "HazardAlertMap";
    private static final String HAZARD_COLLECTION = "hazard_alert_zones";
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000L;
    private static final String USGS_EARTHQUAKE_URL = "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&minlatitude=23.5&maxlatitude=37.2&minlongitude=60.8&maxlongitude=77.9&orderby=time&limit=25";
    // Optional live feed endpoints. Configure one or more GeoJSON feeds for flood/landslide/fire.
    private static final String FLOOD_GEOJSON_URL = "https://www.gdacs.org/gdacsapi/api/events/geteventlist/format/json"; // GDACS event list (filtered locally for Pakistan)
    private static final String LANDSLIDE_GEOJSON_URL = "https://eonet.gsfc.nasa.gov/api/v3/events?status=open&category=landslides&limit=50";
    private static final String FIRE_GEOJSON_URL = "https://eonet.gsfc.nasa.gov/api/v3/events?status=open&category=wildfires&limit=50";
    private static final GeoPoint PAKISTAN_CENTER = new GeoPoint(30.3753, 69.3451);
    private static final BoundingBox PAKISTAN_BOUNDS = new BoundingBox(
            37.2,
            77.9,
            23.5,
            60.8
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
    private boolean headerExpanded = true;
    private float headerTouchStartX;
    private float headerTouchStartY;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
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

        configureMapView();
        configureHeaderCardGestures();

        ImageView backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        loadHazardZones();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        startRefreshLoop();
    }

    @Override
    protected void onPause() {
        stopRefreshLoop();
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
        mapController.setZoom(5.8);
        mapController.setCenter(PAKISTAN_CENTER);

        mapView.post(() -> mapView.zoomToBoundingBox(PAKISTAN_BOUNDS, true));
    }

    private void configureHeaderCardGestures() {
        if (headerCard == null) {
            return;
        }

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
            headerTitle.setText(expanded ? R.string.hazard_alert_title : R.string.hazard_alert_title);
        }
    }

    private void loadHazardZones() {
        // Fetch live feeds (earthquake + optional flood/landslide/fire) then merge with Firestore zones.
        fetchLiveEarthquakeZones(liveEarthquakeZones -> {
            fetchOptionalGeoJsonZones(FLOOD_GEOJSON_URL, "flood", floodZones -> {
                fetchOptionalGeoJsonZones(LANDSLIDE_GEOJSON_URL, "landslide", landslideZones -> {
                    fetchOptionalGeoJsonZones(FIRE_GEOJSON_URL, "fire", fireZones -> {
                        db.collection(HAZARD_COLLECTION)
                                .get()
                                .addOnSuccessListener(querySnapshot -> {
                                    List<HazardZone> firestoreZones = new ArrayList<>();
                                    List<HazardZone> firestoreEarthquakeFallback = new ArrayList<>();

                                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                                        HazardZone zone = parseZone(document);
                                        if (zone == null || !zone.isActive) {
                                            continue;
                                        }

                                        if ("earthquake".equals(zone.type)) {
                                            firestoreEarthquakeFallback.add(zone);
                                        } else {
                                            firestoreZones.add(zone);
                                        }
                                    }

                                    List<HazardZone> zones = mergeZonesForDisplay(
                                            liveEarthquakeZones,
                                            floodZones,
                                            landslideZones,
                                            fireZones,
                                            firestoreZones,
                                            firestoreEarthquakeFallback
                                    );

                                    Log.d(TAG, "Merged zones: " + zones.size() + " total (eq:" + liveEarthquakeZones.size()
                                            + " flood:" + floodZones.size() + " landslide:" + landslideZones.size()
                                            + " fire:" + fireZones.size() + " firestore:" + firestoreZones.size() + ")");

                                    renderZones(zones);
                                })
                                .addOnFailureListener(error -> {
                                    Log.w(TAG, "Failed to load hazard zones", error);
                                    List<HazardZone> zones = mergeZonesForDisplay(
                                            liveEarthquakeZones,
                                            floodZones,
                                            landslideZones,
                                            fireZones,
                                            new ArrayList<>(),
                                            new ArrayList<>()
                                    );
                                    renderZones(zones);
                                });
                    });
                });
            });
        });
    }

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
            if (zone != null) {
                zones.add(zone);
            }
        }

        if (zones.isEmpty()) {
            zones.addAll(firestoreEarthquakeFallback);
        }

        return dedupeAndCapByType(zones, 4);
    }

    private List<HazardZone> limitMostRecentPerType(List<HazardZone> zones, int limitPerType) {
        Map<String, List<HazardZone>> grouped = new LinkedHashMap<>();
        for (HazardZone zone : zones) {
            if (zone == null) {
                continue;
            }
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
            if (zone == null) {
                continue;
            }
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

    private void fetchLiveEarthquakeZones(LiveEarthquakeCallback callback) {
        networkExecutor.execute(() -> {
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
                if (connection != null) {
                    connection.disconnect();
                }
            }

            List<HazardZone> finalZones = zones;
            Log.d(TAG, "Fetched " + finalZones.size() + " earthquake zones from USGS");
            runOnUiThread(() -> callback.onLoaded(finalZones));
        });
    }

    private void fetchOptionalGeoJsonZones(String url, String type, LiveEarthquakeCallback callback) {
        if (url == null || url.trim().isEmpty()) {
            callback.onLoaded(new ArrayList<>());
            return;
        }

        networkExecutor.execute(() -> {
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
                        // Route GDACS flood through dedicated parser
                        if ("flood".equalsIgnoreCase(type) && url.contains("gdacs")) {
                            zones = parseGdacsFloodZones(responseBody);
                        } else {
                            zones = parseGenericGeoJsonZones(responseBody, type);
                        }
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

            List<HazardZone> finalZones = zones;
            Log.d(TAG, "Fetched " + finalZones.size() + " " + type + " zones from live feed");
            runOnUiThread(() -> callback.onLoaded(finalZones));
        });
    }

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
                String combined = (title + " " + place + " " + countryHint + " " + summary).toLowerCase(java.util.Locale.US);
                boolean mentionsPakistan = combined.contains("pakistan");
                if (!(inPakistan || mentionsPakistan)) continue;

                GeoPoint center = new GeoPoint(lat, lon);
                int confidence = 70;
                double radiusKm = 30.0;
                if (magnitude > 0) {
                    confidence = (int) Math.max(55, Math.min(100, 60 + (magnitude * 8)));
                    radiusKm = Math.max(12.0, 10.0 + (magnitude * 10.0));
                }

                if (rainfallMm != null) {
                    summary = String.format(Locale.US, "Rainfall recorded: %.1f mm. %s", rainfallMm, summary);
                }

                zones.add(new HazardZone(
                        type,
                        formatTypeLabel(type),
                        title.isEmpty() ? (type + " event") : title,
                        place,
                        summary.isEmpty() ? defaultSummary(type) : summary,
                        "Live feed",
                        "External feed",
                        center,
                        radiusKm,
                        0,
                        confidence,
                        colorForType(type),
                        markerIconForType(type),
                        new ArrayList<>(),
                        buildAffectedAreasFromPlace(place),
                        true
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
                    // Could be [lon, lat] or [[lon, lat], ...]
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
                String combined = (title + " " + summary).toLowerCase(java.util.Locale.US);
                boolean mentionsPakistan = combined.contains("pakistan");
                if (!(inPakistan || mentionsPakistan)) continue;

                GeoPoint center = new GeoPoint(lat, lon);
                int confidence = 72;
                double radiusKm = 18.0;

                zones.add(new HazardZone(
                        type,
                        formatTypeLabel(type),
                        title.isEmpty() ? (type + " event") : title,
                        title,
                        summary.isEmpty() ? defaultSummary(type) : summary,
                        "Live feed",
                        "EONET",
                        center,
                        radiusKm,
                        0,
                        confidence,
                        colorForType(type),
                        markerIconForType(type),
                        new ArrayList<>(),
                        buildAffectedAreasFromPlace(title),
                        true
                ));
            }
            return zones;
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
            if (direct != null) {
                return direct;
            }
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

    private List<HazardZone> parseGdacsFloodZones(String responseBody) throws JSONException {
        List<HazardZone> zones = new ArrayList<>();
        JSONObject root = new JSONObject(responseBody);
        JSONArray events = root.optJSONArray("events");
        if (events == null) {
            return zones;
        }

        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) continue;

            String eventtype = event.optString("eventtype", "");
            // GDACS uses "FL" for floods
            if (!"FL".equalsIgnoreCase(eventtype)) continue;

            double lat = event.optDouble("lat", Double.NaN);
            double lon = event.optDouble("lon", Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) continue;

            boolean inPakistan = isInsidePakistan(lat, lon);
            String eventname = event.optString("eventname", "");
            String description = event.optString("description", "");
            String combined = (eventname + " " + description).toLowerCase(java.util.Locale.US);
            boolean mentionsPakistan = combined.contains("pakistan");
            if (!(inPakistan || mentionsPakistan)) continue;

            GeoPoint center = new GeoPoint(lat, lon);
            int confidence = 75;
            double radiusKm = 25.0;

            zones.add(new HazardZone(
                    "flood",
                    formatTypeLabel("flood"),
                    eventname.isEmpty() ? "Flood event" : eventname,
                    eventname,
                    description.isEmpty() ? defaultSummary("flood") : description,
                    "Live flood alert",
                    "GDACS",
                    center,
                    radiusKm,
                    0,
                    confidence,
                    colorForType("flood"),
                    markerIconForType("flood"),
                    new ArrayList<>(),
                    buildAffectedAreasFromPlace(eventname),
                    true
            ));
        }

        return zones;
    }

    private List<HazardZone> parseUsgsEarthquakeZones(String responseBody) throws JSONException {
        List<HazardZone> zones = new ArrayList<>();
        JSONObject root = new JSONObject(responseBody);
        JSONArray features = root.optJSONArray("features");
        if (features == null) {
            return zones;
        }

        for (int index = 0; index < features.length(); index++) {
            JSONObject feature = features.optJSONObject(index);
            if (feature == null) {
                continue;
            }

            JSONObject properties = feature.optJSONObject("properties");
            JSONObject geometry = feature.optJSONObject("geometry");
            JSONArray coordinates = geometry != null ? geometry.optJSONArray("coordinates") : null;
            if (properties == null || coordinates == null || coordinates.length() < 2) {
                continue;
            }

            double longitude = coordinates.optDouble(0, Double.NaN);
            double latitude = coordinates.optDouble(1, Double.NaN);
            double depthKm = coordinates.length() >= 3 ? coordinates.optDouble(2, Double.NaN) : Double.NaN;
            if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
                continue;
            }

            if (!isInsidePakistan(latitude, longitude)) {
                continue;
            }

            double magnitude = properties.optDouble("mag", 0.0);
            String place = properties.optString("place", "Pakistan earthquake event");
            long eventTime = properties.optLong("time", System.currentTimeMillis());

            GeoPoint center = new GeoPoint(latitude, longitude);
            List<String> affectedAreas = buildAffectedAreasFromPlace(place);
            int confidence = (int) Math.max(55, Math.min(100, 60 + (magnitude * 8)));
            double radiusKm = Math.max(18.0, 12.0 + (magnitude * 14.0));

                zones.add(new HazardZone(
                    "earthquake",
                    formatTypeLabel("earthquake"),
                    buildEarthquakeTitle(place, magnitude),
                    place,
                    buildEarthquakeSummary(magnitude, depthKm, eventTime),
                    "Live earthquake event",
                    "USGS Earthquake Hazards Program",
                    center,
                    radiusKm,
                    0,
                    confidence,
                    colorForType("earthquake"),
                    markerIconForType("earthquake"),
                    new ArrayList<>(),
                    affectedAreas,
                    true
            ));
        }

        return zones;
    }

    private List<String> buildAffectedAreasFromPlace(String place) {
        List<String> areas = new ArrayList<>();
        if (place == null || place.trim().isEmpty()) {
            return areas;
        }

        areas.add(place.trim());
        int ofIndex = place.toLowerCase(Locale.US).indexOf(" of ");
        if (ofIndex >= 0 && ofIndex + 4 < place.length()) {
            areas.add(place.substring(ofIndex + 4).trim());
        }
        return areas;
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
        if (mapView == null) {
            return;
        }

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
        if (points.size() < 3) {
            return;
        }

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

    private void setSelectedZone(HazardZone zone, boolean animateMap) {
        boolean isLiveZone = safe(zone.source).toLowerCase(Locale.US).contains("usgs")
            || safe(zone.source).toLowerCase(Locale.US).contains("gdacs")
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
                    areaView.setTextColor(0xFF406176);
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
                noAreasView.setTextColor(0xFF8EA3B8);
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
        if (zone == null) {
            return "Updated live";
        }

        String summary = safe(zone.summary);
        String updatedAt = extractTimeMarker(summary, "Updated");
        if (!updatedAt.isEmpty()) {
            return "Updated " + updatedAt;
        }

        String observedAt = extractTimeMarker(summary, "Observed");
        if (!observedAt.isEmpty()) {
            return "Observed " + observedAt;
        }

        if (zone.minutesUntil > 0) {
            return String.format(Locale.US, "%d min", zone.minutesUntil);
        }

        return "Observed live";
    }

    private String buildDetailText(HazardZone zone) {
        if (zone == null) {
            return "Source: live feed";
        }

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
            if (rain.isEmpty()) {
                rain = extractMeasurement(summary, "Rainfall");
            }
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
            if (endIndex >= 0) {
                tail = tail.substring(0, endIndex).trim();
            }
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
            if (endIndex >= 0) {
                tail = tail.substring(0, endIndex).trim();
            }
            return tail;
        }
        return "";
    }

    private void showZoneDialog(HazardZone zone) {
        String timing = zone.minutesUntil <= 0
                ? "Ongoing now"
                : String.format(Locale.US, "Likely in %d minutes", zone.minutesUntil);
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
        if (mapView == null) {
            return;
        }
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

    private void clearHazards() {
        if (mapView == null) {
            return;
        }

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
                type,
                formatTypeLabel(type),
                title,
                area,
                summary,
                status,
                source,
                center,
                radiusKm,
                minutesUntil,
                confidence,
                colorForType(type),
                markerIconForType(type),
                polygonPoints,
                affectedAreas,
                isActive
        );
    }

    private List<String> parseAffectedAreas(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate instanceof List<?>) {
                List<?> listCand = (List<?>) candidate;
                List<String> areas = new ArrayList<>();
                for (Object item : listCand) {
                    String area = toStringValue(item);
                    if (area != null && !area.isEmpty()) {
                        areas.add(area);
                    }
                }
                if (!areas.isEmpty()) {
                    return areas;
                }
            }
        }
        return new ArrayList<>();
    }

    private List<HazardZone> buildFallbackZones() {
        List<HazardZone> zones = new ArrayList<>();
        zones.add(new HazardZone(
                "earthquake",
                formatTypeLabel("earthquake"),
                "Northern seismic watch",
                "Gilgit-Baltistan and upper valleys",
                "Stronger aftershocks are most likely near active fault lines and steep slopes.",
                "Developing seismic pressure",
                "Demo preview",
                new GeoPoint(35.852, 74.640),
                84,
                42,
                78,
                colorForType("earthquake"),
                markerIconForType("earthquake"),
                new ArrayList<>(),
                arrayListOf("Gilgit", "Hunza", "Skardu", "Diamer"),
                true
        ));
        zones.add(new HazardZone(
                "flood",
                formatTypeLabel("flood"),
                "Sindh flood corridor",
                "Lower Sindh river plains",
                "Low-lying districts may see runoff, waterlogging, and rising channels after heavy rain.",
                "Active rain-fed flood risk",
                "Demo preview",
                new GeoPoint(25.580, 68.470),
                105,
                24,
                82,
                colorForType("flood"),
                markerIconForType("flood"),
                new ArrayList<>(),
                arrayListOf("Hyderabad", "Thatta", "Badin", "Sukkur"),
                true
        ));
        zones.add(new HazardZone(
                "fire",
                formatTypeLabel("fire"),
                "Forest fire watch",
                "Margalla hills and nearby forest belts",
                "Dry winds and high heat can keep smoke and flame spread active in exposed ridges.",
                "Ongoing fire spread",
                "Demo preview",
                new GeoPoint(33.732, 73.079),
                38,
                0,
                75,
                colorForType("fire"),
                markerIconForType("fire"),
                new ArrayList<>(),
                arrayListOf("Islamabad", "Rawalpindi", "Murree"),
                true
        ));
        zones.add(new HazardZone(
                "landslide",
                formatTypeLabel("landslide"),
                "Mountain slope instability",
                "Khyber Pakhtunkhwa and Karakoram routes",
                "Rain-soaked slopes may fail on sharp turns, cut roads, or block relief access.",
                "High slope instability",
                "Demo preview",
                new GeoPoint(35.260, 75.650),
                72,
                55,
                69,
                colorForType("landslide"),
                markerIconForType("landslide"),
                new ArrayList<>(),
                arrayListOf("Chitral", "Swat", "Kohistan", "Mansehra"),
                true
        ));
        return zones;
    }

    private List<String> arrayListOf(String... items) {
        List<String> list = new ArrayList<>();
        for (String item : items) {
            list.add(item);
        }
        return list;
    }

    private GeoPoint extractCenter(DocumentSnapshot document) {
        Object centerRoot = document.get("center");
        GeoPoint center = toGeoPoint(centerRoot);
        if (center != null) {
            return center;
        }

        center = toGeoPoint(getNestedValue(document, "geometry", "center"));
        if (center != null) {
            return center;
        }

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

        if (latitude == null || longitude == null) {
            return null;
        }
        return new GeoPoint(latitude, longitude);
    }

    private List<GeoPoint> extractPolygon(Object... candidates) {
        List<GeoPoint> points = new ArrayList<>();
        for (Object candidate : candidates) {
            points = parsePoints(candidate);
            if (points.size() >= 3) {
                return points;
            }
        }
        return new ArrayList<>();
    }

    private List<GeoPoint> parsePoints(Object rawPoints) {
        List<GeoPoint> points = new ArrayList<>();
        if (!(rawPoints instanceof List<?>)) {
            return points;
        }

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

        if (rawValue instanceof GeoPoint) {
            return (GeoPoint) rawValue;
        }

        if (!(rawValue instanceof Map<?, ?>)) {
            return null;
        }

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

        if (latitude == null || longitude == null) {
            return null;
        }
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
        if (points.isEmpty()) {
            return null;
        }

        double sumLatitude = 0;
        double sumLongitude = 0;
        for (GeoPoint point : points) {
            sumLatitude += point.getLatitude();
            sumLongitude += point.getLongitude();
        }
        return new GeoPoint(sumLatitude / points.size(), sumLongitude / points.size());
    }

    private double estimateRadiusKm(GeoPoint center, List<GeoPoint> polygonPoints) {
        if (center == null || polygonPoints.isEmpty()) {
            return 0;
        }

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

    private Object getNestedValue(DocumentSnapshot document, String... path) {
        if (path.length == 0) {
            return null;
        }

        Object current = document.get(path[0]);
        for (int index = 1; index < path.length; index++) {
            if (!(current instanceof Map<?, ?>)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(path[index]);
        }
        return current;
    }

    private int markerIconForType(String type) {
        switch (normalizeType(type)) {
            case "flood":
                return R.drawable.ic_hazard_marker_flood;
            case "fire":
                return R.drawable.ic_hazard_marker_fire;
            case "landslide":
                return R.drawable.ic_hazard_marker_landslide;
            case "earthquake":
            default:
                return R.drawable.ic_hazard_marker_earthquake;
        }
    }

    private int colorForType(String type) {
        switch (normalizeType(type)) {
            case "flood":
                return Color.parseColor("#3D8DFF");
            case "fire":
                return Color.parseColor("#F29A3F");
            case "landslide":
                return Color.parseColor("#8B6A4B");
            case "earthquake":
            default:
                return Color.parseColor("#E75B5B");
        }
    }

    private String defaultTitle(String type) {
        switch (normalizeType(type)) {
            case "flood":
                return "Flood warning zone";
            case "fire":
                return "Forest fire watch";
            case "landslide":
                return "Landslide watch zone";
            case "earthquake":
            default:
                return "Earthquake watch zone";
        }
    }

    private String defaultArea(String type) {
        switch (normalizeType(type)) {
            case "flood":
                return "Pakistan floodplain";
            case "fire":
                return "Forest belt";
            case "landslide":
                return "Mountain corridor";
            case "earthquake":
            default:
                return "Seismic corridor";
        }
    }

    private String defaultSummary(String type) {
        switch (normalizeType(type)) {
            case "flood":
                return "Forecast rain bands and river runoff may affect nearby settlements and roads.";
            case "fire":
                return "Dry conditions and heat can keep an active fire front moving through forest edges.";
            case "landslide":
                return "Steep and wet slopes may fail if the current rain pattern continues.";
            case "earthquake":
            default:
                return "Seismic activity is being watched for a likely event window and aftershock spread.";
        }
    }

    private String defaultStatus(String type) {
        switch (normalizeType(type)) {
            case "flood":
                return "Ongoing flood risk";
            case "fire":
                return "Active fire spread";
            case "landslide":
                return "Developing landslide risk";
            case "earthquake":
            default:
                return "Forecast watch";
        }
    }

    private String normalizeType(String rawType) {
        String normalized = safe(rawType).toLowerCase(Locale.US).replace(' ', '_').replace('-', '_');
        if (normalized.contains("earthquake") || normalized.equals("quake") || normalized.equals("seismic")) {
            return "earthquake";
        }
        if (normalized.contains("flood") || normalized.contains("monsoon")) {
            return "flood";
        }
        if (normalized.contains("fire") || normalized.contains("wildfire") || normalized.contains("forest_fire")) {
            return "fire";
        }
        if (normalized.contains("landslide") || normalized.contains("mudslide") || normalized.contains("rockfall")) {
            return "landslide";
        }
        return "earthquake";
    }

    private String formatTypeLabel(String type) {
        switch (normalizeType(type)) {
            case "flood":
                return "Flood";
            case "fire":
                return "Forest Fire";
            case "landslide":
                return "Landslide";
            case "earthquake":
            default:
                return "Earthquake";
        }
    }

    private int applyAlpha(int baseColor, int alpha) {
        int boundedAlpha = Math.max(0, Math.min(255, alpha));
        return Color.argb(boundedAlpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
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

    private Integer toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean toBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String normalized = ((String) value).trim();
            if ("true".equalsIgnoreCase(normalized)) {
                return true;
            }
            if ("false".equalsIgnoreCase(normalized)) {
                return false;
            }
        }
        return fallback;
    }

    private String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Double firstNonNullDouble(Double... values) {
        for (Double value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer firstNonNullInt(Integer first, Integer second, Integer fallback) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return fallback;
    }

    private Integer firstNonNullInt(Integer first, Integer second, Integer third, Integer fallback) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        if (third != null) {
            return third;
        }
        return fallback;
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            String value = safe(candidate);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private interface LiveEarthquakeCallback {
        void onLoaded(List<HazardZone> zones);
    }

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

        HazardZone(String type,
                   String typeLabel,
                   String title,
                   String area,
                   String summary,
                   String status,
                   String source,
                   GeoPoint center,
                   double radiusKm,
                   int minutesUntil,
                   int confidence,
                   int color,
                   int markerIconRes,
                   List<GeoPoint> polygonPoints,
                   List<String> affectedAreas,
                   boolean isActive) {
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
        }
    }
}
