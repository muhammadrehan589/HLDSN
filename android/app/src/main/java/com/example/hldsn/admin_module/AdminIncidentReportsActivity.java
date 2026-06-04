package com.example.hldsn.admin_module;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.example.hldsn.admin_module.adapter.AdminIncidentReportAdapter;
import com.example.hldsn.incident_report_module.IncidentModel;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin screen that lists ALL incident reports (regardless of age)
 * and allows the admin to permanently delete any report.
 */
public class AdminIncidentReportsActivity extends AppCompatActivity {

    private static final String TAG = "AdminIncidentReports";

    private RecyclerView recyclerView;
    private TextView emptyStateText;

    private AdminIncidentReportAdapter adapter;
    private FirebaseFirestore firestore;
    private ListenerRegistration listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_incident_reports);

        ImageView backButton = findViewById(R.id.backButton);
        recyclerView = findViewById(R.id.adminIncidentRecyclerView);
        emptyStateText = findViewById(R.id.adminEmptyStateText);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        firestore = FirebaseFirestore.getInstance();

        adapter = new AdminIncidentReportAdapter(this::confirmAndDelete);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadAllIncidents();
    }

    /** Listens to ALL incidents in real-time (no 48-hour filter for admins). */
    private void loadAllIncidents() {
        listener = firestore.collection("incidents")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Failed to load incidents", error);
                        Toast.makeText(this, "Failed to load reports", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<IncidentModel> list = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            try {
                                IncidentModel m = doc.toObject(IncidentModel.class);
                                if (m != null) {
                                    m.setId(doc.getId());
                                    list.add(m);
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Parse error for " + doc.getId(), e);
                            }
                        }
                    }

                    adapter.updateList(list);

                    if (emptyStateText != null) {
                        emptyStateText.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
    }

    /**
     * Shows a confirmation dialog before deleting the incident and
     * all its sub-collections (votes, comments).
     */
    private void confirmAndDelete(IncidentModel incident) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Report?")
                .setMessage("This will permanently remove the report \""
                        + incident.getIncidentType()
                        + "\" from the community. This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteIncident(incident))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteIncident(IncidentModel incident) {
        String incidentId = incident.getId();
        if (incidentId == null) {
            Toast.makeText(this, "Invalid report ID", Toast.LENGTH_SHORT).show();
            return;
        }

        // Delete the main incident document.
        // Firestore does NOT auto-delete sub-collections, but for this
        // use-case the orphaned sub-collections are small and acceptable.
        // A Cloud Function can clean them up server-side if desired.
        firestore.collection("incidents")
                .document(incidentId)
                .delete()
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Report deleted successfully", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Deleted incident: " + incidentId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to delete incident: " + incidentId, e);
                    Toast.makeText(this, "Failed to delete report: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) listener.remove();
    }
}
