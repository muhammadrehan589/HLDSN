package com.example.hldsn.volunteer;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;
import com.google.android.material.button.MaterialButton;

public class VolunteerSkillsFormActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_volunteer_skills_form);

        ImageView backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        MaterialButton nextButton = findViewById(R.id.nextButton);
        if (nextButton != null) {
            nextButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, VolunteerAgreementActivity.class);
                startActivity(intent);
            });
        }
    }
}
