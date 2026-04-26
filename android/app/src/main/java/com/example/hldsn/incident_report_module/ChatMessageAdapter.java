package com.example.hldsn.incident_report_module;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Date;
import java.util.List;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SENT     = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;
    private static final int VIEW_TYPE_SOS      = 3;   // full-width red alert card

    private final Context           context;
    private final List<ChatMessage> messages;
    private final String            currentUid;

    public ChatMessageAdapter(Context context, List<ChatMessage> messages) {
        this.context    = context;
        this.messages   = messages;
        FirebaseAuth auth = FirebaseAuth.getInstance();
        this.currentUid = auth.getCurrentUser() != null
                ? auth.getCurrentUser().getUid()
                : "";
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage msg = messages.get(position);
        if (msg.isSosMessage()) return VIEW_TYPE_SOS;
        return msg.getSenderId() != null && msg.getSenderId().equals(currentUid)
                ? VIEW_TYPE_SENT
                : VIEW_TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(context);
        if (viewType == VIEW_TYPE_SOS) {
            View v = inf.inflate(R.layout.item_message_sos, parent, false);
            return new SosViewHolder(v);
        } else if (viewType == VIEW_TYPE_SENT) {
            View v = inf.inflate(R.layout.item_message_sent, parent, false);
            return new SentViewHolder(v);
        } else {
            View v = inf.inflate(R.layout.item_message_received, parent, false);
            return new ReceivedViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        String timeStr = formatTime(msg.getTimestamp());

        if (holder instanceof SosViewHolder) {
            SosViewHolder h = (SosViewHolder) holder;
            // Strip the raw text — sender name is already in the header
            h.senderNameLabel.setText(msg.getSenderName() != null ? msg.getSenderName() : "");
            h.messageText.setText(msg.getText());
            h.timeText.setText(timeStr);
        } else if (holder instanceof SentViewHolder) {
            SentViewHolder h = (SentViewHolder) holder;
            h.messageText.setText(msg.getText());
            h.timeText.setText(timeStr);
        } else {
            ReceivedViewHolder h = (ReceivedViewHolder) holder;
            h.messageText.setText(msg.getText());
            h.timeText.setText(timeStr);

            String senderName = msg.getSenderName();
            if (senderName != null && !senderName.isEmpty()) {
                String initial = String.valueOf(senderName.charAt(0)).toUpperCase();
                h.senderInitial.setText(initial);
                h.senderNameLabel.setText(senderName);
            }
        }
    }

    @Override
    public int getItemCount() { return messages.size(); }

    private String formatTime(Timestamp ts) {
        if (ts == null) return "";
        Date date = ts.toDate();
        return DateFormat.format("hh:mm a", date).toString();
    }

    // ── ViewHolders ────────────────────────────────────────────────────────────

    static class SosViewHolder extends RecyclerView.ViewHolder {
        final TextView senderNameLabel;
        final TextView messageText;
        final TextView timeText;

        SosViewHolder(@NonNull View itemView) {
            super(itemView);
            senderNameLabel = itemView.findViewById(R.id.sosSenderName);
            messageText     = itemView.findViewById(R.id.sosMessageText);
            timeText        = itemView.findViewById(R.id.sosTimeText);
        }
    }

    static class SentViewHolder extends RecyclerView.ViewHolder {
        final TextView messageText;
        final TextView timeText;

        SentViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageTextSent);
            timeText    = itemView.findViewById(R.id.timeTextSent);
        }
    }

    static class ReceivedViewHolder extends RecyclerView.ViewHolder {
        final TextView senderInitial;
        final TextView senderNameLabel;
        final TextView messageText;
        final TextView timeText;

        ReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            senderInitial   = itemView.findViewById(R.id.senderInitial);
            senderNameLabel = itemView.findViewById(R.id.senderNameLabel);
            messageText     = itemView.findViewById(R.id.messageTextReceived);
            timeText        = itemView.findViewById(R.id.timeTextReceived);
        }
    }
}
