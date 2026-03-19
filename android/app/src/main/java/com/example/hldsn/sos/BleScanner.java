package com.example.hldsn.sos;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.Collections;

/**
 * Passively scans for BLE SOS packets broadcast by {@link BleAdvertiser}.
 *
 * When an SOS advertisement is detected, the {@link SosReceivedListener} is invoked.
 * The service that owns this scanner is responsible for relaying the packet
 * (i.e., re-advertising it via a BleAdvertiser to extend the mesh range).
 *
 * REQUIRES: BLUETOOTH_SCAN permission (API 31+) or BLUETOOTH_ADMIN (< API 31).
 */
public class BleScanner {

    private static final String TAG = "BleScanner";

    public interface SosReceivedListener {
        void onSosReceived(SosPacket packet);
    }

    private BluetoothLeScanner  scanner;
    private ScanCallback        scanCallback;
    private SosReceivedListener listener;
    private boolean             isScanning = false;

    // ── Public API ────────────────────────────────────────────────────────────

    public void setListener(SosReceivedListener listener) {
        this.listener = listener;
    }

    @SuppressLint("MissingPermission")
    public void startScanning(Context context) {
        if (isScanning) return;

        BluetoothManager bm = (BluetoothManager)
                context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bm == null) { Log.w(TAG, "BluetoothManager unavailable"); return; }

        BluetoothAdapter adapter = bm.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Log.w(TAG, "Bluetooth not enabled — scan skipped");
            return;
        }

        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { Log.w(TAG, "BluetoothLeScanner null"); return; }

        // Filter: only receive advertisements with our custom SOS service UUID
        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(BleAdvertiser.SOS_SERVICE_UUID))
                .build();

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build();

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                ScanRecord record = result.getScanRecord();
                if (record == null) return;

                byte[] data = record.getServiceData(
                        new ParcelUuid(BleAdvertiser.SOS_SERVICE_UUID));
                if (data == null) return;

                SosPacket packet = SosPacket.decode(data);
                if (packet != null && listener != null) {
                    Log.d(TAG, "SOS detected via BLE: " + packet.senderName);
                    listener.onSosReceived(packet);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                isScanning = false;
                Log.e(TAG, "BLE scan failed with errorCode: " + errorCode);
            }
        };

        scanner.startScan(Collections.singletonList(filter), settings, scanCallback);
        isScanning = true;
        Log.d(TAG, "BLE SOS scanning started");
    }

    @SuppressLint("MissingPermission")
    public void stopScanning() {
        if (scanner != null && scanCallback != null && isScanning) {
            try {
                scanner.stopScan(scanCallback);
            } catch (Exception e) {
                Log.e(TAG, "Error stopping scan", e);
            }
            isScanning = false;
            Log.d(TAG, "BLE SOS scanning stopped");
        }
    }

    public boolean isScanning() { return isScanning; }
}
