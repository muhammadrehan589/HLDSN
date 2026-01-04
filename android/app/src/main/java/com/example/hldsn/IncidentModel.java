package com.example.hldsn;

import java.util.Date;

public class IncidentModel {

    private String userId;
    private String incidentType;
    private String mediaUrl;
    private String location;
    private String description;
    private boolean isSafe;
    private String status;

    private Date createdAt; // Firestore Timestamp
    private Double reporterLat;
    private Double reporterLng;

    // REQUIRED empty constructor
    public IncidentModel() {}

    // Constructor WITHOUT createdAt
    public IncidentModel(
            String userId,
            String incidentType,
            String location,
            String description,
            boolean isSafe,
            String mediaUrl,
            Double reporterLat,
            Double reporterLng
    ) {
        this.userId = userId;
        this.incidentType = incidentType;
        this.location = location;
        this.description = description;
        this.isSafe = isSafe;
        this.mediaUrl = mediaUrl;
        this.status = "PENDING";
        this.reporterLat = reporterLat;
        this.reporterLng = reporterLng;


        // ❌ DO NOT set createdAt here
    }

    // Getters
    public String getUserId() { return userId; }
    public String getIncidentType() { return incidentType; }
    public String getMediaUrl() { return mediaUrl; }
    public String getLocation() { return location; }
    public String getDescription() { return description; }
    public boolean isSafe() { return isSafe; }
    public String getStatus() { return status; }
    public Date getCreatedAt() { return createdAt; }
    public Double getReporterLat() { return reporterLat; }
    public Double getReporterLng() { return reporterLng; }

    // Setters
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public void setReporterLat(Double reporterLat) { this.reporterLat = reporterLat; }
    public void setReporterLng(Double reporterLng) { this.reporterLng = reporterLng; }
}
