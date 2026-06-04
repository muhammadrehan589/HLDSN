package com.example.hldsn.incident_report_module;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.hldsn.R;
import com.example.hldsn.incident_report_module.ReportIncidentActivity;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
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
    private ImageView backButton;
    private ImageView communityBell;

    private IncidentAdapter adapter;
    private final ArrayList<IncidentModel> allIncidents = new ArrayList<>();

    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    private boolean isFirstLoad = true;
    private final Map<String, ListenerRegistration> incidentListeners = new HashMap<>();
    private ListenerRegistration commentsListener;
    /** Persistent real-time listener for the incidents collection (offline-capable). */
    private ListenerRegistration incidentsCollectionListener;
    /** Prevents the "showing cached data" toast from repeating within one session. */
    private boolean hasShownCacheBanner = false;

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
            backButton = findViewById(R.id.backButton);
            communityBell = findViewById(R.id.communityBell);
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
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        if (communityBell != null) {
            communityBell.setOnClickListener(v -> {
                startActivity(new Intent(this, ReportIncidentActivity.class));
            });
        }

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

        // Re-registering replaces any previous listener (e.g. on swipe-refresh).
        if (incidentsCollectionListener != null) {
            incidentsCollectionListener.remove();
        }

        // Only show incidents created within the last 48 hours.
        java.util.Date cutoff48h = new java.util.Date(System.currentTimeMillis() - 48L * 60 * 60 * 1000);

        incidentsCollectionListener = firestore.collection("incidents")
                .whereGreaterThan("createdAt", cutoff48h)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);

                    if (error != null) {
                        Log.e(TAG, "Failed to load incidents", error);
                        showEmptyState("Failed to load reports");
                        updateUIWithIncidents();
                        return;
                    }

                    allIncidents.clear();
                    // The collection listener replaces per-document listeners.
                    removeAllIncidentListeners();

                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot) {
                            try {
                                IncidentModel incident = doc.toObject(IncidentModel.class);
                                if (incident != null) {
                                    incident.setId(doc.getId());

                                    Long likes    = doc.getLong("likes");
                                    Long dislikes = doc.getLong("dislikes");
                                    Long comments = doc.getLong("commentCount");

                                    incident.setLikes(likes        != null ? likes        : incident.getLikes());
                                    incident.setDislikes(dislikes  != null ? dislikes     : incident.getDislikes());
                                    incident.setCommentCount(comments != null ? comments  : incident.getCommentCount());

                                    allIncidents.add(incident);
                                }
                            } catch (Exception parseError) {
                                Log.w(TAG, "Failed to parse incident " + doc.getId(), parseError);
                            }
                        }
                    }

                    // Offline: data served from local cache.
                    boolean fromCache = querySnapshot != null
                            && querySnapshot.getMetadata().isFromCache();
                    if (fromCache && !hasShownCacheBanner) {
                        hasShownCacheBanner = true;
                        showToast("You're offline — showing cached reports");
                    } else if (!fromCache) {
                        hasShownCacheBanner = false; // reset for the next offline session
                    }

                    updateUIWithIncidents();
                });
    }

    /** Cancel every per-document real-time listener. */
    private void removeAllIncidentListeners() {
        for (ListenerRegistration reg : incidentListeners.values()) {
            if (reg != null) reg.remove();
        }
        incidentListeners.clear();
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
            Long rawLikes    = incSnap.getLong("likes");
            Long rawDislikes = incSnap.getLong("dislikes");
            long likes    = rawLikes    != null ? rawLikes    : 0;
            long dislikes = rawDislikes != null ? rawDislikes : 0;

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
        if (incidentsCollectionListener != null) incidentsCollectionListener.remove();
        removeAllIncidentListeners();
        if (commentsListener != null) commentsListener.remove();
    }

    private void showCommentsBottomSheet(IncidentModel incident) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_comments, null);

        TextView title = sheetView.findViewById(R.id.commentsTitle);
        TextView subtitle = sheetView.findViewById(R.id.commentsSubtitle);
        TextView commentsCount = sheetView.findViewById(R.id.commentsCount);
        RecyclerView commentsRecyclerView = sheetView.findViewById(R.id.commentsRecyclerView);
        EditText commentInput = sheetView.findViewById(R.id.commentInput);
        ImageButton sendButton = sheetView.findViewById(R.id.commentSendButton);
        View closeSheet = sheetView.findViewById(R.id.closeSheet);


        title.setText("Comments");
        subtitle.setText("Discussion on " + incident.getIncidentType());
        if (commentsCount != null) {
            long initialCount = incident.getCommentCount();
            commentsCount.setText(initialCount + (initialCount == 1 ? " comment" : " comments"));
        }

        commentsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        final CommentAdapter[] adapterHolder = new CommentAdapter[1];
        CommentAdapter commentAdapter = new CommentAdapter(new CommentAdapter.CommentActionListener() {
            @Override
            public void onLike(CommentModel comment) {
                handleCommentVote(incident.getId(), comment, "like", adapterHolder[0]);
            }

            @Override
            public void onDislike(CommentModel comment) {
                handleCommentVote(incident.getId(), comment, "dislike", adapterHolder[0]);
            }

            @Override
            public void onReply(CommentModel comment,String name) {
                if (commentInput != null) {
                    commentInput.requestFocus();
                    commentInput.setText("@" +name +"");
                    commentInput.setSelection(commentInput.getText().length());
                }
            }
        });
        adapterHolder[0] = commentAdapter;
        commentsRecyclerView.setAdapter(commentAdapter);
        attachCommentsListener(incident.getId(), commentAdapter, commentsCount);

        sendButton.setOnClickListener(v -> {
            FirebaseUser user = auth != null ? auth.getCurrentUser() : null;
            if (user == null) {
                showToast("Please login to comment");
                return;
            }

            String body = commentInput.getText() != null ? commentInput.getText().toString().trim() : "";
            if (body.isEmpty()) {
                showToast("Comment cannot be empty");
                return;
            }

            DocumentReference incidentRef = firestore.collection("incidents").document(incident.getId());
            incidentRef.collection("comments")
                    .add(buildCommentPayload(user, body))
                    .addOnSuccessListener(ref -> incidentRef.update("commentCount", FieldValue.increment(1)))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to add comment", e));

            commentInput.setText("");
        });

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

        dialog.setOnDismissListener(d -> {
            if (commentsListener != null) {
                commentsListener.remove();
                commentsListener = null;
            }
        });

        dialog.show();
    }

    private void handleCommentVote(String incidentId, CommentModel comment, String voteType, CommentAdapter adapter) {
        if (firestore == null || incidentId == null || comment == null || comment.getId() == null) {
            showToast("Invalid comment");
            return;
        }

        FirebaseUser user = auth != null ? auth.getCurrentUser() : null;
        if (user == null) {
            showToast("Please login to vote");
            return;
        }

        String userId = user.getUid();
        DocumentReference commentRef = firestore.collection("incidents")
                .document(incidentId)
                .collection("comments")
                .document(comment.getId());
        DocumentReference voteRef = commentRef.collection("votes").document(userId);

        long oldLikes = comment.getLikes();
        long oldDislikes = comment.getDislikes();

        if ("like".equals(voteType)) {
            if ("like".equals(comment.getUserVote())) {
                comment.setLikes(Math.max(0, oldLikes - 1));
                comment.setUserVote(null);
            } else if ("dislike".equals(comment.getUserVote())) {
                comment.setLikes(oldLikes + 1);
                comment.setDislikes(Math.max(0, oldDislikes - 1));
                comment.setUserVote("like");
            } else {
                comment.setLikes(oldLikes + 1);
                comment.setUserVote("like");
            }
        } else {
            if ("dislike".equals(comment.getUserVote())) {
                comment.setDislikes(Math.max(0, oldDislikes - 1));
                comment.setUserVote(null);
            } else if ("like".equals(comment.getUserVote())) {
                comment.setDislikes(oldDislikes + 1);
                comment.setLikes(Math.max(0, oldLikes - 1));
                comment.setUserVote("dislike");
            } else {
                comment.setDislikes(oldDislikes + 1);
                comment.setUserVote("dislike");
            }
        }

        adapter.notifyDataSetChanged();

        firestore.runTransaction(transaction -> {
            DocumentSnapshot voteSnap    = transaction.get(voteRef);
            DocumentSnapshot commentSnap = transaction.get(commentRef);

            String currentVote = voteSnap.exists() ? voteSnap.getString("type") : null;
            Long rawCLikes    = commentSnap.getLong("likes");
            Long rawCDislikes = commentSnap.getLong("dislikes");
            long likes    = rawCLikes    != null ? rawCLikes    : 0;
            long dislikes = rawCDislikes != null ? rawCDislikes : 0;

            if ("like".equals(voteType)) {
                if ("like".equals(currentVote)) {
                    transaction.delete(voteRef);
                    transaction.update(commentRef, "likes", likes - 1);
                } else if ("dislike".equals(currentVote)) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("type", "like");
                    transaction.set(voteRef, data);
                    transaction.update(commentRef, "likes", likes + 1);
                    transaction.update(commentRef, "dislikes", dislikes - 1);
                } else {
                    Map<String, Object> data = new HashMap<>();
                    data.put("type", "like");
                    transaction.set(voteRef, data);
                    transaction.update(commentRef, "likes", likes + 1);
                }
            } else {
                if ("dislike".equals(currentVote)) {
                    transaction.delete(voteRef);
                    transaction.update(commentRef, "dislikes", dislikes - 1);
                } else if ("like".equals(currentVote)) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("type", "dislike");
                    transaction.set(voteRef, data);
                    transaction.update(commentRef, "dislikes", dislikes + 1);
                    transaction.update(commentRef, "likes", likes - 1);
                } else {
                    Map<String, Object> data = new HashMap<>();
                    data.put("type", "dislike");
                    transaction.set(voteRef, data);
                    transaction.update(commentRef, "dislikes", dislikes + 1);
                }
            }
            return null;
        }).addOnFailureListener(e -> Log.e(TAG, "Comment vote failed for " + comment.getId(), e));
    }



    private Map<String, Object> buildCommentPayload(FirebaseUser user, String body) {
        Map<String, Object> data = new HashMap<>();
        data.put("authorId", user.getUid());
        data.put("authorName", user.getDisplayName() != null ? user.getDisplayName() : "User");
        data.put("body", body);
        data.put("parentId", null);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("likes", 0L);
        data.put("dislikes", 0L);
        return data;
    }

    private void attachCommentsListener(String incidentId, CommentAdapter adapter, TextView commentsCount) {
        if (firestore == null) return;
        if (commentsListener != null) commentsListener.remove();

        commentsListener = firestore.collection("incidents")
                .document(incidentId)
                .collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        Log.w(TAG, "Comments listen failed", error);
                        return;
                    }
                    if (snap == null) return;

                    List<CommentModel> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        try {
                            CommentModel c = doc.toObject(CommentModel.class);
                            if (c != null) {
                                c.setId(doc.getId());
                                // User vote is not stored in comment doc; clear to avoid stale state
                                c.setUserVote(null);
                                list.add(c);
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to parse comment " + doc.getId(), e);
                        }
                    }

                    adapter.updateList(list);
                    if (commentsCount != null) {
                        commentsCount.setText(list.size() + (list.size() == 1 ? " comment" : " comments"));
                    }
                });
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