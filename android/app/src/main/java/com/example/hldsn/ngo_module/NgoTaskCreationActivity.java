package com.example.hldsn.ngo_module;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;
import com.example.hldsn.notification_module.UserNotificationStore;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class NgoTaskCreationActivity extends AppCompatActivity {

    public static final String EXTRA_VOLUNTEER_UID = "extra_volunteer_uid";
    public static final String EXTRA_VOLUNTEER_NAME = "extra_volunteer_name";

    private EditText taskTitleField;
    private EditText taskDescriptionField;
    private TextView volunteerSelector;
    private MaterialButton submitButton;
    private MaterialButton deadlinePickerButton;
    private TextView deadlineDateText;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration volunteerListener;

    private final List<VolunteerOption> allVolunteers = new ArrayList<>();
    private final List<VolunteerOption> selectedVolunteers = new ArrayList<>();
    private String currentNgoId = "";
    private String currentNgoName = "";
    private String currentUserId = "";
    private String preselectedVolunteerUid = "";
    private String preselectedVolunteerName = "";
    private Date selectedDeadline = null;
    private boolean isPreselectedLocked = false;

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
        deadlinePickerButton = findViewById(R.id.deadlinePickerButton);
        deadlineDateText = findViewById(R.id.deadlineDateText);
        ImageView backButton = findViewById(R.id.backButton);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        if (deadlinePickerButton != null) {
            deadlinePickerButton.setOnClickListener(v -> showDeadlinePicker());
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
            volunteerSelector.setOnClickListener(v -> {
                if (!isPreselectedLocked) {
                    showVolunteerSelectionDialog();
                }
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

                    allVolunteers.clear();
                    Set<String> seenUids = new HashSet<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            String uid = safe(doc.getString("uid"));
                            String name = buildVolunteerName(doc);
                            if (uid.isEmpty() || seenUids.contains(uid)) {
                                continue;
                            }
                            seenUids.add(uid);
                            allVolunteers.add(new VolunteerOption(uid, name, doc.getId()));
                        }
                    }

                    if (!preselectedVolunteerUid.isEmpty()) {
                        setPreselectedVolunteer();
                    }
                    updateSubmitState();
                });
    }

    private void setPreselectedVolunteer() {
        isPreselectedLocked = true;
        selectedVolunteers.clear();

        // Try to find the preselected volunteer in the loaded list
        for (VolunteerOption v : allVolunteers) {
            if (v.uid.equals(preselectedVolunteerUid)) {
                selectedVolunteers.add(v);
                break;
            }
        }

        // If not found (e.g. loaded before snapshot arrived), create a temporary entry
        if (selectedVolunteers.isEmpty()) {
            selectedVolunteers.add(new VolunteerOption(preselectedVolunteerUid, preselectedVolunteerName, ""));
        }

        updateVolunteerDisplay();
        if (volunteerSelector != null) {
            volunteerSelector.setAlpha(0.75f);
        }
    }

    private void showVolunteerSelectionDialog() {
        if (allVolunteers.isEmpty()) {
            Toast.makeText(this, "No approved volunteers available", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[allVolunteers.size()];
        boolean[] checked = new boolean[allVolunteers.size()];

        // Build a set of currently selected UIDs for quick lookup
        Set<String> selectedUids = new HashSet<>();
        for (VolunteerOption sv : selectedVolunteers) {
            selectedUids.add(sv.uid);
        }

        for (int i = 0; i < allVolunteers.size(); i++) {
            names[i] = allVolunteers.get(i).name;
            checked[i] = selectedUids.contains(allVolunteers.get(i).uid);
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Volunteers")
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> {
                    checked[which] = isChecked;
                })
                .setPositiveButton("OK", (dialog, which) -> {
                    selectedVolunteers.clear();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) {
                            selectedVolunteers.add(allVolunteers.get(i));
                        }
                    }
                    updateVolunteerDisplay();
                    updateSubmitState();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateVolunteerDisplay() {
        if (volunteerSelector == null) {
            return;
        }
        if (selectedVolunteers.isEmpty()) {
            volunteerSelector.setText(null);
            volunteerSelector.setHint("Tap to select volunteers");
        } else if (selectedVolunteers.size() == 1) {
            volunteerSelector.setText(selectedVolunteers.get(0).name);
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < selectedVolunteers.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(selectedVolunteers.get(i).name);
            }
            volunteerSelector.setText(sb.toString());
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

        if (selectedVolunteers.isEmpty()) {
            Toast.makeText(this, "Select at least one volunteer", Toast.LENGTH_SHORT).show();
            return;
        }

        submitButton.setEnabled(false);

        int totalTasks = selectedVolunteers.size();
        int[] completedCount = {0};
        int[] failedCount = {0};

        for (VolunteerOption volunteer : selectedVolunteers) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("ngoId", currentNgoId);
            payload.put("ngoName", currentNgoName);
            payload.put("createdByUid", currentUserId);
            payload.put("taskTitle", title);
            payload.put("taskDescription", description);
            payload.put("volunteerUid", volunteer.uid);
            payload.put("volunteerName", volunteer.name);
            payload.put("volunteerApplicationId", volunteer.applicationId);
            payload.put("status", "pending");
            payload.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
            if (selectedDeadline != null) {
                payload.put("deadline", new Timestamp(selectedDeadline));
            }

            NgoTaskStore.createTask(db, payload, taskId -> {
                UserNotificationStore.createTaskAssignmentNotification(
                        db,
                        volunteer.uid,
                        currentNgoId,
                        currentNgoName,
                        taskId,
                        title,
                        description,
                        currentUserId,
                        volunteer.name
                );
                completedCount[0]++;
                checkAllTasksSubmitted(totalTasks, completedCount[0], failedCount[0]);
            }, error -> {
                failedCount[0]++;
                checkAllTasksSubmitted(totalTasks, completedCount[0], failedCount[0]);
            });
        }
    }

    private void checkAllTasksSubmitted(int total, int completed, int failed) {
        if (completed + failed < total) {
            return;
        }
        if (failed == 0) {
            String msg = total == 1 ? "Task created" : total + " tasks created successfully";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, completed + " created, " + failed + " failed", Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private void updateSubmitState() {
        if (submitButton == null) {
            return;
        }

        boolean enabled = !safeText(taskTitleField).isEmpty()
                && !safeText(taskDescriptionField).isEmpty()
                && !selectedVolunteers.isEmpty();
        submitButton.setEnabled(enabled);
        submitButton.setAlpha(enabled ? 1f : 0.6f);
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

    private void showDeadlinePicker() {
        Calendar calendar = Calendar.getInstance();
        if (selectedDeadline != null) {
            calendar.setTime(selectedDeadline);
        }

        DatePickerDialog picker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar chosen = Calendar.getInstance();
                    chosen.set(year, month, dayOfMonth, 23, 59, 59);
                    chosen.set(Calendar.MILLISECOND, 999);
                    selectedDeadline = chosen.getTime();
                    updateDeadlineDisplay();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        picker.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        picker.show();
    }

    private void updateDeadlineDisplay() {
        if (deadlineDateText == null) {
            return;
        }
        if (selectedDeadline == null) {
            deadlineDateText.setText("No deadline set");
            deadlineDateText.setTextColor(0xFF999999);
            if (deadlinePickerButton != null) {
                deadlinePickerButton.setText("Set Deadline");
            }
        } else {
            String formatted = new SimpleDateFormat("dd MMM yyyy", Locale.US).format(selectedDeadline);
            deadlineDateText.setText(formatted);
            deadlineDateText.setTextColor(0xFF1F1F1F);
            if (deadlinePickerButton != null) {
                deadlinePickerButton.setText("Change");
            }
        }
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