package com.example.hldsn;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    private final List<CommentModel> comments = new ArrayList<>();

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
        holder.commentAuthor.setText(comment.getAuthor());
        holder.commentBody.setText(comment.getBody());
        holder.commentTime.setText(comment.getTimeAgo());
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    public void updateList(List<CommentModel> newComments) {
        comments.clear();
        comments.addAll(newComments);
        notifyDataSetChanged();
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {
        final TextView commentAuthor;
        final TextView commentBody;
        final TextView commentTime;

        CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            commentAuthor = itemView.findViewById(R.id.commentAuthor);
            commentBody = itemView.findViewById(R.id.commentBody);
            commentTime = itemView.findViewById(R.id.commentTime);
        }
    }
}
