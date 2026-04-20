package com.example.hldsn.volunteer_module;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;
import com.example.hldsn.home.HomePageActivity;
import com.google.android.material.button.MaterialButton;

public class VolunteerSuccessActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_volunteer_success);

        ImageView backButton = findViewById(R.id.backButton);
        MaterialButton gotItButton = findViewById(R.id.gotItButton);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        if (gotItButton != null) {
            gotItButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, HomePageActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }
    }
}
