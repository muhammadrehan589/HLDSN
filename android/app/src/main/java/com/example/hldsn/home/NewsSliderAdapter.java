package com.example.hldsn.home;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.hldsn.R;

import java.util.ArrayList;
import java.util.List;

class NewsSliderAdapter extends RecyclerView.Adapter<NewsSliderAdapter.NewsSlideViewHolder> {

    private final List<NewsSlideItem> newsItems = new ArrayList<>();
    private final OnSlideClickListener onSlideClickListener;

    interface OnSlideClickListener {
        void onSlideClick(NewsSlideItem item);
    }

    NewsSliderAdapter(OnSlideClickListener onSlideClickListener) {
        this.onSlideClickListener = onSlideClickListener;
    }

    void submitItems(@NonNull List<NewsSlideItem> items) {
        newsItems.clear();
        newsItems.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NewsSlideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_news_slide, parent, false);
        return new NewsSlideViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NewsSlideViewHolder holder, int position) {
        holder.bind(newsItems.get(position), onSlideClickListener);
    }

    @Override
    public int getItemCount() {
        return newsItems.size();
    }

    static class NewsSlideItem {

        private final String headline;
        private final String imageUrl;
        private final String articleUrl;
        private final int fallbackImageResId;

        NewsSlideItem(String headline, String imageUrl, String articleUrl,
                      @DrawableRes int fallbackImageResId) {
            this.headline = headline;
            this.imageUrl = imageUrl;
            this.articleUrl = articleUrl;
            this.fallbackImageResId = fallbackImageResId;
        }

        String getHeadline() {
            return headline;
        }

        String getImageUrl() {
            return imageUrl;
        }

        String getArticleUrl() {
            return articleUrl;
        }

        int getFallbackImageResId() {
            return fallbackImageResId;
        }
    }

    static class NewsSlideViewHolder extends RecyclerView.ViewHolder {

        private final ImageView imageView;
        private final TextView headlineView;

        NewsSlideViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.news_slide_image);
            headlineView = itemView.findViewById(R.id.news_slide_headline);
        }

        void bind(NewsSlideItem item, OnSlideClickListener onSlideClickListener) {
            headlineView.setText(item.getHeadline());
            itemView.setOnClickListener(v -> {
                if (onSlideClickListener != null) {
                    onSlideClickListener.onSlideClick(item);
                }
            });

            if (TextUtils.isEmpty(item.getImageUrl())) {
                Glide.with(imageView)
                        .load(item.getFallbackImageResId())
                        .centerCrop()
                        .into(imageView);
                return;
            }

            Glide.with(imageView)
                    .load(item.getImageUrl())
                    .placeholder(item.getFallbackImageResId())
                    .error(item.getFallbackImageResId())
                    .centerCrop()
                    .into(imageView);
        }
    }
}
