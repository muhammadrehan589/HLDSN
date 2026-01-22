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
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
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
import java.util.List;
import java.util.Map;

public class DisplayReportActivity extends AppCompatActivity implements OnIncidentReactionListener {

    private static final String TAG = "DisplayReportDebug";

    private RecyclerView communityRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ShimmerFrameLayout shimmerLayout;
    private TextView emptyStateText;
    private FloatingActionButton addReportBtn;

    private IncidentAdapter adapter;
    private final ArrayList<IncidentModel> allIncidents = new ArrayList<>();

    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    private boolean isFirstLoad = true;
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

                                    Long likes = doc.getLong("likes");
                                    Long dislikes = doc.getLong("dislikes");
                                    Long comments = doc.getLong("commentCount");

                                    incident.setLikes(likes != null ? likes : incident.getLikes());
                                    incident.setDislikes(dislikes != null ? dislikes : incident.getDislikes());
                                    incident.setCommentCount(comments != null ? comments : incident.getCommentCount());

                                    allIncidents.add(incident);
                                    attachRealTimeListener(incident);
                                }
                            } catch (Exception parseError) {
                                Log.w(TAG, "Failed to parse incident " + doc.getId(), parseError);
                            }
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

    private void attachRealTimeListener(@NonNull IncidentModel incident) {
        if (incident.getId() == null) return;
        String incidentId = incident.getId();

        ListenerRegistration existing = incidentListeners.remove(incidentId);
        if (existing != null) existing.remove();

        DocumentReference ref = firestore.collection("incidents").document(incidentId);
        ListenerRegistration listener = ref.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.w(TAG, "Realtime listen error for " + incidentId, error);
                return;
            }
            if (snapshot == null || !snapshot.exists()) return;

            try {
                Long newLikes = snapshot.getLong("likes");
                Long newDislikes = snapshot.getLong("dislikes");
                Long newComments = snapshot.getLong("commentCount");

                if (newLikes != null) incident.setLikes(newLikes);
                if (newDislikes != null) incident.setDislikes(newDislikes);
                if (newComments != null) incident.setCommentCount(newComments);

                int index = allIncidents.indexOf(incident);
                if (index >= 0) {
                    adapter.notifyItemChanged(index);
                }
            } catch (Exception ex) {
                Log.w(TAG, "Failed to update realtime counts for " + incidentId, ex);
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

    @Override
    public void onCommentsClicked(IncidentModel incident, int position) {
        showCommentsBottomSheet(incident);
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
                    Map<String, Object> data = new HashMap<>();
                    data.put("type", "like");
                    transaction.set(voteRef, data);
                    transaction.update(incidentRef, "likes", likes + 1);
                    transaction.update(incidentRef, "dislikes", dislikes - 1);
                } else {
                    Map<String, Object> data = new HashMap<>();
                    data.put("type", "like");
                    transaction.set(voteRef, data);
                    transaction.update(incidentRef, "likes", likes + 1);
                }
            } else {
                if ("dislike".equals(currentVote)) {
                    transaction.delete(voteRef);
                    transaction.update(incidentRef, "dislikes", dislikes - 1);
                } else if ("like".equals(currentVote)) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("type", "dislike");
                    transaction.set(voteRef, data);
                    transaction.update(incidentRef, "dislikes", dislikes + 1);
                    transaction.update(incidentRef, "likes", likes - 1);
                } else {
                    Map<String, Object> data = new HashMap<>();
                    data.put("type", "dislike");
                    transaction.set(voteRef, data);
                    transaction.update(incidentRef, "dislikes", dislikes + 1);
                }
            }
            return null;
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Vote transaction failed for " + incidentId + ": " + e.getMessage(), e);
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
        CommentAdapter commentAdapter = new CommentAdapter(new CommentAdapter.CommentActionListener() {
            @Override
            public void onLike(CommentModel comment) {
                showToast("Like coming soon");
            }

            @Override
            public void onDislike(CommentModel comment) {
                showToast("Dislike coming soon");
            }

            @Override
            public void onReply(CommentModel comment) {
                showToast("Reply coming soon");
            }
        });
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
        long now = System.currentTimeMillis();

        comments.add(new CommentModel(
            null,
            null,
            "Operations Desk",
            "Incident type: " + incident.getIncidentType() + " acknowledged. Dispatch alerted.",
            null,
            new java.util.Date(now - 2 * 60_000L)
        ));

        comments.add(new CommentModel(
            null,
            null,
            "Community Lead",
            "Verified location at " + incident.getLocation() + ". Crowd control volunteers en route.",
            null,
            new java.util.Date(now - 5 * 60_000L)
        ));

        comments.add(new CommentModel(
            null,
            null,
            "Logistics",
            "Water and blankets staged near the perimeter entrance.",
            null,
            new java.util.Date(now - 9 * 60_000L)
        ));

        comments.add(new CommentModel(
            null,
            null,
            "Medical",
            "EMS triage point set at the north exit. ETA 3 minutes.",
            null,
            new java.util.Date(now - 11 * 60_000L)
        ));

        comments.add(new CommentModel(
            null,
            null,
            "Safety",
            "Please keep a 50m radius clear for responders.",
            null,
            new java.util.Date(now - 15 * 60_000L)
        ));

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