package com.example.hldsn.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.hldsn.R;
import com.example.hldsn.services.news.NewsItem;

import java.util.ArrayList;
import java.util.List;

import android.widget.ProgressBar;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import android.graphics.drawable.Drawable;
import androidx.annotation.Nullable;

public class NewsCarouselAdapter extends RecyclerView.Adapter<NewsCarouselAdapter.ViewHolder> {

    private final List<NewsItem> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(NewsItem item);
    }

    public NewsCarouselAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<NewsItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_news_carousel, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NewsItem item = items.get(position);
        
        holder.shimmerLayout.setVisibility(View.VISIBLE);
        holder.shimmerLayout.startShimmer();
        holder.progressBar.setVisibility(View.VISIBLE);

        if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
            Glide.with(holder.imageView.getContext())
                    .load(item.getImageUrl())
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            holder.shimmerLayout.stopShimmer();
                            holder.shimmerLayout.setVisibility(View.GONE);
                            holder.progressBar.setVisibility(View.GONE);
                            holder.imageView.setImageResource(R.drawable.flood_banner);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            holder.shimmerLayout.stopShimmer();
                            holder.shimmerLayout.setVisibility(View.GONE);
                            holder.progressBar.setVisibility(View.GONE);
                            return false;
                        }
                    })
                    .into(holder.imageView);
        } else {
            holder.shimmerLayout.stopShimmer();
            holder.shimmerLayout.setVisibility(View.GONE);
            holder.progressBar.setVisibility(View.GONE);
            holder.imageView.setImageResource(R.drawable.flood_banner);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ShimmerFrameLayout shimmerLayout;
        ProgressBar progressBar;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.carousel_image);
            shimmerLayout = itemView.findViewById(R.id.carousel_shimmer);
            progressBar = itemView.findViewById(R.id.carousel_progress);
        }
    }
}
