package com.example.hldsn;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.IncidentModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CommunityActivity extends AppCompatActivity {

    private static final String TAG = "CommunityActivity";
    private static final int PICK_FILE_REQUEST = 101;

    EditText typeField, locationField, descriptionField;
    Button submitReportButton, uploadButton, safeButton;

    Uri selectedMediaUri;
    boolean isSafe = false;

    FirebaseFirestore firestore;
    FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_community);

        initViews();
        initFirebase();
        initListeners();
    }

    private void initViews() {
        typeField = findViewById(R.id.typeField);
        locationField = findViewById(R.id.locationField);
        descriptionField = findViewById(R.id.descriptionField);

        uploadButton = findViewById(R.id.uploadButton);
        submitReportButton = findViewById(R.id.submitReportButton);
        safeButton = findViewById(R.id.safeButton);
    }

    private void initFirebase() {
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        if (auth.getCurrentUser() == null) {
            auth.signInAnonymously();
        }
    }

    private void initListeners() {

        uploadButton.setOnClickListener(v -> pickFile());

        safeButton.setOnClickListener(v -> {
            isSafe = true;
            Toast.makeText(this, "Marked Safe", Toast.LENGTH_SHORT).show();
        });

        submitReportButton.setOnClickListener(v -> submitIncident());
    }

    // ================= FILE PICK =================
    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_FILE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_FILE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            selectedMediaUri = data.getData();
            Toast.makeText(this, "Media selected", Toast.LENGTH_SHORT).show();
        }
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

        if (selectedMediaUri != null) {
            getImageKitAuthAndUpload(type, location, description);
        } else {
            saveIncidentInFirestore(null, type, location, description);
        }
    }

    // ================= IMAGEKIT AUTH =================
    private void getImageKitAuthAndUpload(String type, String location, String description) {

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://springbootapi-production-cda6.up.railway.app/api/imagekit/auth")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Auth API failed", e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    JSONObject json = new JSONObject(response.body().string());

                    uploadToImageKit(
                            json.getString("token"),
                            json.getString("signature"),
                            json.getLong("expire"),
                            type, location, description
                    );

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
            String base64File = Base64.encodeToString(bytes, Base64.NO_WRAP);

            RequestBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", base64File)
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

                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Upload failed", e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        String mediaUrl = json.getString("url");

                        saveIncidentInFirestore(mediaUrl, type, location, description);

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
    private void saveIncidentInFirestore(String mediaUrl,
                                         String type, String location, String description) {

        String userId = auth.getCurrentUser().getUid();

        IncidentModel incident = new IncidentModel(
                userId,
                type,
                location,
                description,
                isSafe,
                mediaUrl,
                System.currentTimeMillis()
        );

        firestore.collection("incidents")
                .add(incident)
                .addOnSuccessListener(doc -> runOnUiThread(() -> {
                    Toast.makeText(this, "Incident Submitted", Toast.LENGTH_SHORT).show();
                    finish();
                }))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Firestore save failed", e));
    }

    // ================= FILE READ =================
    private byte[] readBytes(Uri uri) throws Exception {

        InputStream inputStream = getContentResolver().openInputStream(uri);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        byte[] data = new byte[4096];
        int nRead;

        while ((nRead = inputStream.read(data)) != -1) {
            buffer.write(data, 0, nRead);
        }

        inputStream.close();
        return buffer.toByteArray();
    }
}
