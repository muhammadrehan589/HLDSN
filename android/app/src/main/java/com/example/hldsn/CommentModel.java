package com.example.hldsn;

public class CommentModel {

    private final String author;
    private final String body;
    private final String timeAgo;

    public CommentModel(String author, String body, String timeAgo) {
        this.author = author;
        this.body = body;
        this.timeAgo = timeAgo;
    }

    public String getAuthor() {
        return author;
    }

    public String getBody() {
        return body;
    }

    public String getTimeAgo() {
        return timeAgo;
    }
}
