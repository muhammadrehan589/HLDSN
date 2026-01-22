package com.example.hldsn;

public class CommentModel {

    private String id;
    private String authorId;
    private String authorName;
    private String body;
    private String parentId; // null for top-level comments
    private java.util.Date createdAt;
    private long likes;
    private long dislikes;

    public CommentModel() {
        // Firestore deserialization
    }

    public CommentModel(String id, String authorId, String authorName, String body, String parentId, java.util.Date createdAt) {
        this.id = id;
        this.authorId = authorId;
        this.authorName = authorName;
        this.body = body;
        this.parentId = parentId;
        this.createdAt = createdAt;
        this.likes = 0;
        this.dislikes = 0;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public java.util.Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.util.Date createdAt) {
        this.createdAt = createdAt;
    }

    public long getLikes() {
        return likes;
    }

    public void setLikes(long likes) {
        this.likes = likes;
    }

    public long getDislikes() {
        return dislikes;
    }

    public void setDislikes(long dislikes) {
        this.dislikes = dislikes;
    }
}
