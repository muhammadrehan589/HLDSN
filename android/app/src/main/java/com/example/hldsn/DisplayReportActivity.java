package com.example.hldsn;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class DisplayReportActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private static final double MAX_DISTANCE_KM = 10.0;

    // Views
    private RecyclerView communityRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ShimmerFrameLayout shimmerLayout;
    private TextView emptyStateText;
    private FloatingActionButton addReportBtn;

    // Adapter & Data
    private IncidentAdapter adapter;
    private ArrayList<IncidentModel> allIncidents = new ArrayList<>();
    private ArrayList<IncidentModel> nearbyIncidents = new ArrayList<>();

    // Firebase
    private FirebaseFirestore firestore;

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private Location currentUserLocation;
    private boolean isFirstLoad = true;
    private boolean locationPermissionGranted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_community_messages);

        initViews();
        initFirebase();
        initLocationClient();
        setupRecyclerView();
        setupSwipeRefresh();
        initListeners();

        requestLocationPermissionAndLoad();
    }

    private void initViews() {
        communityRecyclerView = findViewById(R.id.communityRecyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        shimmerLayout = findViewById(R.id.shimmerLayout);
        addReportBtn = findViewById(R.id.addCommunityPostFab);

        // Optional: Add a TextView in XML for empty state
        emptyStateText = findViewById(R.id.emptyStateText); // Add this in XML if you want
        if (emptyStateText == null) emptyStateText = new TextView(this); // fallback

        findViewById(R.id.backIcon).setOnClickListener(v -> finish());
    }

    private void initFirebase() {
        firestore = FirebaseFirestore.getInstance();
    }

    private void initLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    private void setupRecyclerView() {
        adapter = new IncidentAdapter(this::showCommentsBottomSheet);
        communityRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        communityRecyclerView.setAdapter(adapter);
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(this::loadIncidents);

        swipeRefreshLayout.setColorSchemeColors(
                getColor(R.color.safe_green),
                getColor(android.R.color.holo_red_light),
                getColor(android.R.color.holo_orange_light)
        );
    }

    private void initListeners() {
        addReportBtn.setOnClickListener(v -> {
            startActivity(new Intent(this, ReportIncidentActivity.class));
        });
    }

    // ================= LOCATION & PERMISSION =================
    private void requestLocationPermissionAndLoad() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true;
            getCurrentLocationAndLoadIncidents();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        }
    }

    private void getCurrentLocationAndLoadIncidents() {
        if (!locationPermissionGranted) {
            loadAllIncidentsWithoutFilter(); // Fallback: show all if no permission
            return;
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, new CancellationTokenSource().getToken())
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentUserLocation = location;
                        loadIncidents(); // Now filter by distance
                    } else {
                        // Fallback if location is null
                        Toast.makeText(this, "Getting your location...", Toast.LENGTH_SHORT).show();
                        loadIncidents(); // Will show all or prompt
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Location unavailable. Showing all incidents.", Toast.LENGTH_SHORT).show();
                    loadIncidents(); // Fallback
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationPermissionGranted = true;
                getCurrentLocationAndLoadIncidents();
            } else {
                Toast.makeText(this, "Location permission denied. Showing all incidents.", Toast.LENGTH_LONG).show();
                loadAllIncidentsWithoutFilter();
            }
        }
    }

    // ================= LOAD INCIDENTS WITH 5KM FILTER =================
    private void loadIncidents() {
        if (isFirstLoad) {
            shimmerLayout.setVisibility(View.VISIBLE);
            shimmerLayout.startShimmer();
            communityRecyclerView.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.GONE);
        }

        swipeRefreshLayout.setRefreshing(true);

        firestore.collection("incidents")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    swipeRefreshLayout.setRefreshing(false);

                    if (error != null) {
                        Toast.makeText(this, "Error loading incidents", Toast.LENGTH_SHORT).show();
                        hideShimmerAndShowRecycler();
                        return;
                    }

                    if (snapshots == null || snapshots.isEmpty()) {
                        showEmptyState("No incidents reported yet.");
                        return;
                    }

                    allIncidents.clear();
                    for (var doc : snapshots.getDocuments()) {
                        IncidentModel incident = doc.toObject(IncidentModel.class);
                        if (incident != null && incident.getReporterLat() != null && incident.getReporterLng() != null) {
                            allIncidents.add(incident);
                        }
                    }

                    filterNearbyIncidents();
                });
    }

    private void filterNearbyIncidents() {
        nearbyIncidents.clear();

        if (currentUserLocation == null || !locationPermissionGranted) {
            // Show all if no location
            nearbyIncidents.addAll(allIncidents);
            updateUIWithIncidents();
            return;
        }

        for (IncidentModel incident : allIncidents) {
            Location incidentLocation = new Location("");
            incidentLocation.setLatitude(incident.getReporterLat());
            incidentLocation.setLongitude(incident.getReporterLng());

            float distanceInMeters = currentUserLocation.distanceTo(incidentLocation);
            float distanceInKm = distanceInMeters / 1000f;

            if (distanceInKm <= MAX_DISTANCE_KM) {
                nearbyIncidents.add(incident);
            }
        }

        updateUIWithIncidents();
    }

    private void updateUIWithIncidents() {
        adapter.updateList(nearbyIncidents);

        if (isFirstLoad) {
            isFirstLoad = false;
            hideShimmerAndShowRecycler();
        }

        if (nearbyIncidents.isEmpty()) {
            if (currentUserLocation != null) {
                showEmptyState("No incidents within 5 km of your location.");
            } else {
                showEmptyState("No nearby incidents found.");
            }
        } else {
            emptyStateText.setVisibility(View.GONE);
        }
    }

    private void loadAllIncidentsWithoutFilter() {
        locationPermissionGranted = false;
        currentUserLocation = null;
        loadIncidents(); // Will skip filtering
    }

    private void showCommentsBottomSheet(IncidentModel incident) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_comments, null);

        TextView title = sheetView.findViewById(R.id.commentsTitle);
        TextView subtitle = sheetView.findViewById(R.id.commentsSubtitle);
        RecyclerView commentsRecyclerView = sheetView.findViewById(R.id.commentsRecyclerView);
        View closeSheet = sheetView.findViewById(R.id.closeSheet);

        title.setText("Comments");
        subtitle.setText("Discussion on " + incident.getIncidentType());

        commentsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        CommentAdapter commentAdapter = new CommentAdapter();
        commentsRecyclerView.setAdapter(commentAdapter);
        commentAdapter.updateList(buildPlaceholderComments(incident));

        closeSheet.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(sheetView);
        dialog.setOnShowListener(dialogInterface -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                int halfHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.5f);
                behavior.setPeekHeight(halfHeight, true);
                behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        dialog.show();
    }

    private List<CommentModel> buildPlaceholderComments(IncidentModel incident) {
        List<CommentModel> comments = new ArrayList<>();
        comments.add(new CommentModel("Operations Desk", "Incident type: " + incident.getIncidentType() + " acknowledged. Dispatch alerted.", "2m ago"));
        comments.add(new CommentModel("Community Lead", "Verified location at " + incident.getLocation() + ". Crowd control volunteers en route.", "5m ago"));
        comments.add(new CommentModel("Logistics", "Water and blankets staged near the perimeter entrance.", "9m ago"));
        comments.add(new CommentModel("Medical", "EMS triage point set at the north exit. ETA 3 minutes.", "11m ago"));
        comments.add(new CommentModel("Safety", "Please keep a 50m radius clear for responders.", "15m ago"));
        return comments;
    }

    private void hideShimmerAndShowRecycler() {
        shimmerLayout.stopShimmer();
        shimmerLayout.setVisibility(View.GONE);
        communityRecyclerView.setVisibility(View.VISIBLE);
    }

    private void showEmptyState(String message) {
        hideShimmerAndShowRecycler();
        emptyStateText.setText(message);
        emptyStateText.setVisibility(View.VISIBLE);
    }
}