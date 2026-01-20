package com.example.hldsn;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DisplayReportActivity extends AppCompatActivity implements OnIncidentReactionListener {

    private static final String TAG = "DisplayReportDebug";

    // Views
    private RecyclerView communityRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ShimmerFrameLayout shimmerLayout;
    private TextView emptyStateText;
    private FloatingActionButton addReportBtn;

    // Data
    private IncidentAdapter adapter;
    private final ArrayList<IncidentModel> allIncidents = new ArrayList<>();

    // Firebase
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    // Control shimmer/first load
    private boolean isFirstLoad = true;

    // Real-time listeners
    private final Map<String, ListenerRegistration> incidentListeners = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate started");
        setContentView(R.layout.activity_community_messages);

        try {
            initViews();
            initFirebase();
            setupRecyclerView();
            setupSwipeRefresh();
            initListeners();

            Log.d(TAG, "onCreate finished → loading all incidents");
            loadIncidents();
        } catch (Exception e) {
            Log.e(TAG, "CRASH in onCreate!", e);
            showToast("Startup error - please restart app");
        }
    }

    private void initViews() {
        try {
            communityRecyclerView = findViewById(R.id.communityRecyclerView);
            swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
            shimmerLayout = findViewById(R.id.shimmerLayout);
            emptyStateText = findViewById(R.id.emptyStateText);
            addReportBtn = findViewById(R.id.addCommunityPostFab);
        } catch (Exception e) {
            Log.e(TAG, "View init failed", e);
        }
    }

    private void initFirebase() {
        try {
            firestore = FirebaseFirestore.getInstance();
            auth = FirebaseAuth.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "Firebase init failed", e);
        }
    }

    private void setupRecyclerView() {
        try {
            adapter = new IncidentAdapter(this);
            if (communityRecyclerView != null) {
                communityRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                communityRecyclerView.setAdapter(adapter);
            }
        } catch (Exception e) {
            Log.e(TAG, "Recycler setup failed", e);
        }
    }

    private void setupSwipeRefresh() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                loadIncidents();
                swipeRefreshLayout.setRefreshing(false);
            });
        }
    }

    private void initListeners() {
        if (addReportBtn != null) {
            addReportBtn.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(this, ReportIncidentActivity.class));
                } catch (Exception e) {
                    showToast("Cannot open report screen");
                }
            });
        }
    }

    private void loadIncidents() {
        if (firestore == null) {
            showEmptyState("Database not ready");
            return;
        }

        firestore.collection("incidents")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    allIncidents.clear();
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot) {
                            try {
                                IncidentModel incident = doc.toObject(IncidentModel.class);
                                if (incident != null) {
                                    incident.setId(doc.getId());
                                    // Safe defaults for likes/dislikes
                                    if (incident.getLikes() ==0) incident.setLikes(0L);
                                    if (incident.getDislikes() ==0) incident.setDislikes(0L);
                                    allIncidents.add(incident);

                                    // Attach real-time listener
                                    attachRealTimeListener(incident);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    updateUIWithIncidents();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load incidents", e);
                    showEmptyState("Failed to load reports");
                    updateUIWithIncidents();
                });
    }

    private void attachRealTimeListener(IncidentModel incident) {
        if (incident.getId() == null) return;

        String incidentId = incident.getId();

        // Remove old listener if exists
        if (incidentListeners.containsKey(incidentId)) {
            incidentListeners.get(incidentId).remove();
        }

        DocumentReference ref = firestore.collection("incidents").document(incidentId);

        ListenerRegistration listener = ref.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.w(TAG, "Realtime listen error for " + incidentId, error);
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                try {
                    Long newLikes = snapshot.getLong("likes");
                    Long newDislikes = snapshot.getLong("dislikes");

                    if (newLikes != null) incident.setLikes(newLikes);
                    if (newDislikes != null) incident.setDislikes(newDislikes);

                    // Update UI if visible
                    int index = allIncidents.indexOf(incident);
                    if (index >= 0) {
                        adapter.notifyItemChanged(index);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to update realtime counts for " + incidentId, e);
                }
            }
        });

        incidentListeners.put(incidentId, listener);
    }

    private void updateUIWithIncidents() {
        if (adapter != null) {
            adapter.updateList(allIncidents);
        }

        if (isFirstLoad) {
            isFirstLoad = false;
            if (shimmerLayout != null) {
                shimmerLayout.stopShimmer();
                shimmerLayout.setVisibility(View.GONE);
            }
            if (communityRecyclerView != null) {
                communityRecyclerView.setVisibility(View.VISIBLE);
            }
        }

        if (allIncidents.isEmpty()) {
            showEmptyState("No reports yet");
        } else if (emptyStateText != null) {
            emptyStateText.setVisibility(View.GONE);
        }
    }

    // ──────────────────────────────────────────────
    //          LIKE / DISLIKE LOGIC
    // ──────────────────────────────────────────────

    @Override
    public void onLikeClicked(IncidentModel incident, int position) {
        handleVote(incident, position, "like");
    }

    @Override
    public void onDislikeClicked(IncidentModel incident, int position) {
        handleVote(incident, position, "dislike");
    }

    private void handleVote(IncidentModel incident, int position, String voteType) {
        if (incident == null || incident.getId() == null) {
            showToast("Invalid report");
            return;
        }

        FirebaseUser user = auth != null ? auth.getCurrentUser() : null;
        if (user == null) {
            showToast("Please login to vote");
            return;
        }

        String userId = user.getUid();
        String incidentId = incident.getId();

        DocumentReference incidentRef = firestore.collection("incidents").document(incidentId);
        DocumentReference voteRef = incidentRef.collection("votes").document(userId);

        // Optimistic UI update
        long oldLikes = incident.getLikes() !=0? incident.getLikes() : 0;
        long oldDislikes = incident.getDislikes() != 0 ? incident.getDislikes() : 0;

        if ("like".equals(voteType)) {
            if ("like".equals(incident.getUserVote())) {
                incident.setLikes(Math.max(0, oldLikes - 1));
                incident.setUserVote(null);
            } else if ("dislike".equals(incident.getUserVote())) {
                incident.setLikes(oldLikes + 1);
                incident.setDislikes(Math.max(0, oldDislikes - 1));
                incident.setUserVote("like");
            } else {
                incident.setLikes(oldLikes + 1);
                incident.setUserVote("like");
            }
        } else { // dislike
            if ("dislike".equals(incident.getUserVote())) {
                incident.setDislikes(Math.max(0, oldDislikes - 1));
                incident.setUserVote(null);
            } else if ("like".equals(incident.getUserVote())) {
                incident.setDislikes(oldDislikes + 1);
                incident.setLikes(Math.max(0, oldLikes - 1));
                incident.setUserVote("dislike");
            } else {
                incident.setDislikes(oldDislikes + 1);
                incident.setUserVote("dislike");
            }
        }

        adapter.notifyItemChanged(position);

        // Server transaction
        firestore.runTransaction(transaction -> {
            DocumentSnapshot voteSnap = transaction.get(voteRef);
            DocumentSnapshot incSnap = transaction.get(incidentRef);

            String currentVote = voteSnap.exists() ? voteSnap.getString("type") : null;
            long likes = incSnap.getLong("likes") != null ? incSnap.getLong("likes") : 0;
            long dislikes = incSnap.getLong("dislikes") != null ? incSnap.getLong("dislikes") : 0;

            if ("like".equals(voteType)) {
                if ("like".equals(currentVote)) {
                    transaction.delete(voteRef);
                    transaction.update(incidentRef, "likes", likes - 1);
                } else if ("dislike".equals(currentVote)) {
                    transaction.set(voteRef, Map.of("type", "like"));
                    transaction.update(incidentRef, "likes", likes + 1);
                    transaction.update(incidentRef, "dislikes", dislikes - 1);
                } else {
                    transaction.set(voteRef, Map.of("type", "like"));
                    transaction.update(incidentRef, "likes", likes + 1);
                }
            } else {
                if ("dislike".equals(currentVote)) {
                    transaction.delete(voteRef);
                    transaction.update(incidentRef, "dislikes", dislikes - 1);
                } else if ("like".equals(currentVote)) {
                    transaction.set(voteRef, Map.of("type", "dislike"));
                    transaction.update(incidentRef, "dislikes", dislikes + 1);
                    transaction.update(incidentRef, "likes", likes - 1);
                } else {
                    transaction.set(voteRef, Map.of("type", "dislike"));
                    transaction.update(incidentRef, "dislikes", dislikes + 1);
                }
            }
            return null;
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Vote transaction failed for " + incidentId, e);
            showToast("Vote failed - try again");
            loadIncidents(); // sync again
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (ListenerRegistration reg : incidentListeners.values()) {
            if (reg != null) reg.remove();
        }
        incidentListeners.clear();
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
        try {
            if (shimmerLayout != null) {
                shimmerLayout.stopShimmer();
                shimmerLayout.setVisibility(View.GONE);
            }
            if (communityRecyclerView != null) {
                communityRecyclerView.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.w(TAG, "hideShimmer failed", e);
        }
    }

    private void showEmptyState(String message) {
        Log.d(TAG, "showEmptyState: " + message);
        hideShimmerAndShowRecycler();
        if (emptyStateText != null) {
            emptyStateText.setText(message);
            emptyStateText.setVisibility(View.VISIBLE);
        }
    }

    private void showToast(String msg) {
        try {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
    }
}