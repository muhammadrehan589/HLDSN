package com.example.hldsn.sos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;

public class MeshBootstrapReceiver extends BroadcastReceiver {

    private static final String TAG = "MeshBootstrapReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        boolean supportedAction = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        if (!supportedAction) {
            return;
        }

        try {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Log.d(TAG, "Skipping mesh autostart: no authenticated user.");
                return;
            }

            Intent startMesh = new Intent(context, SosForegroundService.class);
            startMesh.setAction(SosForegroundService.ACTION_START_MESH);
            ContextCompat.startForegroundService(context, startMesh);
            Log.d(TAG, "Mesh autostart requested for action=" + action);
        } catch (Exception e) {
            Log.w(TAG, "Unable to autostart mesh service", e);
        }
    }
}
