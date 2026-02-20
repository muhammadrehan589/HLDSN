package com.example.hldsn.incident_report_module;

import android.Manifest;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
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

import com.example.hldsn.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
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

public class ReportIncidentActivity extends AppCompatActivity {

    private static final String TAG = "ReportIncidentActivity";
    private static final int PERMISSION_REQUEST = 200;
    private static final int LOCATION_PERMISSION_REQUEST = 201;

    // Views
    private MaterialAutoCompleteTextView typeField;
    private EditText locationField, descriptionField;
    private Button uploadButton, safeButton, submitReportButton;
    private ImageView imagePreview;
    private ProgressBar uploadProgress;

    private Uri selectedMediaUri;
    private boolean isSafe = false;
    private int safeColor, unsafeColor;

    // Firebase
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private Geocoder geocoder;
    private Double currentLatitude = null;
    private Double currentLongitude = null;

    // Launchers
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_community); // your report form layout

        initViews();
        initFirebase();
        initLocationServices();
        initLaunchers();
        setupDropdown();
        initListeners();

        checkPermissions();
        requestCurrentLocation(); // Get lat/lng + fill address
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
        if (auth.getCurrentUser() == null) {
            auth.signInAnonymously().addOnSuccessListener(authResult -> Log.d(TAG, "Anonymous sign-in success"));
        }
    }

    private void initLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        geocoder = new Geocoder(this, Locale.getDefault());
    }

    private void setupDropdown() {
        String[] incidentTypes = getResources().getStringArray(R.array.incident_types);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                com.google.android.material.R.layout.m3_auto_complete_simple_item, incidentTypes);
        typeField.setAdapter(adapter);

        typeField.setOnItemClickListener((parent, view, position, id) -> {
            String selected = adapter.getItem(position);
            if (selected != null) {
                typeField.setText(selected, false);
            }
        });

        typeField.setOnClickListener(v -> typeField.showDropDown());
    }

    private void initLaunchers() {
        galleryLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                selectedMediaUri = result.getData().getData();
                showPreview();
            }
        });

        cameraLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                showPreview();
            }
        });
    }
    private void setupSafeButtonUI() {
        if (isSafe) {
            safeButton.setText("I am safe");
            safeButton.setBackgroundColor(safeColor);
        } else {
            safeButton.setText("I am not safe");
            safeButton.setBackgroundColor(unsafeColor);
        }
    }


    private void initListeners() {

        safeColor = getResources().getColor(R.color.safe_green);
        unsafeColor = getResources().getColor(R.color.lightred);

        // INITIAL UI STATE
        setupSafeButtonUI();

        uploadButton.setOnClickListener(v -> showMediaDialog());

        safeButton.setOnClickListener(v -> {
            isSafe = !isSafe; // toggle

            setupSafeButtonUI();

            Toast.makeText(
                    this,
                    isSafe ? "Marked as Safe" : "Marked as NOT Safe",
                    Toast.LENGTH_SHORT
            ).show();
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

    // ================= LOCATION =================
    private void requestCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocation();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        }
    }

    private void fetchLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        saveAndDisplayLocation(location);
                        return;
                    }

                    // Request fresh location when lastLocation is null
                    CancellationTokenSource token = new CancellationTokenSource();
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.getToken())
                            .addOnSuccessListener(this, this::saveAndDisplayLocation)
                            .addOnFailureListener(e -> {
                                Log.w(TAG, "getCurrentLocation failed", e);
                                Toast.makeText(this, "Could not detect location. Please try again. Or see if location is enabled.", Toast.LENGTH_SHORT).show();
                            });
                });
    }

    private void saveAndDisplayLocation(Location location) {
        if (location == null) {
            Log.w(TAG, "Location callback returned null; skipping update");
            Toast.makeText(this, "Could not detect location. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }
        currentLatitude = location.getLatitude();
        currentLongitude = location.getLongitude();

        // Reverse geocode to show readable address
        try {
            List<Address> addresses = geocoder.getFromLocation(currentLatitude, currentLongitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                String area = addr.getSubLocality() != null ? addr.getSubLocality() :
                        addr.getLocality() != null ? addr.getLocality() :
                                addr.getAdminArea() != null ? addr.getAdminArea() : "Unknown Area";

                String fullAddress = area + (addr.getCountryName() != null ? ", " + addr.getCountryName() : "");
                locationField.setText(fullAddress);
            } else {
                locationField.setText("Location detected (no address)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Geocoding failed", e);
            locationField.setText("Location detected");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchLocation();
        } else if (requestCode == LOCATION_PERMISSION_REQUEST) {
            Toast.makeText(this, "Location permission denied. You can type manually.", Toast.LENGTH_LONG).show();
        }
    }

    // ================= MEDIA =================
    private void showMediaDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Select Media Source")
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
        values.put(MediaStore.Images.Media.TITLE, "Incident Photo");
        selectedMediaUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
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
        String locationText = locationField.getText().toString().trim();
        String description = descriptionField.getText().toString().trim();

        if (type.isEmpty() || locationText.isEmpty()) {
            Toast.makeText(this, "Incident type and location are required", Toast.LENGTH_SHORT).show();
            return;
        }

        uploadProgress.setVisibility(View.VISIBLE);

        if (selectedMediaUri != null) {
            uploadMediaAndSave(type, locationText, description);
        } else {
            saveToFirestore(null, type, locationText, description);
        }
    }

    private void uploadMediaAndSave(String type, String locationText, String description) {
        Request request = new Request.Builder()
                .url("https://save-image.up.railway.app/api/imagekit/auth")
                .build();

        new OkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                runOnUiThread(() -> {
                    uploadProgress.setVisibility(View.GONE);
                    Toast.makeText(ReportIncidentActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws java.io.IOException {
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    String token = json.getString("token");
                    String signature = json.getString("signature");
                    long expire = json.getLong("expire");

                    uploadToImageKit(token, signature, expire, type, locationText, description);
                } catch (Exception e) {
                    Log.e(TAG, "Auth parse error", e);
                    runOnUiThread(() -> uploadProgress.setVisibility(View.GONE));
                }
            }
        });
    }

    private void uploadToImageKit(String token, String signature, long expire,
                                  String type, String locationText, String description) {
        try {
            byte[] bytes = readBytes(selectedMediaUri);
            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);

            RequestBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", base64)
                    .addFormDataPart("fileName", "incident_" + System.currentTimeMillis() + ".jpg")
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
                @Override
                public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                    runOnUiThread(() -> {
                        uploadProgress.setVisibility(View.GONE);
                        Toast.makeText(ReportIncidentActivity.this, "Upload failed", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws java.io.IOException {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        String mediaUrl = json.getString("url");
                        saveToFirestore(mediaUrl, type, locationText, description);
                    } catch (Exception e) {
                        runOnUiThread(() -> uploadProgress.setVisibility(View.GONE));
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "File read error", e);
            runOnUiThread(() -> uploadProgress.setVisibility(View.GONE));
        }
    }

    private void saveToFirestore(String mediaUrl, String type, String locationText, String description) {

        IncidentModel incident = new IncidentModel(
                auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "anonymous",
                type,
                locationText,
                description,
                isSafe,
                mediaUrl,
                currentLatitude,     // may be null
                currentLongitude
        );

        firestore.collection("incidents")
                .add(incident)
                .addOnSuccessListener(documentReference -> {

                    // 🔥 SET SERVER TIMESTAMP FOR TTL
                    documentReference.update("createdAt", FieldValue.serverTimestamp());

                    runOnUiThread(() -> {
                        uploadProgress.setVisibility(View.GONE);
                        Toast.makeText(this, "Incident reported successfully!", Toast.LENGTH_LONG).show();
                        finish();
                    });
                })
                .addOnFailureListener(e -> runOnUiThread(() -> {
                    uploadProgress.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to save report", Toast.LENGTH_SHORT).show();
                }));
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
}