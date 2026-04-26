package com.example.hldsn.services.news;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class NewsActivity extends AppCompatActivity {

    private static final String TAG = "NewsActivity";
    private static final int PAGE_SIZE = 10;

    private RecyclerView newsRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private MaterialButton nextPageButton;
    private NewsAdapter newsAdapter;
    private NewsRepository remoteRepository;
    private final NewsRepository fallbackRepository = new DummyNewsRepository();
    private final List<NewsItem> fullNewsList = new ArrayList<>();
    private int currentPage = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_news);

        remoteRepository = new ApiNewsRepository(this);

        ImageView backArrow = findViewById(R.id.backArrow);
        backArrow.setOnClickListener(v -> finish());

        swipeRefreshLayout = findViewById(R.id.newsSwipeRefresh);
        nextPageButton = findViewById(R.id.btnNewsNextPage);
        newsRecyclerView = findViewById(R.id.newsRecyclerView);
        newsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        newsAdapter = new NewsAdapter(this::openNewsDetail);
        newsRecyclerView.setAdapter(newsAdapter);

        swipeRefreshLayout.setOnRefreshListener(() -> loadNews(true));
        nextPageButton.setOnClickListener(v -> showNextPage());
        swipeRefreshLayout.setRefreshing(true);
        loadNews(false);
    }

    private void loadNews(boolean forceRefresh) {
        Log.d(TAG, "loadNews(forceRefresh=" + forceRefresh + ")");
        remoteRepository.fetchNews(forceRefresh, new NewsRepository.Callback() {
            @Override
            public void onSuccess(java.util.List<NewsItem> items) {
                Log.d(TAG, "Remote news success. itemCount=" + items.size());
                swipeRefreshLayout.setRefreshing(false);
                applyFullNewsList(items);
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Remote news failed. Falling back to dummy data. reason=" + message);
                fallbackRepository.fetchNews(forceRefresh, new NewsRepository.Callback() {
                    @Override
                    public void onSuccess(java.util.List<NewsItem> items) {
                        Log.d(TAG, "Fallback news success. itemCount=" + items.size());
                        swipeRefreshLayout.setRefreshing(false);
                        applyFullNewsList(items);
                        Toast.makeText(NewsActivity.this, R.string.news_fallback_notice, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String fallbackMessage) {
                        Log.e(TAG, "Fallback news failed. reason=" + fallbackMessage);
                        swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(NewsActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void applyFullNewsList(List<NewsItem> items) {
        fullNewsList.clear();
        if (items != null) {
            fullNewsList.addAll(items);
        }
        currentPage = 0;
        showCurrentPage();
    }

    private void showCurrentPage() {
        int startIndex = currentPage * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, fullNewsList.size());
        if (startIndex >= endIndex) {
            newsAdapter.submitList(new ArrayList<>());
            nextPageButton.setVisibility(android.view.View.GONE);
            return;
        }

        List<NewsItem> pageItems = new ArrayList<>(fullNewsList.subList(startIndex, endIndex));
        newsAdapter.submitList(pageItems);
        boolean hasMore = endIndex < fullNewsList.size();
        nextPageButton.setVisibility(hasMore ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void showNextPage() {
        currentPage++;
        showCurrentPage();
    }

    private void openNewsDetail(NewsItem item) {
        Intent intent = new Intent(this, NewsDetailActivity.class);
        intent.putExtra(NewsDetailActivity.EXTRA_HEADLINE, item.getHeadline());
        intent.putExtra(NewsDetailActivity.EXTRA_DESCRIPTION, item.getDescription());
        intent.putExtra(NewsDetailActivity.EXTRA_FULL_TEXT, item.getFullText());
        intent.putExtra(NewsDetailActivity.EXTRA_LOCATION, item.getLocation());
        intent.putExtra(NewsDetailActivity.EXTRA_PUBLISHED_AT, item.getPublishedAt());
        intent.putExtra(NewsDetailActivity.EXTRA_IMAGE_URL, item.getImageUrl());
        intent.putExtra(NewsDetailActivity.EXTRA_IMAGE_RES_ID, item.getImageResId());
        startActivity(intent);
    }
}

