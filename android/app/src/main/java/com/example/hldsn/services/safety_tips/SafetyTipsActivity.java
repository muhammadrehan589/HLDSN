package com.example.hldsn.services.safety_tips;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.hldsn.R;

public class SafetyTipsActivity extends AppCompatActivity {

    ImageView floodTips, earthquakeTips, heatwaveTips, landslideTips;
    ImageView menuIcon, notificationIcon, backArrow;
    TextView notificationCount;
    DrawerLayout drawerLayout;

@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.safety_tips_container);
        initViews();
        initHeader();
        initListner();
    }
    void initViews(){
        drawerLayout = findViewById(R.id.drawer_layout);
        floodTips=findViewById(R.id.floodImage);
        earthquakeTips=findViewById(R.id.earthquakeImage);
        heatwaveTips=findViewById(R.id.heatwaveImage);
        landslideTips=findViewById(R.id.landslideImage);
    }
    void initHeader(){
        menuIcon = findViewById(R.id.menu_icon);
        notificationIcon = findViewById(R.id.notification_icon);
        notificationCount = findViewById(R.id.tv_notification_count);
        backArrow = findViewById(R.id.backArrow);

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
    void initListner(){
    floodTips.setOnClickListener(v->{
        Intent intent=new Intent(this, SafetyTypeActivity.class);
        intent.putExtra("incidenttype","flood");
        startActivity(intent);
    });
    earthquakeTips.setOnClickListener(v->{
        Intent intent=new Intent(this,SafetyTypeActivity.class);
        intent.putExtra("incidenttype","earthquake");
        startActivity(intent);
    });
    heatwaveTips.setOnClickListener(v->{
        Intent intent=new Intent(this,SafetyTypeActivity.class);
        intent.putExtra("incidenttype","heatwave");
        startActivity(intent);
    });
    landslideTips.setOnClickListener(v->{
        Intent intent=new Intent(this,SafetyTypeActivity.class);
        intent.putExtra("incidenttype","landslide");
        startActivity(intent);
    });

    }

}
