package com.example.hldsn;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CommunityActivity extends AppCompatActivity {

    private static final String TAG = "CommunityActivity";
    private static final int PERMISSION_REQUEST = 200;
    private static final int LOCATION_PERMISSION_REQUEST = 201;

    EditText typeField, locationField, descriptionField;
    Button submitReportButton, uploadButton, safeButton;
    ImageView imagePreview;
    ProgressBar uploadProgress;
    Uri selectedMediaUri;
    boolean isSafe = false;

    FirebaseFirestore firestore;
    FirebaseAuth auth;

    ActivityResultLauncher<Intent> galleryLauncher;
    ActivityResultLauncher<Intent> cameraLauncher;

    // ========== LOCATION VARIABLES ==========
    private FusedLocationProviderClient fusedLocationClient;
    private Geocoder geocoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_community);

        initViews();
        initFirebase();
        initLaunchers();
        initListeners();

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        geocoder = new Geocoder(this, Locale.getDefault());

        checkPermissions();
        requestLocationAndFillField(); // Auto-fill location as soon as possible
    }

    private void initViews() {
        typeField = findViewById(R.id.typeField);
        locationField = findViewById(R.id.locationField);
        descriptionField = findViewById(R.id.descriptionField);
        uploadButton = findViewById(R.id.uploadButton);
        submitReportButton = findViewById(R.id.submitReportButton);
        safeButton = findViewById(R.id.safeButton);
        imagePreview = findViewById(R.id.imagePreview);
        uploadProgress = findViewById(R.id.uploadProgress);
    }

    private void initFirebase() {
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) auth.signInAnonymously();
    }

    private void initLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        selectedMediaUri = result.getData().getData();
                        showPreview();
                    }
                });

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        showPreview();
                    }
                });
    }

    private void initListeners() {
        uploadButton.setOnClickListener(v -> showMediaDialog());
        safeButton.setOnClickListener(v -> {
            isSafe = true;
            Toast.makeText(this, "Marked Safe", Toast.LENGTH_SHORT).show();
        });
        submitReportButton.setOnClickListener(v -> submitIncident());
    }

    // ================= PERMISSIONS =================
    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES},
                    PERMISSION_REQUEST);
        }
    }

    // ================= LOCATION LOGIC =================
    private void requestLocationAndFillField() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        }
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Try last known location first (fast)
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        reverseGeocodeAndFill(location);
                    } else {
                        // If no last location, request fresh one
                        CancellationTokenSource cancellationToken = new CancellationTokenSource();
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken.getToken())
                                .addOnSuccessListener(this, this::reverseGeocodeAndFill);
                    }
                });
    }

    private void reverseGeocodeAndFill(Location location) {
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String cityArea = "";

                // Prefer sub-locality or locality
                if (address.getSubLocality() != null) {
                    cityArea = address.getSubLocality();
                } else if (address.getLocality() != null) {
                    cityArea = address.getLocality();
                } else {
                    cityArea = address.getAdminArea(); // fallback to state
                }

                String finalLocation = cityArea + (address.getCountryName() != null ? ", " + address.getCountryName() : "");
                runOnUiThread(() -> locationField.setText(finalLocation));
            }
        } catch (Exception e) {
            Log.e(TAG, "Geocoding failed", e);
            runOnUiThread(() -> locationField.setText("Location detected (unknown area)"));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                Toast.makeText(this, "Location permission denied. Type manually.", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ================= MEDIA PICK =================
    private void showMediaDialog() {
        String[] options = {"Camera", "Gallery"};
        new AlertDialog.Builder(this)
                .setTitle("Select Image Source")
                .setItems(options, (d, i) -> {
                    if (i == 0) openCamera();
                    else openGallery();
                })
                .show();
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    private void openCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "Incident");
        selectedMediaUri = getContentResolver()
                .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, selectedMediaUri);
        cameraLauncher.launch(intent);
    }

    private void showPreview() {
        imagePreview.setVisibility(View.VISIBLE);
        imagePreview.setImageURI(selectedMediaUri);
    }

    // ================= SUBMIT =================
    private void submitIncident() {
        String type = typeField.getText().toString().trim();
        String location = locationField.getText().toString().trim();
        String description = descriptionField.getText().toString().trim();

        if (type.isEmpty() || location.isEmpty()) {
            Toast.makeText(this, "Type & Location required", Toast.LENGTH_SHORT).show();
            return;
        }

        uploadProgress.setVisibility(View.VISIBLE);
        if (selectedMediaUri != null) {
            getImageKitAuthAndUpload(type, location, description);
        } else {
            saveIncidentInFirestore(null, type, location, description);
        }
    }

    // ================= IMAGEKIT AUTH =================
    private void getImageKitAuthAndUpload(String type, String location, String description) {
        Request request = new Request.Builder()
                .url("https://springbootapi-production-cda6.up.railway.app/api/imagekit/auth")
                .build();

        new OkHttpClient().newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                Log.e(TAG, "Auth error", e);
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    uploadToImageKit(json.getString("token"), json.getString("signature"),
                            json.getLong("expire"), type, location, description);
                } catch (Exception e) {
                    Log.e(TAG, "Auth parse error", e);
                }
            }
        });
    }

    // ================= IMAGEKIT UPLOAD =================
    private void uploadToImageKit(String token, String signature, long expire,
                                  String type, String location, String description) {
        try {
            byte[] bytes = readBytes(selectedMediaUri);
            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);

            RequestBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", base64)
                    .addFormDataPart("fileName", "incident_" + System.currentTimeMillis())
                    .addFormDataPart("publicKey", "public_Es0vI4aIZOpz+Pa4KLc0GI6jVcA=")
                    .addFormDataPart("signature", signature)
                    .addFormDataPart("token", token)
                    .addFormDataPart("expire", String.valueOf(expire))
                    .addFormDataPart("folder", "/incidents")
                    .build();

            Request request = new Request.Builder()
                    .url("https://upload.imagekit.io/api/v1/files/upload")
                    .post(body)
                    .build();

            new OkHttpClient().newCall(request).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                    Log.e(TAG, "Upload failed", e);
                }

                @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        saveIncidentInFirestore(json.getString("url"), type, location, description);
                    } catch (Exception e) {
                        Log.e(TAG, "Upload parse error", e);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Upload exception", e);
        }
    }

    // ================= FIRESTORE SAVE =================
    private void saveIncidentInFirestore(String mediaUrl, String type, String location, String description) {
        IncidentModel incident = new IncidentModel(
                auth.getUid(), type, location, description, isSafe, mediaUrl, System.currentTimeMillis());

        firestore.collection("incidents")
                .add(incident)
                .addOnSuccessListener(d -> runOnUiThread(() -> {
                    uploadProgress.setVisibility(View.GONE);
                    Toast.makeText(this, "Incident Submitted", Toast.LENGTH_SHORT).show();
                    finish();
                }));
    }

    // ================= FILE READ =================
    private byte[] readBytes(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int n;
        while ((n = in.read(data)) != -1) buffer.write(data, 0, n);
        in.close();
        return buffer.toByteArray();
    }
}