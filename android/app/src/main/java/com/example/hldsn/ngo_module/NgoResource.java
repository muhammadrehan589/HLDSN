package com.example.hldsn.ngo_module;

import com.google.firebase.Timestamp;

import java.util.HashMap;
import java.util.Map;

public class NgoResource {
    private String id;
    private String ngoId;
    private String name;
    private String description;
    private int quantity;
    private String location;
    private String createdByUid;
    private Timestamp createdAt;

    public NgoResource() {}

    public NgoResource(String ngoId, String name, String description, int quantity, String location, String createdByUid) {
        this.ngoId = ngoId;
        this.name = name;
        this.description = description;
        this.quantity = quantity;
        this.location = location;
        this.createdByUid = createdByUid;
        this.createdAt = Timestamp.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNgoId() { return ngoId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getQuantity() { return quantity; }
    public String getLocation() { return location; }
    public String getCreatedByUid() { return createdByUid; }
    public Timestamp getCreatedAt() { return createdAt; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        if (id != null) m.put("id", id);
        m.put("ngoId", ngoId);
        m.put("name", name);
        m.put("description", description == null ? "" : description);
        m.put("quantity", quantity);
        m.put("location", location == null ? "" : location);
        m.put("createdByUid", createdByUid);
        m.put("createdAt", createdAt == null ? Timestamp.now() : createdAt);
        return m;
    }
}
