package com.example.hldsn.nearby;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Optimized Overpass API client with support for all emergency categories.
 */
public class OverpassApiClient {
    private static final String TAG = "OverpassApiClient";
    private static final String API_URL = "https://overpass-api.de/api/interpreter";
    private static final int TIMEOUT = 30000;

    public interface ApiCallback {
        void onSuccess(List<NearbyResource> resources);
        void onError(String error);
    }

    public static void fetchNearbyResources(double latitude, double longitude,
                                           String category, int radiusMeters, ApiCallback callback) {
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                String query = buildOverpassQuery(latitude, longitude, category, radiusMeters);
                if (query.isEmpty()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }
                
                Log.d(TAG, "Requesting: " + category + " @ " + radiusMeters + "m");
                List<NearbyResource> resources = fetchFromApi(query, latitude, longitude);
                
                Log.d(TAG, "Success: Found " + resources.size() + " in " + (System.currentTimeMillis() - startTime) + "ms");
                callback.onSuccess(resources);
            } catch (Exception e) {
                Log.e(TAG, "Fetch Failed: " + e.getMessage());
                callback.onError("Server busy. Please try again. (" + e.getMessage() + ")");
            }
        }).start();
    }

    private static String buildOverpassQuery(double latitude, double longitude, String category, int radiusMeters) {
        StringBuilder query = new StringBuilder();
        query.append("[out:json][timeout:25];(");

        String cat = normalizeCategory(category);
        String around = "(around:" + radiusMeters + "," + latitude + "," + longitude + ")";

        boolean validCategory = true;
        switch (cat) {
            case "hospitals":
                appendNwr(query, around, "amenity", "hospital");
                appendNwr(query, around, "healthcare", "hospital");
                break;
            case "pharmacies":
                appendNwr(query, around, "amenity", "pharmacy");
                appendNwr(query, around, "shop", "chemist");
                break;
            case "police":
                appendNwr(query, around, "amenity", "police");
                break;
            case "fire":
                appendNwr(query, around, "amenity", "fire_station");
                break;
            case "clinics":
                appendNwr(query, around, "amenity", "clinic");
                appendNwr(query, around, "amenity", "doctors");
                break;
            case "rescue":
                appendNwr(query, around, "emergency", "ambulance_station");
                break;
            case "shelters":
                appendNwr(query, around, "amenity", "shelter");
                appendNwr(query, around, "emergency", "assembly_point");
                break;
            case "blood_banks":
                appendNwr(query, around, "healthcare", "blood_bank");
                appendNwr(query, around, "amenity", "blood_bank");
                appendNwr(query, around, "healthcare", "blood_donation");
                break;
            case "government":
                appendNwr(query, around, "office", "government");
                appendNwr(query, around, "government", "administrative");
                appendNwr(query, around, "building", "government");
                appendNwr(query, around, "amenity", "townhall");
                appendNwr(query, around, "amenity", "courthouse");
                break;
            case "water":
                appendNwr(query, around, "amenity", "drinking_water");
                appendNwr(query, around, "amenity", "water_point");
                appendNwr(query, around, "waterway", "water_point");
                appendNwr(query, around, "man_made", "water_tap");
                break;
            case "all":
                appendNwr(query, around, "amenity", "hospital");
                appendNwr(query, around, "amenity", "pharmacy");
                appendNwr(query, around, "amenity", "police");
                appendNwr(query, around, "amenity", "fire_station");
                break;
            default:
                validCategory = false;
                break;
        }

        if (!validCategory) return "";

        query.append(");out center tags;");
        return query.toString();
    }

    private static void appendNwr(StringBuilder query, String around, String key, String value) {
        query.append("nwr[\"").append(key).append("\"=\"").append(value).append("\"]").append(around).append(";");
    }

    private static String normalizeCategory(String category) {
        if (category == null || category.equalsIgnoreCase("all")) return "all";
        String lower = category.trim().toLowerCase();
        if (lower.contains("pharm")) return "pharmacies";
        if (lower.contains("rescu")) return "rescue";
        if (lower.contains("hosp")) return "hospitals";
        if (lower.contains("clinic")) return "clinics";
        if (lower.contains("polic")) return "police";
        if (lower.contains("fire")) return "fire";
        if (lower.contains("shelter")) return "shelters";
        if (lower.contains("blood")) return "blood_banks";
        if (lower.contains("gov")) return "government";
        if (lower.contains("water")) return "water";
        return lower;
    }

    private static List<NearbyResource> fetchFromApi(String query, double userLat, double userLon) throws IOException, JSONException {
        List<NearbyResource> resources = new ArrayList<>();
        String encodedQuery = URLEncoder.encode(query, "UTF-8");
        URL url = new URL(API_URL + "?data=" + encodedQuery);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(TIMEOUT);
        connection.setReadTimeout(TIMEOUT);
        connection.setRequestProperty("User-Agent", "HLDSN-App-v3");

        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server error " + connection.getResponseCode());
            }

            String body = readInputStream(connection.getInputStream());
            JSONArray elements = new JSONObject(body).getJSONArray("elements");
            for (int i = 0; i < elements.length(); i++) {
                JSONObject el = elements.getJSONObject(i);
                NearbyResource res = parseElement(el, userLat, userLon);
                if (res != null) resources.add(res);
            }
            resources.sort((r1, r2) -> Double.compare(r1.distance, r2.distance));
        } finally {
            connection.disconnect();
        }
        return resources;
    }

    private static NearbyResource parseElement(JSONObject el, double userLat, double userLon) throws JSONException {
        JSONObject tags = el.optJSONObject("tags");
        if (tags == null) return null;

        double lat, lon;
        if (el.has("lat")) {
            lat = el.getDouble("lat");
            lon = el.getDouble("lon");
        } else if (el.has("center")) {
            lat = el.getJSONObject("center").getDouble("lat");
            lon = el.getJSONObject("center").getDouble("lon");
        } else return null;

        String name = tags.optString("name", tags.optString("operator", tags.optString("brand", "Resource")));
        String category = determineCategory(tags);
        String phone = tags.optString("phone", tags.optString("contact:phone", ""));
        String address = tags.optString("addr:street", tags.optString("addr:full", "Nearby Location"));

        double dist = calculateDistance(userLat, userLon, lat, lon);
        return new NearbyResource(el.optString("id"), name, category, address, phone, lat, lon, dist, false);
    }

    private static String determineCategory(JSONObject tags) {
        String am = tags.optString("amenity", "");
        String sh = tags.optString("shop", "");
        String hc = tags.optString("healthcare", "");
        String off = tags.optString("office", "");
        String gov = tags.optString("government", "");
        String bld = tags.optString("building", "");
        String ww = tags.optString("waterway", "");
        String mm = tags.optString("man_made", "");

        if (am.equals("hospital") || hc.equals("hospital")) return "Hospitals";
        if (am.equals("pharmacy") || sh.equals("chemist") || hc.equals("pharmacy")) return "Pharmacies";
        if (am.equals("police")) return "Police";
        if (am.equals("fire_station")) return "Fire";
        if (am.equals("clinic") || am.equals("doctors") || hc.equals("clinic")) return "Clinics";
        if (am.equals("shelter")) return "Shelters";
        if (hc.equals("blood_bank") || am.equals("blood_bank") || hc.equals("blood_donation")) return "Blood Banks";
        if (!off.isEmpty() || !gov.isEmpty() || bld.equals("government") || am.equals("townhall") || am.equals("courthouse")) return "Government";
        if (am.equals("drinking_water") || am.equals("water_point") || ww.equals("water_point") || mm.equals("water_tap")) return "Water";
        return "Resource";
    }

    private static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String readInputStream(java.io.InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
