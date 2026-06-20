package com.example.hldsn.ngo_module;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class NgoDonationListActivity extends AppCompatActivity {

    private RecyclerView rvDonations;
    private NgoDonationAdapter adapter;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration listenerRegistration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ngo_donation_list);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        ImageView backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        rvDonations = findViewById(R.id.rvDonations);
        rvDonations.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NgoDonationAdapter();
        rvDonations.setAdapter(adapter);

        loadDonationsForMyNgo();
    }

    private void loadDonationsForMyNgo() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String ngoId = doc.getString("ngoId");
                    if (ngoId == null || ngoId.trim().isEmpty()) {
                        Toast.makeText(this, "No NGO found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    listenerRegistration = NgoDonationStore.observeDonationsForNgo(db, ngoId, (snapshot, error) -> {
                        if (error != null) return;
                        List<NgoDonation> list = new ArrayList<>();
                        if (snapshot != null) {
                            for (DocumentSnapshot ds : snapshot.getDocuments()) {
                                NgoDonation d = ds.toObject(NgoDonation.class);
                                if (d != null) {
                                    d.setId(ds.getId());
                                    list.add(d);
                                }
                            }
                        }
                        adapter.setItems(list);
                    });
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Unable to read profile", Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (listenerRegistration != null) listenerRegistration.remove();
    }
}
