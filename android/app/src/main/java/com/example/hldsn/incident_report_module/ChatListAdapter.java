package com.example.hldsn.incident_report_module;

import android.content.Context;
import android.graphics.Color;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.List;

public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ViewHolder>
        implements Filterable {

    public interface OnUserClickListener {
        void onUserClick(ChatUser user);
    }

    // Palette of avatar background colours
    private static final int[] AVATAR_COLORS = {
            0xFFF9C06D, 0xFFF4A1C4, 0xFFFAD5A6, 0xFFF07E7E,
            0xFFA0D8EF, 0xFFFDB77E, 0xFFE7B6D8, 0xFF8ECAE6
    };

    private final Context            context;
    private final OnUserClickListener listener;
    private List<ChatUser>           fullList;
    private List<ChatUser>           filteredList;

    public ChatListAdapter(Context context, List<ChatUser> users,
                           OnUserClickListener listener) {
        this.context      = context;
        this.listener     = listener;
        this.fullList     = new ArrayList<>(users);
        this.filteredList = new ArrayList<>(users);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_chat_user, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        ChatUser user = filteredList.get(position);

        // -- Avatar initial & colour --
        String name    = user.getName() != null ? user.getName() : "?";
        String initial = name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase();
        h.avatarInitial.setText(initial);
        int colorIndex = Math.abs(name.hashCode()) % AVATAR_COLORS.length;
        // Tint the frame background (avatarInitial itself has no background)
        if (h.avatarFrame.getBackground() != null) {
            h.avatarFrame.getBackground().mutate().setTint(AVATAR_COLORS[colorIndex]);
        } else {
            h.avatarFrame.setBackgroundColor(AVATAR_COLORS[colorIndex]);
        }

        // -- Name --
        h.userName.setText(name);

        // -- Last message --
        String last = user.getLastMessage();
        h.lastMessage.setText(last != null ? last : "");

        // -- Time --
        Timestamp ts = user.getLastMessageTime();
        if (ts != null) {
            long millis = ts.toDate().getTime();
            CharSequence rel = DateUtils.getRelativeTimeSpanString(
                    millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            h.messageTime.setText(rel);
        } else {
            h.messageTime.setText("");
        }

        // -- Unread badge --
        int unread = user.getUnreadCount();
        if (unread > 0) {
            h.unreadBadge.setVisibility(View.VISIBLE);
            h.unreadBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
        } else {
            h.unreadBadge.setVisibility(View.GONE);
        }

        // -- Click --
        h.itemView.setOnClickListener(v -> listener.onUserClick(user));
    }

    @Override
    public int getItemCount() { return filteredList.size(); }

    /** Replace the full dataset and refresh. */
    public void updateList(List<ChatUser> newList) {
        fullList     = new ArrayList<>(newList);
        filteredList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    // ── Filtering ──────────────────────────────────────────────────────────────

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                List<ChatUser> result = new ArrayList<>();
                if (constraint == null || constraint.length() == 0) {
                    result.addAll(fullList);
                } else {
                    String query = constraint.toString().toLowerCase().trim();
                    for (ChatUser u : fullList) {
                        if (u.getName() != null &&
                                u.getName().toLowerCase().contains(query)) {
                            result.add(u);
                        }
                    }
                }
                FilterResults fr = new FilterResults();
                fr.values = result;
                return fr;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                filteredList = (List<ChatUser>) results.values;
                notifyDataSetChanged();
            }
        };
    }

    // ── ViewHolder ─────────────────────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {
        final android.widget.FrameLayout avatarFrame;
        final TextView avatarInitial;
        final TextView userName;
        final TextView lastMessage;
        final TextView messageTime;
        final TextView unreadBadge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarFrame   = itemView.findViewById(R.id.avatarFrame);
            avatarInitial = itemView.findViewById(R.id.avatarInitial);
            userName      = itemView.findViewById(R.id.userName);
            lastMessage   = itemView.findViewById(R.id.lastMessage);
            messageTime   = itemView.findViewById(R.id.messageTime);
            unreadBadge   = itemView.findViewById(R.id.unreadBadge);
        }
    }
}
