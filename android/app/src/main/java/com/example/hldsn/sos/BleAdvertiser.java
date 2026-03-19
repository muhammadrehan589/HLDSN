package com.example.hldsn.sos;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.UUID;

/**
 * Broadcasts a compact SOS packet over BLE advertising.
 *
 * The advertisement is non-connectable and uses a custom 128-bit service UUID
 * ({@link #SOS_SERVICE_UUID}) so that {@link BleScanner} can filter for it.
 *
 * REQUIRES: BLUETOOTH_ADVERTISE permission (API 31+) or BLUETOOTH_ADMIN (< API 31).
 */
public class BleAdvertiser {

    private static final String TAG = "BleAdvertiser";

    /**
     * Custom UUID that identifies HLDSN SOS advertisements.
     * Must match the UUID used in {@link BleScanner}.
     */
    public static final UUID SOS_SERVICE_UUID =
            UUID.fromString("0000DEAD-0000-1000-8000-00805F9B34FB");

    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback     advertiseCallback;
    private boolean               isAdvertising = false;

    // ── Public API ────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    public void startAdvertising(Context context, SosPacket packet) {
        if (isAdvertising) stopAdvertising();   // restart with new packet

        BluetoothManager bm = (BluetoothManager)
                context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bm == null) { Log.w(TAG, "BluetoothManager unavailable"); return; }

        BluetoothAdapter adapter = bm.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Log.w(TAG, "Bluetooth not enabled");
            return;
        }
        if (!adapter.isMultipleAdvertisementSupported()) {
            Log.w(TAG, "BLE multi-advertisement not supported on this device");
            return;
        }

        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) { Log.w(TAG, "BluetoothLeAdvertiser null"); return; }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .setTimeout(0)   // advertise until stopAdvertising() is called
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceUuid(new ParcelUuid(SOS_SERVICE_UUID))
                .addServiceData(new ParcelUuid(SOS_SERVICE_UUID), packet.encode())
                .setIncludeDeviceName(false)
                .build();

        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                isAdvertising = true;
                Log.d(TAG, "BLE SOS advertising started — " + packet.senderName);
            }

            @Override
            public void onStartFailure(int errorCode) {
                isAdvertising = false;
                Log.e(TAG, "BLE advertising failed, error code: " + errorCode);
            }
        };

        advertiser.startAdvertising(settings, data, advertiseCallback);
    }

    @SuppressLint("MissingPermission")
    public void stopAdvertising() {
        if (advertiser != null && advertiseCallback != null && isAdvertising) {
            try {
                advertiser.stopAdvertising(advertiseCallback);
            } catch (Exception e) {
                Log.e(TAG, "Error stopping advertising", e);
            }
            isAdvertising = false;
            Log.d(TAG, "BLE SOS advertising stopped");
        }
    }

    public boolean isAdvertising() { return isAdvertising; }
}
