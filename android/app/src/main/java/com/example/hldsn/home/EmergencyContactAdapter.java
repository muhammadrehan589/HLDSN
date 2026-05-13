package com.example.hldsn.home;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class EmergencyContactAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_CONTACT = 1;

    public abstract static class DisplayItem {
        abstract int getType();
    }

    public static final class HeaderItem extends DisplayItem {
        private final String title;
        private final String subtitle;

        public HeaderItem(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }

        public String getTitle() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        @Override
        int getType() {
            return TYPE_HEADER;
        }
    }

    public static final class ContactItem extends DisplayItem {
        private final EmergencyContact contact;

        public ContactItem(EmergencyContact contact) {
            this.contact = contact;
        }

        public EmergencyContact getContact() {
            return contact;
        }

        @Override
        int getType() {
            return TYPE_CONTACT;
        }
    }

    private final Context context;
    private final List<DisplayItem> items = new ArrayList<>();

    public EmergencyContactAdapter(Context context) {
        this.context = context;
    }

    public void submitItems(List<DisplayItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_emergency_section_header, parent, false);
            return new HeaderViewHolder(view);
        }
        View view = inflater.inflate(R.layout.item_emergency_contact, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DisplayItem item = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            HeaderItem headerItem = (HeaderItem) item;
            ((HeaderViewHolder) holder).bind(headerItem);
        } else if (holder instanceof ContactViewHolder) {
            ContactItem contactItem = (ContactItem) item;
            ((ContactViewHolder) holder).bind(contactItem.getContact());
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private final class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleTextView;
        private final TextView subtitleTextView;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.section_title);
            subtitleTextView = itemView.findViewById(R.id.section_subtitle);
        }

        void bind(HeaderItem item) {
            titleTextView.setText(item.getTitle());
            if (item.getSubtitle() == null || item.getSubtitle().trim().isEmpty()) {
                subtitleTextView.setVisibility(View.GONE);
            } else {
                subtitleTextView.setVisibility(View.VISIBLE);
                subtitleTextView.setText(item.getSubtitle());
            }
        }
    }

    private final class ContactViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final View accentView;
        private final ImageView iconImageView;
        private final TextView nameTextView;
        private final TextView numberTextView;
        private final TextView provinceTextView;
        private final TextView cityTextView;
        private final TextView categoryTextView;
        private final TextView importantBadge;
        private final View callButton;

        ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.contact_card);
            accentView = itemView.findViewById(R.id.contact_accent);
            iconImageView = itemView.findViewById(R.id.contact_icon);
            nameTextView = itemView.findViewById(R.id.contact_name);
            numberTextView = itemView.findViewById(R.id.contact_number);
            provinceTextView = itemView.findViewById(R.id.contact_province);
            cityTextView = itemView.findViewById(R.id.contact_city);
            categoryTextView = itemView.findViewById(R.id.contact_category);
            importantBadge = itemView.findViewById(R.id.contact_important_badge);
            callButton = itemView.findViewById(R.id.emergency_call_button);
        }

        void bind(EmergencyContact contact) {
            iconImageView.setImageResource(contact.getIconResId());
            nameTextView.setText(contact.getName());
            numberTextView.setText(contact.getNumber());
            provinceTextView.setText(contact.getProvince());
            cityTextView.setText(contact.getCity());
            categoryTextView.setText(contact.getCategory());

            if (contact.isDefault()) {
                importantBadge.setVisibility(View.VISIBLE);
                cardView.setCardBackgroundColor(Color.parseColor("#FFF4F4"));
                cardView.setStrokeColor(Color.parseColor("#E53935"));
                accentView.setBackgroundColor(Color.parseColor("#E53935"));
            } else {
                importantBadge.setVisibility(View.GONE);
                cardView.setCardBackgroundColor(Color.WHITE);
                cardView.setStrokeColor(Color.parseColor("#F0D0D0"));
                accentView.setBackgroundColor(Color.parseColor("#FF8A80"));
            }

            callButton.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + contact.getNumber()));
                try {
                    context.startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    // Dialer unavailable; ignore silently because this is an emergency UX path.
                }
            });
        }
    }
}


