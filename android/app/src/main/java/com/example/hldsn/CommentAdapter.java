package com.example.hldsn;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    public interface CommentActionListener {
        void onLike(CommentModel comment);
        void onDislike(CommentModel comment);
        void onReply(CommentModel comment);
    }

    private final List<CommentModel> comments = new ArrayList<>();
    private final CommentActionListener listener;

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

        holder.commentAuthor.setText(comment.getAuthorName() != null ? comment.getAuthorName() : "User");
        holder.commentBody.setText(comment.getBody());
        holder.commentTime.setText(formatTime(comment.getCreatedAt()));
        holder.commentLikeCount.setText(String.valueOf(comment.getLikes()));
        holder.commentDislikeCount.setText(String.valueOf(comment.getDislikes()));

        // Indent replies
        if (comment.getParentId() != null) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
            params.leftMargin = 48;
            holder.itemView.setLayoutParams(params);
        }

        holder.commentLikeContainer.setOnClickListener(v -> {
            if (listener != null) listener.onLike(comment);
        });

        holder.commentDislikeContainer.setOnClickListener(v -> {
            if (listener != null) listener.onDislike(comment);
        });

        holder.commentReplyContainer.setOnClickListener(v -> {
            if (listener != null) listener.onReply(comment);
        });
    }

    private String formatTime(Date date) {
        if (date == null) return "now";
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

        CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            commentAuthor = itemView.findViewById(R.id.commentAuthor);
            commentBody = itemView.findViewById(R.id.commentBody);
            commentTime = itemView.findViewById(R.id.commentTime);
            commentLikeCount = itemView.findViewById(R.id.commentLikeCount);
            commentDislikeCount = itemView.findViewById(R.id.commentDislikeCount);
            commentLikeContainer = itemView.findViewById(R.id.commentLikeContainer);
            commentDislikeContainer = itemView.findViewById(R.id.commentDislikeContainer);
            commentReplyContainer = itemView.findViewById(R.id.commentReplyContainer);
            commentLikeIcon = itemView.findViewById(R.id.commentLikeIcon);
            commentDislikeIcon = itemView.findViewById(R.id.commentDislikeIcon);
        }
    }
}
