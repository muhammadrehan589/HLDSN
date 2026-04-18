package com.example.hldsn.home;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;
import com.example.hldsn.login_module.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;

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
            Class<?> destination = FirebaseAuth.getInstance().getCurrentUser() != null
                    ? HomePageActivity.class
                    : LoginActivity.class;

            Intent intent = new Intent(LaunchActivity.this, destination);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, SPLASH_DELAY);
    }
}
