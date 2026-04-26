package com.example.hldsn.services.news;

import android.os.Bundle;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.hldsn.R;
import com.facebook.shimmer.ShimmerFrameLayout;

import androidx.annotation.Nullable;
import android.graphics.drawable.Drawable;

public class NewsDetailActivity extends AppCompatActivity {

    private static final int COLLAPSED_BODY_LINES = 4;
    private boolean isBodyExpanded;

    public static final String EXTRA_HEADLINE = "extra_news_headline";
    public static final String EXTRA_DESCRIPTION = "extra_news_description";
    public static final String EXTRA_FULL_TEXT = "extra_news_full_text";
    public static final String EXTRA_LOCATION = "extra_news_location";
    public static final String EXTRA_PUBLISHED_AT = "extra_news_published_at";
    public static final String EXTRA_IMAGE_URL = "extra_news_image_url";
    public static final String EXTRA_IMAGE_RES_ID = "extra_news_image_res";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_news_detail);

        ImageView backArrow = findViewById(R.id.backArrow);
        ImageView detailImage = findViewById(R.id.detailNewsImage);
        ShimmerFrameLayout shimmerViewContainer = findViewById(R.id.shimmerViewContainerDetail);
        TextView detailHeadline = findViewById(R.id.detailNewsHeadline);
        TextView detailSummary = findViewById(R.id.detailNewsSummary);
        TextView detailLocation = findViewById(R.id.detailNewsLocation);
        TextView detailDate = findViewById(R.id.detailNewsDate);
        TextView detailBody = findViewById(R.id.detailNewsBody);
        TextView detailReadToggle = findViewById(R.id.detailNewsReadToggle);

        backArrow.setOnClickListener(v -> finish());

        String headline = getIntent().getStringExtra(EXTRA_HEADLINE);
        String description = getIntent().getStringExtra(EXTRA_DESCRIPTION);
        String fullText = getIntent().getStringExtra(EXTRA_FULL_TEXT);
        String location = getIntent().getStringExtra(EXTRA_LOCATION);
        String publishedAt = getIntent().getStringExtra(EXTRA_PUBLISHED_AT);
        String imageUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);
        int imageResId = getIntent().getIntExtra(EXTRA_IMAGE_RES_ID, R.drawable.flood_banner);

        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            shimmerViewContainer.setVisibility(View.VISIBLE);
            shimmerViewContainer.startShimmer();
            Glide.with(this)
                    .load(imageUrl)
                    .thumbnail(0.25f)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .dontAnimate()
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            shimmerViewContainer.stopShimmer();
                            shimmerViewContainer.setVisibility(View.GONE);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            shimmerViewContainer.stopShimmer();
                            shimmerViewContainer.setVisibility(View.GONE);
                            return false;
                        }
                    })
                    .error(imageResId)
                    .into(detailImage);
        } else {
            shimmerViewContainer.stopShimmer();
            shimmerViewContainer.setVisibility(View.GONE);
            detailImage.setImageResource(imageResId);
        }
        detailHeadline.setText(headline == null ? "" : headline);
        detailSummary.setText(description == null ? "" : description);
        detailBody.setText(fullText == null ? "" : fullText);

        bindOptionalMeta(detailLocation, location);
        bindOptionalMeta(detailDate, publishedAt);
        setupReadToggle(detailBody, detailReadToggle);
    }

    private void bindOptionalMeta(TextView textView, String value) {
        if (TextUtils.isEmpty(value)) {
            textView.setVisibility(View.GONE);
            return;
        }
        textView.setText(value);
        textView.setVisibility(View.VISIBLE);
    }

    private void setupReadToggle(TextView detailBody, TextView detailReadToggle) {
        if (TextUtils.isEmpty(detailBody.getText())) {
            detailReadToggle.setVisibility(View.GONE);
            return;
        }

        detailBody.post(() -> {
            if (detailBody.getLineCount() <= COLLAPSED_BODY_LINES) {
                detailReadToggle.setVisibility(View.GONE);
                return;
            }

            detailReadToggle.setVisibility(View.VISIBLE);
            isBodyExpanded = false;
            applyBodyState(detailBody, detailReadToggle);
            detailReadToggle.setOnClickListener(v -> {
                isBodyExpanded = !isBodyExpanded;
                applyBodyState(detailBody, detailReadToggle);
            });
        });
    }

    private void applyBodyState(TextView detailBody, TextView detailReadToggle) {
        if (isBodyExpanded) {
            detailBody.setMaxLines(Integer.MAX_VALUE);
            detailBody.setEllipsize(null);
            detailReadToggle.setText(R.string.news_read_less);
            return;
        }

        detailBody.setMaxLines(COLLAPSED_BODY_LINES);
        detailBody.setEllipsize(TruncateAt.END);
        detailReadToggle.setText(R.string.news_read_more);
    }
}

