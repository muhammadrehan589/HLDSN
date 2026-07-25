package com.example.hldsn;

import android.app.Application;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;
import org.osmdroid.config.Configuration;

import java.io.File;

/**
 * Application entry-point.
 * Configures Firestore offline persistence with an unlimited cache so every
 * collection the app has ever loaded is available when the device is offline.
 */
public class HldsnApp extends Application {

    private static final String TAG = "HldsnApp";

    @Override
    public void onCreate() {
        super.onCreate();
        configureOsmdroid();
        configureFirestoreOffline();
    }

    private void configureOsmdroid() {
        Configuration.getInstance().load(
                getApplicationContext(),
                getSharedPreferences("osmdroid", MODE_PRIVATE)
        );
        // Set a unique, descriptive User-Agent as required by tile providers.
        Configuration.getInstance().setUserAgentValue("HLDSN-DisasterApp/1.0 (Android)");

        // Set a writable cache directory for tile storage.
        File cacheDir = new File(getExternalCacheDir(), "osmdroid");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        Configuration.getInstance().setOsmdroidTileCache(cacheDir);
    }

    private void configureFirestoreOffline() {
        try {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    // Persistent on-disk cache with no size cap.
                    .setLocalCacheSettings(
                            PersistentCacheSettings.newBuilder()
                                    .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                                    .build())
                    .build();
            db.setFirestoreSettings(settings);
            Log.d(TAG, "Firestore offline persistence configured (unlimited cache).");
        } catch (Exception e) {
            // Settings can only be applied before the first use of the instance.
            Log.w(TAG, "Could not apply Firestore settings (already in use): " + e.getMessage());
        }
    }
}

