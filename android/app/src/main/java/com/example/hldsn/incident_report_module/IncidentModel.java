package com.example.hldsn.incident_report_module;

import java.util.Date;

public class IncidentModel {

    private String userId;
    private String incidentType;
    private String mediaUrl;
    private String location;
    private String description;
    private Boolean safe;
    private String status;
    private Date createdAt; // Firestore Timestamp
    private Double reporterLat;
    private Double reporterLng;
    private String id;
    private boolean commentsExpanded;
    private long likes = 0;
    private long dislikes = 0;
    private  String userVote;
    private long commentCount = 0;


    public IncidentModel() {
    }

    // Constructor WITHOUT createdAt
    public IncidentModel(
            String userId,
            String incidentType,
            String location,
            String description,
            Boolean safe,
            String mediaUrl,
            Double reporterLat,
            Double reporterLng
    ) {
        this.userId = userId;
        this.incidentType = incidentType;
        this.location = location;
        this.description = description;
        this.safe = safe;
        this.mediaUrl = mediaUrl;
        this.status = "PENDING";
        this.reporterLat = reporterLat;
        this.reporterLng = reporterLng;

    }

    public String getUserVote() {
        return userVote;
    }

    public void setUserVote(String userVote) {
        this.userVote = userVote;
    }
    // Getters
    public long getLikes() { return likes; }
    public void setLikes(long likes) { this.likes = likes; }

    public long getDislikes() { return dislikes; }
    public void setDislikes(long dislikes) { this.dislikes = dislikes;}

    public long getCommentCount() { return commentCount; }
    public void setCommentCount(long commentCount) { this.commentCount = commentCount; }
    public String getUserId() {
        return userId;
    }

    public String getIncidentType() {
        return incidentType;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public String getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    //    public Boolean isSafe() { return safe; }
    public String getStatus() {
        return status;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public Double getReporterLat() {
        return reporterLat;
    }

    public Double getReporterLng() {
        return reporterLng;
    }

    // Setters
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public void setReporterLat(Double reporterLat) {
        this.reporterLat = reporterLat;
    }

    public void setReporterLng(Double reporterLng) {
        this.reporterLng = reporterLng;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setIncidentType(String incidentType) {
        this.incidentType = incidentType;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Boolean getSafe() {
        return safe;
    }

    public void setSafe(Boolean safe) {
        this.safe = safe;
    }

    public boolean isCommentsExpanded() {
        return commentsExpanded;
    }

    public void setCommentsExpanded(boolean commentsExpanded) {
        this.commentsExpanded = commentsExpanded;
    }

}
