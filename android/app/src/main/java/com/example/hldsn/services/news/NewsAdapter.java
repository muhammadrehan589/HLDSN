package com.example.hldsn.services.news;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.hldsn.R;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import androidx.annotation.Nullable;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;

public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.NewsViewHolder> {

    private final List<NewsItem> newsItems = new ArrayList<>();
    private final OnNewsClickListener onNewsClickListener;

    public interface OnNewsClickListener {
        void onNewsClick(NewsItem item);
    }

    public NewsAdapter(OnNewsClickListener onNewsClickListener) {
        this.onNewsClickListener = onNewsClickListener;
    }

    @NonNull
    @Override
    public NewsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_news, parent, false);
        return new NewsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NewsViewHolder holder, int position) {
        NewsItem item = newsItems.get(position);

        // Clear any previous request first to avoid recycled-image flicker.
        Glide.with(holder.itemView.getContext()).clear(holder.newsImage);

        if (item.getImageUrl() != null && !item.getImageUrl().trim().isEmpty()) {
            holder.shimmerViewContainer.setVisibility(View.VISIBLE);
            holder.shimmerViewContainer.startShimmer();
            
            Glide.with(holder.itemView.getContext())
                    .load(item.getImageUrl())
                    .thumbnail(0.25f)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .dontAnimate()
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            holder.shimmerViewContainer.stopShimmer();
                            holder.shimmerViewContainer.setVisibility(View.GONE);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            holder.shimmerViewContainer.stopShimmer();
                            holder.shimmerViewContainer.setVisibility(View.GONE);
                            return false;
                        }
                    })
                    .error(item.getImageResId())
                    .into(holder.newsImage);
        } else {
            holder.shimmerViewContainer.stopShimmer();
            holder.shimmerViewContainer.setVisibility(View.GONE);
            holder.newsImage.setImageResource(item.getImageResId());
        }
        holder.newsHeadline.setText(item.getHeadline());
        holder.newsDescription.setText(item.getDescription());
        holder.itemView.setOnClickListener(v -> {
            if (onNewsClickListener != null) {
                onNewsClickListener.onNewsClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return newsItems.size();
    }

    @Override
    public void onViewRecycled(@NonNull NewsViewHolder holder) {
        super.onViewRecycled(holder);
        Glide.with(holder.itemView.getContext()).clear(holder.newsImage);
    }

    public void submitList(List<NewsItem> items) {
        newsItems.clear();
        newsItems.addAll(items);
        notifyDataSetChanged();
    }

    static class NewsViewHolder extends RecyclerView.ViewHolder {
        ImageView newsImage;
        TextView newsHeadline;
        TextView newsDescription;
        ShimmerFrameLayout shimmerViewContainer;

        NewsViewHolder(@NonNull View itemView) {
            super(itemView);
            newsImage = itemView.findViewById(R.id.newsImage);
            newsHeadline = itemView.findViewById(R.id.newsHeadline);
            newsDescription = itemView.findViewById(R.id.newsDescription);
            shimmerViewContainer = itemView.findViewById(R.id.shimmerViewContainer);
        }
    }
}

