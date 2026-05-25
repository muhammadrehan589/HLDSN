package com.example.hldsn.nearby;

import java.io.Serializable;

/**
 * Model class for nearby emergency resources fetched from Overpass API.
 * Represents hospitals, pharmacies, police stations, fire stations, shelters, etc.
 */
public class NearbyResource implements Serializable {
    public String id;
    public String name;
    public String category;
    public String address;
    public String phone;
    public double latitude;
    public double longitude;
    public double distance; // in kilometers
    public boolean isVerified; // For "Verified Local" badge

    public NearbyResource() {
    }

    public NearbyResource(String id, String name, String category, String address,
                         String phone, double latitude, double longitude, double distance) {
        this(id, name, category, address, phone, latitude, longitude, distance, false);
    }

    public NearbyResource(String id, String name, String category, String address,
                         String phone, double latitude, double longitude, double distance, boolean isVerified) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.address = address;
        this.phone = phone;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distance = distance;
        this.isVerified = isVerified;
    }

    @Override
    public String toString() {
        return "NearbyResource{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", category='" + category + '\'' +
                ", distance=" + String.format("%.1f", distance) +
                ", verified=" + isVerified +
                '}';
    }
}
