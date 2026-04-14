package com.example.hldsn.sos;

final class TransportSelector {

    enum Transport {
        BLE,
        WIFI_DIRECT
    }

    Transport select(int payloadBytes, int batteryPercent, boolean bleReady, boolean wifiDirectReady) {
        if (bleReady && (payloadBytes <= SosCodec.BLE_BEACON_SIZE || batteryPercent <= 25 || !wifiDirectReady)) {
            return Transport.BLE;
        }
        return wifiDirectReady ? Transport.WIFI_DIRECT : Transport.BLE;
    }
}

