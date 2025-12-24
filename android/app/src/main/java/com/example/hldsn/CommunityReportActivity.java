package com.example.hldsn;

import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class CommunityReportActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_community);

        ImageView backIcon = findViewById(R.id.backIcon);
        if (backIcon != null) {
            backIcon.setOnClickListener(v -> finish());
        }
    }
}
