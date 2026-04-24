package com.example.hldsn.services.news;

public class NewsItem {

    private final String headline;
    private final String description;
    private final String fullText;
    private final String location;
    private final String publishedAt;
    private final String imageUrl;
    private final int imageResId;

    public NewsItem(
            String headline,
            String description,
            String fullText,
            String location,
            String publishedAt,
            String imageUrl,
            int imageResId
    ) {
        this.headline = headline;
        this.description = description;
        this.fullText = fullText;
        this.location = location;
        this.publishedAt = publishedAt;
        this.imageUrl = imageUrl;
        this.imageResId = imageResId;
    }

    public NewsItem(
            String headline,
            String description,
            String fullText,
            String location,
            String publishedAt,
            int imageResId
    ) {
        this(headline, description, fullText, location, publishedAt, "", imageResId);
    }

    public String getHeadline() {
        return headline;
    }

    public String getDescription() {
        return description;
    }

    public String getFullText() {
        return fullText;
    }

    public String getLocation() {
        return location;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public int getImageResId() {
        return imageResId;
    }
}

