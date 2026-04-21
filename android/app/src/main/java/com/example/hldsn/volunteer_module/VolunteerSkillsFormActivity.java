package com.example.hldsn.volunteer_module;

import android.content.Intent;
import android.os.Bundle;
import java.util.ArrayList;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;
import com.google.android.material.button.MaterialButton;

public class VolunteerSkillsFormActivity extends AppCompatActivity {

    private MaterialButton nextButton;
    private CheckBox[] skillOptions;
    private String[] skillLabels;
    private CheckBox[] resourceOptions;
    private String[] resourceLabels;
    private CheckBox[] languageOptions;
    private String[] languageLabels;
    private RadioButton[] experienceOptions;
    private String[] experienceLabels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_volunteer_skills_form);

        ImageView backButton = findViewById(R.id.backButton);
        nextButton = findViewById(R.id.nextButton);

        skillOptions = new CheckBox[] {
            findViewById(R.id.cbFirstAid),
            findViewById(R.id.cbMedical),
            findViewById(R.id.cbSearchRescue),
            findViewById(R.id.cbDriving),
            findViewById(R.id.cbEvacuation),
            findViewById(R.id.cbOthers),
            findViewById(R.id.cbFoodWaterDistribution)
        };
        skillLabels = new String[] {
            "First Aid",
            "Medical",
            "Search & Rescue",
            "Driving",
            "Evacuation",
            "Others",
            "Food/Water Distribution"
        };

        resourceOptions = new CheckBox[] {
            findViewById(R.id.cbVehicle),
            findViewById(R.id.cbBoat),
            findViewById(R.id.cbMedicalSupplies),
            findViewById(R.id.cbFoodWater),
            findViewById(R.id.cbTools),
            findViewById(R.id.cbNone)
        };
        resourceLabels = new String[] {
            "Vehicle",
            "Boat",
            "Medical Supplies",
            "Food/Water",
            "Tools",
            "None"
        };

        languageOptions = new CheckBox[] {
                findViewById(R.id.cbUrdu),
                findViewById(R.id.cbEnglish),
                findViewById(R.id.cbPunjabi),
                findViewById(R.id.cbSindhi),
                findViewById(R.id.cbPashto),
                findViewById(R.id.cbBalochi)
        };
        languageLabels = new String[] {
            "Urdu",
            "English",
            "Punjabi",
            "Sindhi",
            "Pashto",
            "Balochi"
        };

        experienceOptions = new RadioButton[] {
                findViewById(R.id.rbBeginner),
                findViewById(R.id.rbIntermediate),
                findViewById(R.id.rbExpert)
        };
        experienceLabels = new String[] {
            "Beginner",
            "Intermediate",
            "Expert"
        };

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        setupSelectionListeners();
        updateNextButtonState();

        if (nextButton != null) {
            nextButton.setOnClickListener(v -> {
                if (!canProceed()) {
                    Toast.makeText(this, "Select at least one language and one experience level", Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent intent = new Intent(this, VolunteerAgreementActivity.class);
                Bundle existing = getIntent().getExtras();
                if (existing != null) {
                    intent.putExtras(new Bundle(existing));
                }
                intent.putStringArrayListExtra(
                        VolunteerFormExtras.SKILLS,
                        collectSelectedValues(skillOptions, skillLabels)
                );
                intent.putStringArrayListExtra(
                        VolunteerFormExtras.RESOURCES,
                        collectSelectedValues(resourceOptions, resourceLabels)
                );
                intent.putStringArrayListExtra(
                        VolunteerFormExtras.LANGUAGES,
                        collectSelectedValues(languageOptions, languageLabels)
                );
                intent.putExtra(VolunteerFormExtras.EXPERIENCE_LEVEL, selectedExperienceLevel());
                startActivity(intent);
            });
        }
    }

    private void setupSelectionListeners() {
        CompoundButton.OnCheckedChangeListener listener = (buttonView, isChecked) -> {
            if (isChecked && isExperienceButton(buttonView)) {
                enforceSingleExperienceSelection((RadioButton) buttonView);
            }
            updateNextButtonState();
        };

        for (CheckBox option : languageOptions) {
            if (option != null) {
                option.setOnCheckedChangeListener(listener);
            }
        }

        for (RadioButton option : experienceOptions) {
            if (option != null) {
                option.setOnCheckedChangeListener(listener);
            }
        }
    }

    private boolean isExperienceButton(CompoundButton button) {
        for (RadioButton option : experienceOptions) {
            if (option == button) {
                return true;
            }
        }
        return false;
    }

    private void enforceSingleExperienceSelection(RadioButton selected) {
        for (RadioButton option : experienceOptions) {
            if (option != null && option != selected && option.isChecked()) {
                option.setChecked(false);
            }
        }
    }

    private void updateNextButtonState() {
        if (nextButton == null) {
            return;
        }
        boolean enabled = canProceed();
        nextButton.setEnabled(enabled);
        nextButton.setAlpha(enabled ? 1f : 0.6f);
    }

    private boolean canProceed() {
        return hasSelectedLanguage() && hasSelectedExperience();
    }

    private boolean hasSelectedLanguage() {
        for (CheckBox option : languageOptions) {
            if (option != null && option.isChecked()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSelectedExperience() {
        for (RadioButton option : experienceOptions) {
            if (option != null && option.isChecked()) {
                return true;
            }
        }
        return false;
    }

    private String selectedExperienceLevel() {
        for (int i = 0; i < experienceOptions.length; i++) {
            RadioButton option = experienceOptions[i];
            if (option != null && option.isChecked()) {
                return experienceLabels[i];
            }
        }
        return "";
    }

    private ArrayList<String> collectSelectedValues(CheckBox[] options, String[] labels) {
        ArrayList<String> values = new ArrayList<>();
        int count = Math.min(options.length, labels.length);
        for (int i = 0; i < count; i++) {
            CheckBox option = options[i];
            if (option != null && option.isChecked()) {
                values.add(labels[i]);
            }
        }
        return values;
    }
}
