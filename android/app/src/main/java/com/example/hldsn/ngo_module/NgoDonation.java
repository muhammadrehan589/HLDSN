package com.example.hldsn.ngo_module;

import com.google.firebase.Timestamp;

import java.util.HashMap;
import java.util.Map;

public class NgoDonation {
    private String id;
    private String ngoId;
    private String resourceId;
    private String name;
    private String description;
    private int quantity;
    private String donationLocation;
    private String createdByUid;
    private Timestamp createdAt;

    public NgoDonation() {}

    public NgoDonation(String ngoId, String resourceId, String name, String description,
                       int quantity, String donationLocation, String createdByUid) {
        this.ngoId = ngoId;
        this.resourceId = resourceId;
        this.name = name;
        this.description = description;
        this.quantity = quantity;
        this.donationLocation = donationLocation;
        this.createdByUid = createdByUid;
        this.createdAt = Timestamp.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNgoId() { return ngoId; }
    public String getResourceId() { return resourceId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getQuantity() { return quantity; }
    public String getDonationLocation() { return donationLocation; }
    public String getCreatedByUid() { return createdByUid; }
    public Timestamp getCreatedAt() { return createdAt; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        if (id != null) m.put("id", id);
        m.put("ngoId", ngoId);
        m.put("resourceId", resourceId == null ? "" : resourceId);
        m.put("name", name);
        m.put("description", description == null ? "" : description);
        m.put("quantity", quantity);
        m.put("donationLocation", donationLocation == null ? "" : donationLocation);
        m.put("createdByUid", createdByUid);
        m.put("createdAt", createdAt == null ? Timestamp.now() : createdAt);
        return m;
    }
}
