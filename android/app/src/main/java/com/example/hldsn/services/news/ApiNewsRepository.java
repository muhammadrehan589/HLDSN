package com.example.hldsn.services.news;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.example.hldsn.BuildConfig;
import com.example.hldsn.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiNewsRepository implements NewsRepository {

    private static final String TAG = "ApiNewsRepository";
    private static final String CACHE_FILE_NAME = "news_cache.json";
    private static final String API_URL = "https://api.currentsapi.services/v1/search";
    private static final String LATEST_URL = "https://api.currentsapi.services/v1/latest-news";
    private static final String DEFAULT_KEYWORDS = "natural disaster flood earthquake landslide heatwave monsoon";
    private static final String[] DISASTER_TERMS = {
            "flood", "earthquake", "landslide", "heatwave", "monsoon", "cyclone", "drought", "avalanche"
    };
    private static final int API_PAGE_SIZE = 50; // Currents free tier maximum.
    private static final int NEWS_LIMIT = 20;
    private static final int RESPONSE_PREVIEW_CHARS = 400;
    private static final int TIMEOUT_MS = 15000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private List<NewsItem> memoryCache = new ArrayList<>();
    private final Context context;

    public ApiNewsRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void fetchNews(boolean forceRefresh, Callback callback) {
        if (callback == null) {
            return;
        }

        if (!forceRefresh && !memoryCache.isEmpty()) {
            Log.d(TAG, "Using in-memory cached news. Count=" + memoryCache.size());
            callback.onSuccess(new ArrayList<>(memoryCache));
            return;
        }

        executor.execute(() -> {
            // Check Network Connectivity
            if (!isNetworkAvailable()) {
                List<NewsItem> cachedItems = loadFromDisk();
                if (!cachedItems.isEmpty()) {
                    memoryCache = new ArrayList<>(cachedItems);
                    postSuccess(callback, cachedItems);
                    return;
                }
                postError(callback, "No internet connection and no cached news available.");
                return;
            }

            if (TextUtils.isEmpty(BuildConfig.NEWS_API_KEY)) {
                Log.e(TAG, "NEWS_API_KEY is empty. Cannot call CurrentsAPI.");
                postError(callback, "News API key is missing.");
                return;
            }

            try {
                String latestUrl = LATEST_URL
                        + "?country=PK"
                        + "&language=en"
                        + "&page_size=" + API_PAGE_SIZE;
                String latestResponse = executeRequest(latestUrl, "latest-news");
                List<NewsItem> items = parseItems(latestResponse);

                if (items.size() < NEWS_LIMIT) {
                    List<NewsItem> searchItems = fetchSearchCandidates();
                    items = mergeUnique(items, searchItems, NEWS_LIMIT);
                }

                if (items.size() > NEWS_LIMIT) {
                    items = new ArrayList<>(items.subList(0, NEWS_LIMIT));
                }

                if (items.isEmpty()) {
                    // Try to fallback to disk cache if API returns nothing (e.g. rate limit)
                    List<NewsItem> cached = loadFromDisk();
                    if (!cached.isEmpty()) {
                        postSuccess(callback, cached);
                        return;
                    }
                    postError(callback, "No Pakistan natural-disaster news available right now.");
                    return;
                }

                memoryCache = new ArrayList<>(items);
                saveToDisk(items);
                postSuccess(callback, items);
            } catch (Exception exception) {
                Log.e(TAG, "CurrentsAPI fetch failed", exception);
                List<NewsItem> cached = loadFromDisk();
                if (!cached.isEmpty()) {
                    postSuccess(callback, cached);
                } else {
                    postError(callback, "Unable to fetch news right now.");
                }
            }
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private void saveToDisk(List<NewsItem> items) {
        try {
            JSONArray array = new JSONArray();
            for (NewsItem item : items) {
                JSONObject obj = new JSONObject();
                obj.put("headline", item.getHeadline());
                obj.put("description", item.getDescription());
                obj.put("fullText", item.getFullText());
                obj.put("location", item.getLocation());
                obj.put("publishedAt", item.getPublishedAt());
                obj.put("imageUrl", item.getImageUrl());
                array.put(obj);
            }
            File file = new File(context.getCacheDir(), CACHE_FILE_NAME);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(array.toString().getBytes(StandardCharsets.UTF_8));
            }
            Log.d(TAG, "News saved to disk cache.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save news to disk", e);
        }
    }

    private List<NewsItem> loadFromDisk() {
        List<NewsItem> items = new ArrayList<>();
        File file = new File(context.getCacheDir(), CACHE_FILE_NAME);
        if (!file.exists()) return items;

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            String json = new String(data, StandardCharsets.UTF_8);
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                items.add(new NewsItem(
                        obj.optString("headline"),
                        obj.optString("description"),
                        obj.optString("fullText"),
                        obj.optString("location"),
                        obj.optString("publishedAt"),
                        obj.optString("imageUrl"),
                        R.drawable.ic_disaster
                ));
            }
            Log.d(TAG, "Loaded " + items.size() + " news items from disk cache.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load news from disk", e);
        }
        return items;
    }

    private String executeRequest(String urlString, String label) throws Exception {
        HttpURLConnection connection = null;
        try {
            Log.d(TAG, "Requesting CurrentsAPI (" + label + "): " + urlString.replace("\n", ""));
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", BuildConfig.NEWS_API_KEY);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String responseBody = readStream(stream);

            Log.d(TAG, "CurrentsAPI " + label + " response code=" + responseCode
                    + " bodyPreview=" + trimForLog(responseBody));

            if (responseCode < 200 || responseCode >= 300) {
                throw new IllegalStateException("News API error: " + responseCode);
            }

            return responseBody;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private List<NewsItem> parseItems(String body) throws Exception {
        List<NewsItem> items = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        JSONObject root = new JSONObject(body);

        JSONArray data = root.optJSONArray("news");
        if (data == null || data.length() == 0) {
            Log.d(TAG, "CurrentsAPI response has no 'news' array or is empty.");
            return items;
        }

        for (int i = 0; i < data.length(); i++) {
            JSONObject article = data.optJSONObject(i);
            if (article == null) {
                continue;
            }

            String headline = article.optString("title");
            String description = article.optString("description");
            String fullText = firstNonEmpty(description, article.optString("title"));
            String location = resolveLocation(article);
            String publishedAt = firstNonEmpty(article.optString("published"), article.optString("published_at"));
            String imageUrl = firstNonEmpty(article.optString("image"), article.optString("image_url"));
            String articleUrl = article.optString("url");
            String author = article.optString("author");
            String categoryText = flattenCategories(article.optJSONArray("category"));

            String combined = (headline + " " + description + " " + fullText + " " + location
                    + " " + categoryText + " " + articleUrl + " " + author).toLowerCase(Locale.US);
            if (!isPakistanNaturalDisaster(combined)) {
                continue;
            }

            if (TextUtils.isEmpty(headline)) {
                continue;
            }

            String dedupeKey = firstNonEmpty(articleUrl, headline).trim().toLowerCase(Locale.US);
            if (TextUtils.isEmpty(dedupeKey) || seenKeys.contains(dedupeKey)) {
                continue;
            }
            seenKeys.add(dedupeKey);

            items.add(new NewsItem(
                    headline,
                    TextUtils.isEmpty(description) ? "Tap to read details." : description,
                    TextUtils.isEmpty(fullText) ? description : fullText,
                    location,
                    publishedAt,
                    imageUrl,
                    R.drawable.ic_disaster
            ));

            if (items.size() >= NEWS_LIMIT) {
                break;
            }
        }
        return items;
    }

    private boolean isPakistanNaturalDisaster(String text) {
        boolean pakistanMatch = text.contains("pakistan")
                || text.contains("\"country\":\"pk\"")
                || text.contains(".pk")
                || text.contains("karachi")
                || text.contains("lahore")
                || text.contains("islamabad")
                || text.contains("peshawar")
                || text.contains("quetta")
                || text.contains("multan")
                || text.contains("sindh")
                || text.contains("punjab")
                || text.contains("balochistan")
                || text.contains("khyber")
                || text.contains("gilgit")
                || text.contains("pk");

        boolean disasterMatch = text.contains("disaster")
                || text.contains("flood")
                || text.contains("earthquake")
                || text.contains("landslide")
                || text.contains("heatwave")
                || text.contains("monsoon")
                || text.contains("storm")
                || text.contains("drought")
                || text.contains("avalanche")
                || text.contains("cyclone")
                || text.contains("wildfire")
                || text.contains("weather");

        return pakistanMatch && disasterMatch;
    }

    private List<NewsItem> fetchSearchCandidates() throws Exception {
        List<NewsItem> combinedSearchItems = new ArrayList<>();

        // First pass: strict Pakistan query per disaster term.
        for (String term : DISASTER_TERMS) {
            if (combinedSearchItems.size() >= NEWS_LIMIT) {
                break;
            }
            String encodedTerm = URLEncoder.encode(term, StandardCharsets.UTF_8.name());
            String searchUrl = API_URL
                    + "?keywords=" + encodedTerm
                    + "&country=PK"
                    + "&language=en"
                    + "&page_size=" + API_PAGE_SIZE;
            String response = executeRequest(searchUrl, "search-pk:" + term);
            List<NewsItem> parsed = parseItems(response);
            Log.d(TAG, "Parsed/filtered search-pk term=" + term + " count=" + parsed.size());
            combinedSearchItems = mergeUnique(combinedSearchItems, parsed, NEWS_LIMIT);
        }

        // Second pass: broader search without country filter; local Pakistan filter still applies.
        if (combinedSearchItems.size() < NEWS_LIMIT) {
            for (String term : DISASTER_TERMS) {
                if (combinedSearchItems.size() >= NEWS_LIMIT) {
                    break;
                }
                String encodedTerm = URLEncoder.encode(term, StandardCharsets.UTF_8.name());
                String searchUrl = API_URL
                        + "?keywords=" + encodedTerm
                        + "&language=en"
                        + "&page_size=" + API_PAGE_SIZE;
                String response = executeRequest(searchUrl, "search-global:" + term);
                List<NewsItem> parsed = parseItems(response);
                Log.d(TAG, "Parsed/filtered search-global term=" + term + " count=" + parsed.size());
                combinedSearchItems = mergeUnique(combinedSearchItems, parsed, NEWS_LIMIT);
            }
        }

        Log.d(TAG, "Total merged search candidates=" + combinedSearchItems.size());
        return combinedSearchItems;
    }

    private String resolveLocation(JSONObject article) {
        JSONArray categories = article.optJSONArray("category");
        String categoryText = flattenCategories(categories).toLowerCase(Locale.US);
        if (categoryText.contains("pakistan")) {
            return "Pakistan";
        }

        String language = article.optString("language");
        if (!TextUtils.isEmpty(language)) {
            return "PK/" + language.toUpperCase(Locale.US);
        }

        return "Pakistan";
    }

    private String flattenCategories(JSONArray categories) {
        if (categories == null || categories.length() == 0) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < categories.length(); i++) {
            String value = categories.optString(i);
            if (!TextUtils.isEmpty(value)) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(value);
            }
        }
        return builder.toString();
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }

    private String trimForLog(String value) {
        if (value == null) {
            return "";
        }
        String compact = value.replace('\n', ' ').replace('\r', ' ');
        if (compact.length() <= RESPONSE_PREVIEW_CHARS) {
            return compact;
        }
        return compact.substring(0, RESPONSE_PREVIEW_CHARS) + "...";
    }

    private List<NewsItem> mergeUnique(List<NewsItem> base, List<NewsItem> extras, int limit) {
        List<NewsItem> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (NewsItem item : base) {
            String key = buildDedupKey(item);
            if (seen.add(key)) {
                merged.add(item);
            }
            if (merged.size() >= limit) {
                return merged;
            }
        }

        for (NewsItem item : extras) {
            String key = buildDedupKey(item);
            if (seen.add(key)) {
                merged.add(item);
            }
            if (merged.size() >= limit) {
                return merged;
            }
        }

        return merged;
    }

    private String buildDedupKey(NewsItem item) {
        String headline = item.getHeadline();
        String published = item.getPublishedAt();
        String description = item.getDescription();
        return (firstNonEmpty(headline, "untitled") + "|"
                + firstNonEmpty(published, "no-date") + "|"
                + firstNonEmpty(description, "no-description")).toLowerCase(Locale.US);
    }

    private void postSuccess(Callback callback, List<NewsItem> items) {
        mainHandler.post(() -> callback.onSuccess(items));
    }

    private void postError(Callback callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }
}

