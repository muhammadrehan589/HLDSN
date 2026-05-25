package com.example.hldsn.ngo_module;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;
import com.example.hldsn.notification_module.UserNotificationStore;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NgoTaskCreationActivity extends AppCompatActivity {

    public static final String EXTRA_VOLUNTEER_UID = "extra_volunteer_uid";
    public static final String EXTRA_VOLUNTEER_NAME = "extra_volunteer_name";

    private EditText taskTitleField;
    private EditText taskDescriptionField;
    private AutoCompleteTextView volunteerSelector;
    private MaterialButton submitButton;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration volunteerListener;

    private final Map<String, VolunteerOption> volunteerOptions = new LinkedHashMap<>();
    private String currentNgoId = "";
    private String currentNgoName = "";
    private String currentUserId = "";
    private String selectedVolunteerUid = "";
    private String preselectedVolunteerUid = "";
    private String preselectedVolunteerName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ngo_task_creation);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        taskTitleField = findViewById(R.id.taskTitleField);
        taskDescriptionField = findViewById(R.id.taskDescriptionField);
        volunteerSelector = findViewById(R.id.volunteerSelector);
        submitButton = findViewById(R.id.submitTaskButton);
        ImageView backButton = findViewById(R.id.backButton);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        preselectedVolunteerUid = safe(getIntent().getStringExtra(EXTRA_VOLUNTEER_UID));
        preselectedVolunteerName = safe(getIntent().getStringExtra(EXTRA_VOLUNTEER_NAME));

        if (taskDescriptionField != null) {
            taskDescriptionField.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    updateSubmitState();
                }
            });
        }

        if (taskTitleField != null) {
            taskTitleField.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    updateSubmitState();
                }
            });
        }

        if (volunteerSelector != null) {
            volunteerSelector.setOnClickListener(v -> volunteerSelector.showDropDown());
            volunteerSelector.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    volunteerSelector.showDropDown();
                }
            });
            volunteerSelector.setOnItemClickListener((parent, view, position, id) -> {
                String selectedName = volunteerSelector.getText() == null ? "" : volunteerSelector.getText().toString();
                VolunteerOption option = volunteerOptions.get(selectedName);
                selectedVolunteerUid = option == null ? "" : option.uid;
                updateSubmitState();
            });
        }

        if (submitButton != null) {
            submitButton.setOnClickListener(v -> submitTask());
        }

        loadNgoContext();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (volunteerListener != null) {
            volunteerListener.remove();
            volunteerListener = null;
        }
    }

    private void loadNgoContext() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentUserId = user.getUid();
        db.collection("users")
                .document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    currentNgoId = safe(documentSnapshot.getString("ngoId"));
                    currentNgoName = firstNonBlank(documentSnapshot.getString("ngoName"), documentSnapshot.getString("name"));
                    if (currentNgoId.isEmpty()) {
                        Toast.makeText(this, "No NGO assigned to this account", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    loadApprovedVolunteers();
                })
                .addOnFailureListener(error -> {
                    Toast.makeText(this, "Could not load NGO profile", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void loadApprovedVolunteers() {
        if (volunteerListener != null) {
            volunteerListener.remove();
        }

        volunteerListener = db.collection("volunteer_applications")
                .whereEqualTo("ngoId", currentNgoId)
                .whereEqualTo("status", "approved")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Could not load volunteers", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    volunteerOptions.clear();
                    List<String> displayNames = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            String uid = safe(doc.getString("uid"));
                            String name = buildVolunteerName(doc);
                            if (uid.isEmpty() || volunteerOptions.containsKey(name)) {
                                continue;
                            }
                            volunteerOptions.put(name, new VolunteerOption(uid, name, doc.getId()));
                            displayNames.add(name);
                        }
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_dropdown_item_1line,
                            displayNames
                    );
                    volunteerSelector.setAdapter(adapter);
                    if (!preselectedVolunteerUid.isEmpty()) {
                        setPreselectedVolunteer();
                    }
                    updateSubmitState();
                });
    }

    private void setPreselectedVolunteer() {
        selectedVolunteerUid = preselectedVolunteerUid;
        if (volunteerSelector != null) {
            volunteerSelector.setText(preselectedVolunteerName, false);
            volunteerSelector.setEnabled(false);
            volunteerSelector.setAlpha(0.75f);
        }
    }

    private void submitTask() {
        String title = safeText(taskTitleField);
        String description = safeText(taskDescriptionField);

        if (title.isEmpty()) {
            if (taskTitleField != null) {
                taskTitleField.setError("Required");
            }
            return;
        }

        if (description.isEmpty()) {
            if (taskDescriptionField != null) {
                taskDescriptionField.setError("Required");
            }
            return;
        }

        if (selectedVolunteerUid.isEmpty()) {
            if (volunteerSelector != null) {
                volunteerSelector.setError("Select a volunteer");
            }
            return;
        }

        VolunteerOption selectedVolunteer = getSelectedVolunteer();
        if (selectedVolunteer == null) {
            if (volunteerSelector != null) {
                volunteerSelector.setError("Select a valid volunteer");
            }
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("ngoId", currentNgoId);
        payload.put("ngoName", currentNgoName);
        payload.put("createdByUid", currentUserId);
        payload.put("taskTitle", title);
        payload.put("taskDescription", description);
        payload.put("volunteerUid", selectedVolunteer.uid);
        payload.put("volunteerName", selectedVolunteer.name);
        payload.put("volunteerApplicationId", selectedVolunteer.applicationId);
        payload.put("status", "pending");
        payload.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        submitButton.setEnabled(false);
        NgoTaskStore.createTask(db, payload, taskId -> {
            UserNotificationStore.createTaskAssignmentNotification(
                    db,
                    selectedVolunteer.uid,
                    currentNgoId,
                    currentNgoName,
                    taskId,
                    title,
                    description,
                    currentUserId,
                    selectedVolunteer.name
            );
            Toast.makeText(this, "Task created", Toast.LENGTH_SHORT).show();
            finish();
        }, error -> {
            submitButton.setEnabled(true);
            submitButton.setAlpha(1f);
            Toast.makeText(this, "Could not create task: " + error.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void updateSubmitState() {
        if (submitButton == null) {
            return;
        }

        boolean enabled = !safeText(taskTitleField).isEmpty()
                && !safeText(taskDescriptionField).isEmpty()
                && !selectedVolunteerUid.isEmpty();
        submitButton.setEnabled(enabled);
        submitButton.setAlpha(enabled ? 1f : 0.6f);
    }

    private VolunteerOption getSelectedVolunteer() {
        for (Map.Entry<String, VolunteerOption> entry : volunteerOptions.entrySet()) {
            if (entry.getValue().uid.equals(selectedVolunteerUid)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String buildVolunteerName(DocumentSnapshot doc) {
        String firstName = safe(doc.getString("firstName"));
        String surname = safe(doc.getString("surname"));
        String fullName = (firstName + " " + surname).trim();
        return fullName.isEmpty() ? "Volunteer" : fullName;
    }

    private String safeText(EditText field) {
        if (field == null || field.getText() == null) {
            return "";
        }
        return field.getText().toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String primary, String fallback) {
        return safe(primary).isEmpty() ? safe(fallback) : safe(primary);
    }

    private static class VolunteerOption {
        final String uid;
        final String name;
        final String applicationId;

        VolunteerOption(String uid, String name, String applicationId) {
            this.uid = uid;
            this.name = name;
            this.applicationId = applicationId;
        }
    }
}