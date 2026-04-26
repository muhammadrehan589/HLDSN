package com.example.hldsn.sos;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * One-shot location helper using FusedLocationProviderClient.
 * Tries the last known location first (instant), falls back to a fresh GPS fix.
 *
 * REQUIRES: ACCESS_FINE_LOCATION permission already granted.
 */
public class LocationHelper {

    private static final String TAG = "LocationHelper";

    public interface LocationCallback2 {
        void onLocationReceived(double lat, double lng);
        void onFailed(String reason);
    }

    private final FusedLocationProviderClient fusedClient;

    public LocationHelper(Context context) {
        this.fusedClient = LocationServices.getFusedLocationProviderClient(context);
    }

    @SuppressLint("MissingPermission")
    public void getLocation(Context context, LocationCallback2 callback) {
        fusedClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        Log.d(TAG, "Using last known location");
                        callback.onLocationReceived(location.getLatitude(), location.getLongitude());
                    } else {
                        Log.d(TAG, "Last known location null, requesting fresh fix");
                        requestFreshLocation(callback);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "getLastLocation failed: " + e.getMessage());
                    requestFreshLocation(callback);
                });
    }

    @SuppressLint("MissingPermission")
    private void requestFreshLocation(LocationCallback2 callback) {
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000)
                .setMaxUpdates(1)
                .setWaitForAccurateLocation(false)
                .build();

        LocationCallback lc = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                fusedClient.removeLocationUpdates(this);
                Location loc = result.getLastLocation();
                if (loc != null) {
                    Log.d(TAG, "Fresh fix received");
                    callback.onLocationReceived(loc.getLatitude(), loc.getLongitude());
                } else {
                    callback.onFailed("Fresh fix returned null");
                }
            }
        };

        fusedClient.requestLocationUpdates(req, lc, Looper.getMainLooper())
                .addOnFailureListener(e -> callback.onFailed(e.getMessage()));
    }
}
