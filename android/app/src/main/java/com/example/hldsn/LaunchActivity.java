package com.example.hldsn;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class LaunchActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 3000; // 3 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);

        ImageView background = findViewById(R.id.logoMark);


        // Load animations
        Animation bgAnim = AnimationUtils.loadAnimation(this, R.anim.backgroundimage);
        Animation logoAnim = AnimationUtils.loadAnimation(this, R.anim.logo_anim);


        // Start animations
        background.startAnimation(bgAnim);


        // Navigate to LoginActivity after delay
        new Handler().postDelayed(() -> {
            startActivity(new Intent(LaunchActivity.this, LoginActivity.class));
            finish();
        }, SPLASH_DELAY);
    }
}
