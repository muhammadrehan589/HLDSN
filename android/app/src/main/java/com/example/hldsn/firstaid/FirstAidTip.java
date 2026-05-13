package com.example.hldsn.firstaid;

import java.util.List;

public class FirstAidTip {
    private final int id;
    private final String title;
    private final String category;
    private final String severity;
    private final String icon;
    private final String shortDescription;
    private final List<String> keywords;
    private final List<String> steps;
    private final List<String> doNot;

    public FirstAidTip(
            int id,
            String title,
            String category,
            String severity,
            String icon,
            String shortDescription,
            List<String> keywords,
            List<String> steps,
            List<String> doNot
    ) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.severity = severity;
        this.icon = icon;
        this.shortDescription = shortDescription;
        this.keywords = keywords;
        this.steps = steps;
        this.doNot = doNot;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public String getSeverity() {
        return severity;
    }

    public String getIcon() {
        return icon;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public List<String> getSteps() {
        return steps;
    }

    public List<String> getDoNot() {
        return doNot;
    }
}

