package com.example.hldsn;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

/**
 * Lightweight helper for checking network reachability.
 * Works on API 26+ (min SDK of this project).
 */
public final class NetworkUtils {

    private NetworkUtils() { /* no instances */ }

    /**
     * Returns {@code true} when the device has an active network connection
     * (Wi-Fi, cellular, or Ethernet).  Does NOT guarantee internet access, but
     * is sufficient for deciding whether to attempt an HTTP upload.
     */
    public static boolean isOnline(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null
                && (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }
}

