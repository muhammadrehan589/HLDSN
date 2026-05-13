package com.example.hldsn.firstaid;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class FirstAidTipsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnTipClickListener {
        void onViewSteps(FirstAidTip tip);
    }

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_TIP = 1;

    public abstract static class DisplayItem {
        abstract int type();
    }

    public static final class HeaderItem extends DisplayItem {
        private final String title;

        public HeaderItem(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }

        @Override
        int type() {
            return TYPE_HEADER;
        }
    }

    public static final class TipItem extends DisplayItem {
        private final FirstAidTip tip;

        public TipItem(FirstAidTip tip) {
            this.tip = tip;
        }

        public FirstAidTip getTip() {
            return tip;
        }

        @Override
        int type() {
            return TYPE_TIP;
        }
    }

    private final Context context;
    private final OnTipClickListener listener;
    private final List<DisplayItem> items = new ArrayList<>();

    public FirstAidTipsAdapter(Context context, OnTipClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void submit(List<DisplayItem> displayItems) {
        items.clear();
        if (displayItems != null) {
            items.addAll(displayItems);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderViewHolder(inflater.inflate(R.layout.item_first_aid_header, parent, false));
        }
        return new TipViewHolder(inflater.inflate(R.layout.item_first_aid_tip, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DisplayItem item = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((HeaderItem) item);
        } else if (holder instanceof TipViewHolder) {
            ((TipViewHolder) holder).bind(((TipItem) item).getTip());
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static int colorForSeverity(String severity) {
        if ("Critical".equalsIgnoreCase(severity)) return Color.parseColor("#D32F2F");
        if ("Serious".equalsIgnoreCase(severity)) return Color.parseColor("#EF6C00");
        return Color.parseColor("#2E7D32");
    }

    private static int bgColorForSeverity(String severity) {
        if ("Critical".equalsIgnoreCase(severity)) return Color.parseColor("#FFEBEE");
        if ("Serious".equalsIgnoreCase(severity)) return Color.parseColor("#FFF3E0");
        return Color.parseColor("#E8F5E9");
    }

    private static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.header_title);
        }

        void bind(HeaderItem item) {
            title.setText(item.getTitle());
        }
    }

    private final class TipViewHolder extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView title;
        private final TextView desc;
        private final TextView category;
        private final TextView severity;
        private final MaterialButton button;
        private final View root;

        TipViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView;
            icon = itemView.findViewById(R.id.tip_icon);
            title = itemView.findViewById(R.id.tip_title);
            desc = itemView.findViewById(R.id.tip_description);
            category = itemView.findViewById(R.id.tip_category_badge);
            severity = itemView.findViewById(R.id.tip_severity_badge);
            button = itemView.findViewById(R.id.tip_view_steps);
        }

        void bind(FirstAidTip tip) {
            title.setText(tip.getTitle());
            desc.setText(tip.getShortDescription());
            category.setText(tip.getCategory());
            severity.setText(tip.getSeverity());
            icon.setImageResource(FirstAidTipsRepository.resolveIconRes(tip.getIcon()));

            GradientDrawable categoryBg = new GradientDrawable();
            categoryBg.setCornerRadius(999f);
            categoryBg.setColor(Color.parseColor("#FFF3CD"));
            category.setBackground(categoryBg);

            GradientDrawable severityBg = new GradientDrawable();
            severityBg.setCornerRadius(999f);
            severityBg.setColor(bgColorForSeverity(tip.getSeverity()));
            severity.setBackground(severityBg);
            severity.setTextColor(colorForSeverity(tip.getSeverity()));

            if ("Critical".equalsIgnoreCase(tip.getSeverity())) {
                root.setBackgroundColor(Color.parseColor("#FFF7F7"));
            } else {
                root.setBackgroundColor(Color.WHITE);
            }

            View.OnClickListener clickListener = v -> listener.onViewSteps(tip);
            button.setOnClickListener(clickListener);
            itemView.setOnClickListener(clickListener);
        }
    }
}

