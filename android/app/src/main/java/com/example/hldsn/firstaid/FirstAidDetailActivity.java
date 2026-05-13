package com.example.hldsn.firstaid;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class FirstAidDetailActivity extends AppCompatActivity {

    private static final String PREFS = "first_aid_prefs";
    private static final String KEY_RECENTS = "recent_tip_ids";
    private static final String KEY_FEEDBACK_PREFIX = "feedback_";

    private FirstAidTip tip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_aid_detail);

        int tipId = getIntent().getIntExtra(EmergencyFirstAidActivity.EXTRA_TIP_ID, -1);
        tip = FirstAidTipsRepository.findById(tipId);
        if (tip == null) {
            finish();
            return;
        }

        saveRecentTip(tipId);
        bind();
    }

    private void bind() {
        ImageView back = findViewById(R.id.first_aid_detail_back);
        ImageView icon = findViewById(R.id.first_aid_detail_icon);
        TextView title = findViewById(R.id.first_aid_detail_title);
        TextView severity = findViewById(R.id.first_aid_detail_severity);
        TextView warning = findViewById(R.id.first_aid_warning_text);
        TextView stepsHeading = findViewById(R.id.first_aid_steps_heading);
        TextView doNotHeading = findViewById(R.id.first_aid_do_not_heading);
        LinearLayout stepsContainer = findViewById(R.id.first_aid_steps_container);
        LinearLayout doNotContainer = findViewById(R.id.first_aid_do_not_container);
        MaterialButton callButton = findViewById(R.id.first_aid_call_button);
        MaterialButton yesButton = findViewById(R.id.first_aid_feedback_yes);
        MaterialButton noButton = findViewById(R.id.first_aid_feedback_no);

        icon.setImageResource(FirstAidTipsRepository.resolveIconRes(tip.getIcon()));
        title.setText(tip.getTitle());
        severity.setText(tip.getSeverity());
        warning.setText("Call emergency help if the person is in serious danger.");

        if ("Critical".equalsIgnoreCase(tip.getSeverity())) {
            severity.setTextColor(Color.parseColor("#B71C1C"));
            severity.setBackgroundColor(Color.parseColor("#FFEBEE"));
        } else if ("Serious".equalsIgnoreCase(tip.getSeverity())) {
            severity.setTextColor(Color.parseColor("#E65100"));
            severity.setBackgroundColor(Color.parseColor("#FFF3E0"));
        } else {
            severity.setTextColor(Color.parseColor("#2E7D32"));
            severity.setBackgroundColor(Color.parseColor("#E8F5E9"));
        }

        fillSteps(stepsContainer, tip.getSteps(), true);
        fillSteps(doNotContainer, tip.getDoNot(), false);

        stepsHeading.setOnClickListener(v -> toggleSection(stepsContainer));
        doNotHeading.setOnClickListener(v -> toggleSection(doNotContainer));

        back.setOnClickListener(v -> finish());
        callButton.setOnClickListener(v -> {
            Intent dial = new Intent(Intent.ACTION_DIAL);
            dial.setData(Uri.parse("tel:1122"));
            startActivity(dial);
        });

        String feedback = getFeedback(tip.getId());
        updateFeedbackState(yesButton, noButton, feedback);

        yesButton.setOnClickListener(v -> {
            saveFeedback(tip.getId(), "yes");
            updateFeedbackState(yesButton, noButton, "yes");
            Toast.makeText(this, "Thanks for your feedback", Toast.LENGTH_SHORT).show();
        });

        noButton.setOnClickListener(v -> {
            saveFeedback(tip.getId(), "no");
            updateFeedbackState(yesButton, noButton, "no");
            Toast.makeText(this, "Thanks for your feedback", Toast.LENGTH_SHORT).show();
        });
    }

    private void fillSteps(LinearLayout container, List<String> steps, boolean numbered) {
        container.removeAllViews();
        for (int i = 0; i < steps.size(); i++) {
            TextView item = new TextView(this);
            item.setTextSize(15f);
            item.setTextColor(Color.parseColor(numbered ? "#212121" : "#B71C1C"));
            item.setPadding(0, 8, 0, 8);
            if (numbered) {
                item.setText((i + 1) + ". " + steps.get(i));
            } else {
                item.setText("X " + steps.get(i));
            }
            container.addView(item);
        }
    }

    private void toggleSection(View section) {
        section.setVisibility(section.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private void updateFeedbackState(MaterialButton yesButton, MaterialButton noButton, String feedback) {
        if ("yes".equals(feedback)) {
            yesButton.setBackgroundColor(Color.parseColor("#2E7D32"));
            yesButton.setTextColor(Color.WHITE);
            noButton.setBackgroundColor(Color.parseColor("#FFFFFF"));
            noButton.setTextColor(Color.parseColor("#666666"));
        } else if ("no".equals(feedback)) {
            noButton.setBackgroundColor(Color.parseColor("#D32F2F"));
            noButton.setTextColor(Color.WHITE);
            yesButton.setBackgroundColor(Color.parseColor("#FFFFFF"));
            yesButton.setTextColor(Color.parseColor("#666666"));
        } else {
            yesButton.setBackgroundColor(Color.parseColor("#FFFFFF"));
            noButton.setBackgroundColor(Color.parseColor("#FFFFFF"));
            yesButton.setTextColor(Color.parseColor("#666666"));
            noButton.setTextColor(Color.parseColor("#666666"));
        }
    }

    private void saveFeedback(int tipId, String value) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_FEEDBACK_PREFIX + tipId, value)
                .apply();
    }

    private String getFeedback(int tipId) {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_FEEDBACK_PREFIX + tipId, "");
    }

    private void saveRecentTip(int tipId) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String raw = prefs.getString(KEY_RECENTS, "");
        List<Integer> ids = new ArrayList<>();
        if (raw != null && !raw.trim().isEmpty()) {
            String[] parts = raw.split(",");
            for (String part : parts) {
                try {
                    int id = Integer.parseInt(part.trim());
                    if (id != tipId && !ids.contains(id)) {
                        ids.add(id);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        ids.add(0, tipId);
        while (ids.size() > 8) {
            ids.remove(ids.size() - 1);
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) builder.append(',');
            builder.append(ids.get(i));
        }
        prefs.edit().putString(KEY_RECENTS, builder.toString()).apply();
    }
}


