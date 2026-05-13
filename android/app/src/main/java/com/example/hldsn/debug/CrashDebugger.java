package com.example.hldsn.debug;

import android.util.Log;

/**
 * Dedicated crash debugging utility with custom tags for tracking app crashes.
 * Use this class to log crashes and exceptions with a dedicated tag.
 */
public class CrashDebugger {

    // Dedicated debug tags
    public static final String TAG_BUTTON_CRASH = "CRASH:ButtonClick";
    public static final String TAG_ACTIVITY_CRASH = "CRASH:Activity";
    public static final String TAG_NETWORK_CRASH = "CRASH:Network";
    public static final String TAG_FIREBASE_CRASH = "CRASH:Firebase";
    public static final String TAG_LOCATION_CRASH = "CRASH:Location";
    public static final String TAG_ADAPTER_CRASH = "CRASH:Adapter";
    public static final String TAG_GENERAL_CRASH = "CRASH:General";

    /**
     * Log button click with debugging info
     */
    public static void logButtonClick(String buttonName, String actionDetails) {
        Log.d(TAG_BUTTON_CRASH, "Button [" + buttonName + "] clicked: " + actionDetails);
    }

    /**
     * Log button click exception
     */
    public static void logButtonClickError(String buttonName, Exception e) {
        Log.e(TAG_BUTTON_CRASH, "Button [" + buttonName + "] CRASHED", e);
    }

    /**
     * Log activity lifecycle event
     */
    public static void logActivityEvent(String activityName, String event) {
        Log.d(TAG_ACTIVITY_CRASH, "Activity [" + activityName + "] " + event);
    }

    /**
     * Log activity exception
     */
    public static void logActivityError(String activityName, String event, Exception e) {
        Log.e(TAG_ACTIVITY_CRASH, "Activity [" + activityName + "] " + event + " CRASHED", e);
    }

    /**
     * Log network error
     */
    public static void logNetworkError(String context, Exception e) {
        Log.e(TAG_NETWORK_CRASH, "Network error in [" + context + "]", e);
    }

    /**
     * Log Firebase error
     */
    public static void logFirebaseError(String context, Exception e) {
        Log.e(TAG_FIREBASE_CRASH, "Firebase error in [" + context + "]", e);
    }

    /**
     * Log general exception
     */
    public static void logCrash(String context, String message, Exception e) {
        Log.e(TAG_GENERAL_CRASH, "CRASH in [" + context + "]: " + message, e);
    }

    /**
     * Log crash with null check info
     */
    public static void logNullPointerDebug(String context, String fieldName) {
        Log.e(TAG_GENERAL_CRASH, "NullPointerException in [" + context + "]: Field '" + fieldName + "' is null");
    }
}

