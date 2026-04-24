package com.example.hldsn.services.news;

import java.util.List;

public interface NewsRepository {

    interface Callback {
        void onSuccess(List<NewsItem> items);

        void onError(String message);
    }

    void fetchNews(boolean forceRefresh, Callback callback);
}

