package com.example.hldsn.services.safety_tips;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.hldsn.R;
import com.example.hldsn.home.HomePageActivity;
import com.example.hldsn.login_module.LoginActivity;
import com.example.hldsn.login_module.SaveUserProfileActivity;
import com.example.hldsn.login_module.UserProfileActivity;
import com.example.hldsn.ngo_module.NgoRegistrationRequestActivity;
import com.example.hldsn.volunteer_module.VolunteerAssignedTasksActivity;
import com.google.firebase.auth.FirebaseAuth;

public class SafetyTipsActivity extends AppCompatActivity {

    ImageView floodTips, earthquakeTips, heatwaveTips, landslideTips;
    ImageView menuIcon, notificationIcon, backArrow;
    TextView notificationCount;
    DrawerLayout drawerLayout;
    FirebaseAuth auth;

@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.safety_tips_container);
        auth = FirebaseAuth.getInstance();
        initViews();
        initHeader();
        initMenuListeners();
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

    void initMenuListeners(){
        View menuDrawerContainer = findViewById(R.id.menuDrawerContainer);
        if (menuDrawerContainer == null) {
            return;
        }

        View homeMenuItem = menuDrawerContainer.findViewById(R.id.homeMenuItem);
        if (homeMenuItem != null) {
            homeMenuItem.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(this, HomePageActivity.class));
                finish();
            });
        }

        View assignedTasksMenuItem = menuDrawerContainer.findViewById(R.id.assignedTasksMenuItem);
        if (assignedTasksMenuItem != null) {
            assignedTasksMenuItem.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(this, VolunteerAssignedTasksActivity.class));
            });
        }

        View profileMenuItem = menuDrawerContainer.findViewById(R.id.profileMenuItem);
        if (profileMenuItem != null) {
            profileMenuItem.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                Intent intent = new Intent(this,
                        auth.getCurrentUser() != null ? UserProfileActivity.class : SaveUserProfileActivity.class);
                startActivity(intent);
            });
        }

        View aboutMenuItem = menuDrawerContainer.findViewById(R.id.aboutMenuItem);
        if (aboutMenuItem != null) {
            aboutMenuItem.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                showAboutUsDialog();
            });
        }

        View ngoRegistrationItem = menuDrawerContainer.findViewById(R.id.ngoRegistrationMenuItem);
        if (ngoRegistrationItem != null) {
            ngoRegistrationItem.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(this, NgoRegistrationRequestActivity.class));
            });
        }

        View logoutMenuItem = menuDrawerContainer.findViewById(R.id.logoutMenuItem);
        if (logoutMenuItem != null) {
            logoutMenuItem.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                auth.signOut();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
        }

        View drawerBackButton = menuDrawerContainer.findViewById(R.id.backButton);
        if (drawerBackButton != null) {
            drawerBackButton.setOnClickListener(v -> {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    onBackPressed();
                }
            });
        }
    }

    private void showAboutUsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_about_us, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        Button closeButton = dialogView.findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.90),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
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
