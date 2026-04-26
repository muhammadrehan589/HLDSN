package com.example.hldsn.services.safety_tips;

import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.core.widget.NestedScrollView;

import com.example.hldsn.R;

public class SafetyTypeActivity extends AppCompatActivity {
    String incidenttype;
    DrawerLayout drawerLayout;
    ImageView menuIcon, notificationIcon, backArrow;
    TextView notificationCount;
    NestedScrollView contentScroll;
    View scrollTrack, scrollThumb;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        incidenttype=getIntent().getStringExtra("incidenttype");
        switch(incidenttype){
            case "flood":
                setContentView(R.layout.flood_tips_container);
                break;
            case "earthquake":
                setContentView(R.layout.earthquake_tips_container);
                break;
            case "heatwave":
                setContentView(R.layout.heatwave_tips_container);
                break;
            case "landslide":
                setContentView(R.layout.landslides_tips_container);
                break;
            default:
                setContentView(R.layout.safety_tips_container);
                break;
        }

        initHeader();
            initScrollThumb();

    }

    private void initHeader() {
        drawerLayout = findViewById(R.id.drawer_layout);
        menuIcon = findViewById(R.id.menu_icon);
        notificationIcon = findViewById(R.id.notification_icon);
        notificationCount = findViewById(R.id.tv_notification_count);
        backArrow = findViewById(R.id.backButton);

        if (notificationCount != null) {
            notificationCount.setVisibility(View.GONE);
        }

        if (menuIcon != null) {
            menuIcon.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }

        if (notificationIcon != null) {
            notificationIcon.setOnClickListener(v -> {
                if (drawerLayout == null) return;
                if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    drawerLayout.closeDrawer(GravityCompat.END);
                } else {
                    drawerLayout.openDrawer(GravityCompat.END);
                }
            });
        }

        if (backArrow != null) {
            backArrow.setOnClickListener(v -> finish());
        }
    }

    private void initScrollThumb() {
        contentScroll = findViewById(R.id.contentScroll);
        scrollTrack = findViewById(R.id.scrollTrack);
        scrollThumb = findViewById(R.id.scrollThumb);

        if (contentScroll == null || scrollTrack == null || scrollThumb == null) {
            return; // layout missing pieces
        }

        ViewTreeObserver vto = contentScroll.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(() -> {
            int trackHeight = scrollTrack.getHeight();
            int thumbHeight = scrollThumb.getHeight();
            final float maxThumbOffset = Math.max(0, trackHeight - thumbHeight);

            contentScroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                View child = contentScroll.getChildAt(0);
                if (child == null) return;
                int contentHeight = child.getHeight();
                int containerHeight = contentScroll.getHeight();
                int scrollRange = Math.max(0, contentHeight - containerHeight);

                float ratio = scrollRange == 0 ? 0f : (float) scrollY / (float) scrollRange;
                float offset = Math.min(maxThumbOffset, Math.max(0f, ratio * maxThumbOffset));
                scrollThumb.setTranslationY(offset);
            });
        });
    }

}
