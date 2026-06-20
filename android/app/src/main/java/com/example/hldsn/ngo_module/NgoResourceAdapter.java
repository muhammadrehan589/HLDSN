package com.example.hldsn.ngo_module;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class NgoResourceAdapter extends RecyclerView.Adapter<NgoResourceAdapter.VH> {

    public interface OnDonateListener { void onDonate(NgoResource resource); }

    private final List<NgoResource> allItems = new ArrayList<>();
    private final List<NgoResource> filteredItems = new ArrayList<>();
    private final OnDonateListener donateListener;

    public NgoResourceAdapter(OnDonateListener donateListener) {
        this.donateListener = donateListener;
    }

    public void setItems(List<NgoResource> list) {
        allItems.clear();
        if (list != null) allItems.addAll(list);
        filteredItems.clear();
        filteredItems.addAll(allItems);
        notifyDataSetChanged();
    }

    /**
     * Filters the inventory list by search query and/or category.
     * @param query text to match against item name (case-insensitive)
     * @param category category to filter by, or null/"All" to show all categories
     */
    public void filter(String query, String category) {
        filteredItems.clear();
        String q = (query == null) ? "" : query.trim().toLowerCase();
        boolean hasCategory = category != null && !category.isEmpty()
                && !category.equalsIgnoreCase("All");

        for (NgoResource r : allItems) {
            String name = r.getName() == null ? "" : r.getName().toLowerCase();
            boolean matchesQuery = q.isEmpty() || name.contains(q);
            boolean matchesCategory = !hasCategory || name.equalsIgnoreCase(category)
                    || (r.getName() != null && r.getName().equalsIgnoreCase(category));
            if (matchesQuery && matchesCategory) {
                filteredItems.add(r);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ngo_resource, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        NgoResource r = filteredItems.get(position);
        holder.tvName.setText(r.getName());
        holder.tvQuantity.setText("Quantity: " + r.getQuantity());
        holder.tvLocation.setText("Location: " + (r.getLocation() == null ? "" : r.getLocation()));
        holder.tvDescription.setText(r.getDescription() == null ? "" : r.getDescription());
        holder.btnDonate.setOnClickListener(v -> { if (donateListener != null) donateListener.onDonate(r); });
    }

    @Override
    public int getItemCount() { return filteredItems.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvQuantity, tvLocation, tvDescription;
        MaterialButton btnDonate;
        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvResourceName);
            tvQuantity = itemView.findViewById(R.id.tvResourceQuantity);
            tvLocation = itemView.findViewById(R.id.tvResourceLocation);
            tvDescription = itemView.findViewById(R.id.tvResourceDescription);
            btnDonate = itemView.findViewById(R.id.btnEditResource);
        }
    }
}
