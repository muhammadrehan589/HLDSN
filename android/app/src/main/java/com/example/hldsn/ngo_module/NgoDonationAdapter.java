package com.example.hldsn.ngo_module;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NgoDonationAdapter extends RecyclerView.Adapter<NgoDonationAdapter.VH> {

    private final List<NgoDonation> items = new ArrayList<>();

    public void setItems(List<NgoDonation> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ngo_donation, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        NgoDonation d = items.get(position);
        holder.tvName.setText(d.getName());
        holder.tvQuantity.setText("Donated Quantity: " + d.getQuantity());
        holder.tvLocation.setText("Donation Location: " + (d.getDonationLocation() == null ? "" : d.getDonationLocation()));

        if (d.getCreatedAt() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
            holder.tvDate.setText(sdf.format(d.getCreatedAt().toDate()));
        } else {
            holder.tvDate.setText("");
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvQuantity, tvLocation, tvDate;
        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvDonationName);
            tvQuantity = itemView.findViewById(R.id.tvDonationQuantity);
            tvLocation = itemView.findViewById(R.id.tvDonationLocation);
            tvDate = itemView.findViewById(R.id.tvDonationDate);
        }
    }
}
