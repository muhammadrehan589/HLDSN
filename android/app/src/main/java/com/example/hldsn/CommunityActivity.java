package com.example.hldsn;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class CommunityActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_community_messages);

        ImageView backIcon = findViewById(R.id.backIcon);
        if (backIcon != null) {
            backIcon.setOnClickListener(v -> finish());
        }

        FloatingActionButton addFab = findViewById(R.id.addCommunityPostFab);
        if (addFab != null) {
            addFab.setOnClickListener(v ->
                    startActivity(new Intent(CommunityActivity.this, CommunityReportActivity.class)));
        }
    }
}
