package com.example.hldsn.login_module;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.hldsn.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SaveUserProfileActivity extends AppCompatActivity {

    private static final String TAG = "SaveUserProfileActivity";
    private static final int PERMISSION_REQUEST = 200;

    private EditText nameField, addressField, phoneField;
    private EditText ageField, bloodField, heightField, weightField;
    private EditText allergyField1, allergyField2, allergyField3;
    private EditText contactField1, contactField2, contactField3;
    private EditText injuryField1, injuryField2, injuryField3;
    private ImageView profileImg;
    private ImageView backButton;
    private Button saveButton;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private Uri selectedImageUri;
    private String uploadedImageUrl;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_save_profile);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();
        initLaunchers();
        checkPermissions();

        saveButton.setOnClickListener(v -> saveProfile());
    }

    private void initViews() {
        nameField = findViewById(R.id.nameField);
        addressField = findViewById(R.id.addressField);
        phoneField = findViewById(R.id.phoneField);
        ageField = findViewById(R.id.ageField);
        bloodField = findViewById(R.id.bloodField);
        heightField = findViewById(R.id.heightField);
        weightField = findViewById(R.id.weightField);

        allergyField1 = findViewById(R.id.allergyField1);
        allergyField2 = findViewById(R.id.allergyField2);
        allergyField3 = findViewById(R.id.allergyField3);

        injuryField1 = findViewById(R.id.injuryField1);
        injuryField2 = findViewById(R.id.injuryField2);
        injuryField3 = findViewById(R.id.injuryField3);

       contactField1=findViewById(R.id.contactField1);
       contactField2=findViewById(R.id.contactField2);
       contactField3=findViewById(R.id.contactField3);

        profileImg = findViewById(R.id.profileImage);
        backButton = findViewById(R.id.backButton);
        saveButton = findViewById(R.id.saveButton);

        // Set click listener for profile image
        profileImg.setOnClickListener(v -> showImageDialog());

        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, UserProfileActivity.class);
                startActivity(intent);
                finish();
            });
        }
    }

    private void initLaunchers() {
        galleryLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                selectedImageUri = result.getData().getData();
                showImagePreview();
            }
        });

        cameraLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                showImagePreview();
            }
        });
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST);
        }
    }

    private void showImageDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Select Profile Picture")
                .setItems(new String[]{"Camera", "Gallery"}, (dialog, which) -> {
                    if (which == 0) openCamera();
                    else openGallery();
                })
                .show();
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private void openCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "Profile Photo");
        selectedImageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, selectedImageUri);
        cameraLauncher.launch(intent);
    }

    private void showImagePreview() {
        if (selectedImageUri != null) {
            Glide.with(this)
                    .load(selectedImageUri)
                    .apply(RequestOptions.circleCropTransform())
                    .into(profileImg);
        }
    }

    private void uploadProfileImage(Runnable onSuccess) {
        if (selectedImageUri == null) {
            onSuccess.run();
            return;
        }

        Request request = new Request.Builder()
                .url("https://save-image.up.railway.app/api/imagekit/auth")
                .build();

        new OkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(SaveUserProfileActivity.this, "Network error during image upload", Toast.LENGTH_SHORT).show();
                    saveButton.setEnabled(true);
                    saveButton.setText("Save Profile");
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws java.io.IOException {
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    String token = json.getString("token");
                    String signature = json.getString("signature");
                    long expire = json.getLong("expire");

                    uploadToImageKit(token, signature, expire, onSuccess);
                } catch (Exception e) {
                    Log.e(TAG, "Auth parse error", e);
                    runOnUiThread(() -> {
                        Toast.makeText(SaveUserProfileActivity.this, "Failed to authenticate upload", Toast.LENGTH_SHORT).show();
                        saveButton.setEnabled(true);
                        saveButton.setText("Save Profile");
                    });
                }
            }
        });
    }

    private void uploadToImageKit(String token, String signature, long expire, Runnable onSuccess) {
        try {
            byte[] bytes = readBytes(selectedImageUri);
            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);

            RequestBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", base64)
                    .addFormDataPart("fileName", "profile_" + System.currentTimeMillis() + ".jpg")
                    .addFormDataPart("publicKey", "public_Es0vI4aIZOpz+Pa4KLc0GI6jVcA=")
                    .addFormDataPart("signature", signature)
                    .addFormDataPart("token", token)
                    .addFormDataPart("expire", String.valueOf(expire))
                    .addFormDataPart("folder", "/profiles")
                    .build();

            Request request = new Request.Builder()
                    .url("https://upload.imagekit.io/api/v1/files/upload")
                    .post(body)
                    .build();

            new OkHttpClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(SaveUserProfileActivity.this, "Image upload failed", Toast.LENGTH_SHORT).show();
                        saveButton.setEnabled(true);
                        saveButton.setText("Save Profile");
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws java.io.IOException {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        uploadedImageUrl = json.getString("url");
                        runOnUiThread(onSuccess);
                    } catch (Exception e) {
                        Log.e(TAG, "Image upload response parse error", e);
                        runOnUiThread(() -> {
                            Toast.makeText(SaveUserProfileActivity.this, "Failed to process image", Toast.LENGTH_SHORT).show();
                            saveButton.setEnabled(true);
                            saveButton.setText("Save Profile");
                        });
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "File read error", e);
            runOnUiThread(() -> {
                Toast.makeText(SaveUserProfileActivity.this, "Failed to read image", Toast.LENGTH_SHORT).show();
                saveButton.setEnabled(true);
                saveButton.setText("Save Profile");
            });
        }
    }

    private byte[] readBytes(Uri uri) throws Exception {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(data)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        inputStream.close();
        return buffer.toByteArray();
    }

    private void saveProfile() {
        String uid = auth.getCurrentUser().getUid();
        if (uid == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            return;
        }

        saveButton.setEnabled(false);
        saveButton.setText("Saving...");

        // First upload image if selected, then save profile
        uploadProfileImage(() -> saveProfileToFirestore(uid));
    }

    private void saveProfileToFirestore(String uid) {
        Map<String, Object> profile = new HashMap<>();

        // Only add fields that have values
        String name = nameField.getText().toString().trim();
        if (!name.isEmpty()) profile.put("name", name);

        String address = addressField.getText().toString().trim();
        if (!address.isEmpty()) profile.put("address", address);

        String phone = phoneField.getText().toString().trim();
        if (!phone.isEmpty()) profile.put("phone", phone);

        String age = ageField.getText().toString().trim();
        if (!age.isEmpty()) profile.put("age", age);

        String blood = bloodField.getText().toString().trim();
        if (!blood.isEmpty()) profile.put("bloodGroup", blood);

        String height = heightField.getText().toString().trim();
        if (!height.isEmpty()) profile.put("height", height);

        String weight = weightField.getText().toString().trim();
        if (!weight.isEmpty()) profile.put("weight", weight);

        // Add profile image URL if uploaded
        if (uploadedImageUrl != null && !uploadedImageUrl.isEmpty()) {
            profile.put("profileImageUrl", uploadedImageUrl);
        }

        // Allergies
        List<String> allergies = new ArrayList<>();
        addIfNotEmpty(allergies, allergyField1.getText().toString());
        addIfNotEmpty(allergies, allergyField2.getText().toString());
        addIfNotEmpty(allergies, allergyField3.getText().toString());
        if (!allergies.isEmpty()) profile.put("allergies", allergies);

        // Injuries
        List<String> injuries = new ArrayList<>();
        addIfNotEmpty(injuries, injuryField1.getText().toString());
        addIfNotEmpty(injuries, injuryField2.getText().toString());
        addIfNotEmpty(injuries, injuryField3.getText().toString());
        if (!injuries.isEmpty()) profile.put("injuries", injuries);

        List<String> contacts = new ArrayList<>();
        addIfNotEmpty(contacts, contactField1.getText().toString());
        addIfNotEmpty(contacts, contactField2.getText().toString());
        addIfNotEmpty(contacts, contactField3.getText().toString());
        if (!contacts.isEmpty()) profile.put("contacts", contacts);

        // Use set() with merge option to update only provided fields and preserve existing ones
        db.collection("users")
                .document(uid)
                .set(profile, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Profile saved successfully", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(this, UserProfileActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    saveButton.setEnabled(true);
                    saveButton.setText("Save Profile");
                });
    }

    private void addIfNotEmpty(List<String> list, String text) {
        if (text != null && !text.trim().isEmpty()) {
            list.add(text.trim());
        }
    }
}
