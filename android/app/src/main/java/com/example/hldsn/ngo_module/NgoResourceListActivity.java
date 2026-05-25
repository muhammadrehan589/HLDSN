package com.example.hldsn.ngo_module;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class NgoResourceListActivity extends AppCompatActivity {

    private RecyclerView rvResources;
    private NgoResourceAdapter adapter;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration listenerRegistration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ngo_resource_list);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        rvResources = findViewById(R.id.rvResources);
        rvResources.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NgoResourceAdapter(resource -> {
            // open edit
            Intent i = new Intent(this, NgoResourceCreationActivity.class);
            i.putExtra("resourceId", resource.getId());
            startActivity(i);
        });
        rvResources.setAdapter(adapter);

        loadResourcesForMyNgo();
    }

    private void loadResourcesForMyNgo() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show(); return; }

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String ngoId = doc.getString("ngoId");
                    if (ngoId == null || ngoId.trim().isEmpty()) { Toast.makeText(this, "No NGO found", Toast.LENGTH_SHORT).show(); return; }

                    listenerRegistration = NgoResourceStore.observeResourcesForNgo(db, ngoId, (snapshot, error) -> {
                        if (error != null) return;
                        List<NgoResource> list = new ArrayList<>();
                        if (snapshot != null) {
                            for (com.google.firebase.firestore.DocumentSnapshot ds : snapshot.getDocuments()) {
                                NgoResource r = ds.toObject(NgoResource.class);
                                if (r != null) {
                                    r.setId(ds.getId());
                                    list.add(r);
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
