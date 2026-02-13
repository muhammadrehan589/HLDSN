package com.example.hldsn.login_module;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.example.hldsn.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class UserProfileActivity extends AppCompatActivity {
private static String TAG = "UserProfileActivity";

    // Views
    private TextView nameField, addressField, phoneField;
    private TextView ageField, bloodField, heightField, weightField;
    private TextView allergyField1, allergyField2, allergyField3;
    private TextView injuryField1, injuryField2, injuryField3;
    TextView contactField1, contactField2, contactField3;
    private ImageView profileImage;
    Button editProfile ;


    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_profile);

        // EdgeToEdge padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.header), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }


        // Initialize views
        initViews();

        // Fetch and display user data
        fetchUserData();
    }



    private void initViews() {
        profileImage = findViewById(R.id.profileImage);

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

       editProfile = findViewById(R.id.editprofile);

       contactField1=findViewById(R.id.contactField1);
       contactField2=findViewById(R.id.contactField2);
       contactField3=findViewById(R.id.contactField3);

        editProfile.setOnClickListener(v->{
            Intent intent=new Intent(this, SaveUserProfileActivity.class);
            startActivity(intent);
            finish();


        });

    }


    private void fetchUserData() {
        String uid = auth.getCurrentUser().getUid();

        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Basic info
                    setTextSafe(nameField, nonEmpty(documentSnapshot.getString("name")));
                    setTextSafe(addressField, nonEmpty(documentSnapshot.getString("address")));
                    setTextSafe(phoneField, nonEmpty(documentSnapshot.getString("phone")));

                    // Stats
                    setTextSafe(ageField, nonEmpty(documentSnapshot.getString("age")));
                    setTextSafe(bloodField, nonEmpty(documentSnapshot.getString("bloodGroup")));
                    setTextSafe(heightField, nonEmpty(documentSnapshot.getString("height")));
                    setTextSafe(weightField, nonEmpty(documentSnapshot.getString("weight")));

                    // Allergies
                    List<String> allergies = (List<String>) documentSnapshot.get("allergies");
                    setTextSafe(allergyField1, getListItemOrNone(allergies, 0));
                    setTextSafe(allergyField2, getListItemOrNone(allergies, 1));
                    setTextSafe(allergyField3, getListItemOrNone(allergies, 2));

                    // Injuries
                    List<String> injuries = (List<String>) documentSnapshot.get("injuries");
                    setTextSafe(injuryField1, getListItemOrNone(injuries, 0));
                    setTextSafe(injuryField2, getListItemOrNone(injuries, 1));
                    setTextSafe(injuryField3, getListItemOrNone(injuries, 2));

                    List<String> contacts = (List<String>) documentSnapshot.get("contacts");
                    setTextSafe(contactField1, getListItemOrNone(contacts, 0));
                    setTextSafe(contactField2, getListItemOrNone(contacts, 1));
                    setTextSafe(contactField3, getListItemOrNone(contacts, 2));

                    // Profile image
                    String profileUrl = documentSnapshot.getString("profileImageUrl");
                    Log.d("UserProfileActivity", "Profile URL: "+profileUrl);
                    if (profileUrl != null && !profileUrl.isEmpty()) {

                        Glide.with(this)
                                .load(profileUrl)

                                .placeholder(R.drawable.image_3)
                                .error(R.drawable.image_3)
                                .circleCrop()
                                .into(profileImage);
                    } else {
                        profileImage.setImageResource(R.drawable.image_3);
                    }

                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error fetching user data", Toast.LENGTH_SHORT).show());
    }

    // Helpers
    private String nonEmpty(String value) {
        return (value != null && !value.trim().isEmpty()) ? value : "None";
    }

    private void setTextSafe(TextView editText, String text) {
        if (editText != null) editText.setText(text != null ? text : "None");
    }

    private String getListItemOrNone(List<String> list, int index) {
        return (list != null && list.size() > index && list.get(index) != null && !list.get(index).trim().isEmpty())
                ? list.get(index)
                : "None";
    }
}
