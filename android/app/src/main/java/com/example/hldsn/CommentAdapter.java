package com.example.hldsn;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    public interface CommentActionListener {
        void onLike(CommentModel comment);
        void onDislike(CommentModel comment);
        void onReply(CommentModel comment, String displayName);
    }

    private final List<CommentModel> comments = new ArrayList<>();
    private final CommentActionListener listener;
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    String displayName = null;


    // No need to keep Context as field anymore

    public CommentAdapter(CommentActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        CommentModel comment = comments.get(position);

        // Reset views (important when recycling)
        holder.commentAuthor.setText("Loading...");
        holder.commentProfileImage.setImageResource(R.drawable.ic_profile_avatar);

        String authorId = comment.getAuthorId();

        if (authorId == null || authorId.trim().isEmpty()) {
            holder.commentAuthor.setText("Anonymous");
        } else {
            loadUserProfile(authorId, holder);
        }

        holder.commentBody.setText(comment.getBody() != null ? comment.getBody() : "");
        holder.commentTime.setText(formatTime(comment.getCreatedAt()));
        holder.commentLikeCount.setText(String.valueOf(comment.getLikes()));
        holder.commentDislikeCount.setText(String.valueOf(comment.getDislikes()));

        // Indent replies
        if (comment.getParentId() != null) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
            params.leftMargin = 48;
            holder.itemView.setLayoutParams(params);
        } else {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
            params.leftMargin = 0;
            holder.itemView.setLayoutParams(params);
        }

        holder.commentLikeContainer.setOnClickListener(v -> {
            if (listener != null) listener.onLike(comment);
        });

        holder.commentDislikeContainer.setOnClickListener(v -> {
            if (listener != null) listener.onDislike(comment);
        });

        holder.commentReplyContainer.setOnClickListener(v -> {
            if (listener != null) listener.onReply(comment,displayName);
        });
    }

    /**
     * Loads name (name OR first+last) + profile image
     * Uses itemView context inside callback → safe against recycled views
     */
    private void loadUserProfile(String userId, CommentViewHolder holder) {
        firestore.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot doc) {
                        // Very important: check if view is still attached / alive
                        if (holder.itemView.getWindowToken() == null) {
                            // View recycled → skip Glide / UI update
                            return;
                        }

                        if (doc == null || !doc.exists()) {
                            holder.commentAuthor.setText("Anonymous");
                            return;
                        }

                        // ───── Name logic ─────

                        // Case 1: single "name" field
                        String singleName = doc.getString("name");
                        if (singleName != null && !singleName.trim().isEmpty()) {
                            displayName = singleName.trim();
                        }

                        // Case 2: firstName + lastName
                        if (displayName == null) {
                            String first = doc.getString("firstName");
                            String last  = doc.getString("lastName");

                            StringBuilder sb = new StringBuilder();
                            if (first != null && !first.trim().isEmpty()) {
                                sb.append(first.trim());
                            }
                            if (last != null && !last.trim().isEmpty()) {
                                if (sb.length() > 0) sb.append(" ");
                                sb.append(last.trim());
                            }
                            if (sb.length() > 0) {
                                displayName = sb.toString();
                            }
                        }

                        holder.commentAuthor.setText(
                                (displayName != null && !displayName.trim().isEmpty())
                                        ? displayName
                                        : "Anonymous"
                        );

                        // ───── Profile Image ─────
                        String profileUrl = doc.getString("profileImageUrl");

                        // No image field or empty → use placeholder
                        if (profileUrl == null || profileUrl.trim().isEmpty()) {
                            holder.commentProfileImage.setImageResource(R.drawable.ic_profile_avatar);
                            return;
                        }

                        // Safe Glide load with itemView context
                        Glide.with(holder.itemView.getContext())
                                .load(profileUrl)
                                .placeholder(R.drawable.ic_profile_avatar)
                                .error(R.drawable.ic_profile_avatar)
                                .transform(new CircleCrop())
                                .into(holder.commentProfileImage);
                    }
                })
                .addOnFailureListener(e -> {
                    // View might be recycled here too → check again
                    if (holder.itemView.getWindowToken() == null) {
                        return;
                    }
                    Log.e("CommentAdapter", "Failed to load user: " + userId, e);
                    holder.commentAuthor.setText("Anonymous");
                    holder.commentProfileImage.setImageResource(R.drawable.ic_profile_avatar);
                });
    }

    private String formatTime(Date date) {
        if (date == null) return "just now";
        SimpleDateFormat fmt = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
        return fmt.format(date);
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    public void updateList(List<CommentModel> newComments) {
        comments.clear();
        if (newComments != null) {
            comments.addAll(newComments);
        }
        notifyDataSetChanged();
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {
        final TextView commentAuthor;
        final TextView commentBody;
        final TextView commentTime;
        final TextView commentLikeCount;
        final TextView commentDislikeCount;
        final LinearLayout commentLikeContainer;
        final LinearLayout commentDislikeContainer;
        final LinearLayout commentReplyContainer;
        final ImageView commentLikeIcon;
        final ImageView commentDislikeIcon;
        final ImageView commentProfileImage;

        CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            commentAuthor       = itemView.findViewById(R.id.commentAuthor);
            commentBody         = itemView.findViewById(R.id.commentBody);
            commentTime         = itemView.findViewById(R.id.commentTime);
            commentLikeCount    = itemView.findViewById(R.id.commentLikeCount);
            commentDislikeCount = itemView.findViewById(R.id.commentDislikeCount);
            commentLikeContainer   = itemView.findViewById(R.id.commentLikeContainer);
            commentDislikeContainer= itemView.findViewById(R.id.commentDislikeContainer);
            commentReplyContainer  = itemView.findViewById(R.id.commentReplyContainer);
            commentLikeIcon     = itemView.findViewById(R.id.commentLikeIcon);
            commentDislikeIcon  = itemView.findViewById(R.id.commentDislikeIcon);
            commentProfileImage = itemView.findViewById(R.id.commentProfileImage);
        }
    }
}