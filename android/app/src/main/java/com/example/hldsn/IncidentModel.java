package com.example.hldsn;

import com.google.firebase.Timestamp;

public class IncidentModel {

    public String userId;
    public String incidentType;
    public String mediaUrl;
    public String location;
    public String description;
    public boolean isSafe;
    public String status;
    public Timestamp createdAt;

    public IncidentModel() {}

    public IncidentModel(String userId, String incidentType, String location,
                    String description, boolean isSafe,String mediaUrl,Long createdAt) {

        this.userId = userId;
        this.incidentType = incidentType;
        this.location = location;
        this.description = description;
        this.isSafe = isSafe;
        this.status = "PENDING";
        this.mediaUrl=mediaUrl;
        this.createdAt = Timestamp.now();
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public String getUserId() {
        return userId;
    }

    public String getIncidentType() {
        return incidentType;
    }

    public String getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSafe() {
        return isSafe;
    }

    public String getStatus() {
        return status;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setIncidentType(String incidentType) {
        this.incidentType = incidentType;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setSafe(boolean safe) {
        isSafe = safe;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
