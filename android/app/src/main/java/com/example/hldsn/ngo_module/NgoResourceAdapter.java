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

    public interface OnEditListener { void onEdit(NgoResource resource); }

    private final List<NgoResource> items = new ArrayList<>();
    private final OnEditListener editListener;

    public NgoResourceAdapter(OnEditListener editListener) {
        this.editListener = editListener;
    }

    public void setItems(List<NgoResource> list) {
        items.clear();
        if (list != null) items.addAll(list);
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
        NgoResource r = items.get(position);
        holder.tvName.setText(r.getName());
        holder.tvQuantity.setText("Quantity: " + r.getQuantity());
        holder.tvLocation.setText("Location: " + (r.getLocation() == null ? "" : r.getLocation()));
        holder.tvDescription.setText(r.getDescription() == null ? "" : r.getDescription());
        holder.btnEdit.setOnClickListener(v -> { if (editListener != null) editListener.onEdit(r); });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvQuantity, tvLocation, tvDescription;
        MaterialButton btnEdit;
        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvResourceName);
            tvQuantity = itemView.findViewById(R.id.tvResourceQuantity);
            tvLocation = itemView.findViewById(R.id.tvResourceLocation);
            tvDescription = itemView.findViewById(R.id.tvResourceDescription);
            btnEdit = itemView.findViewById(R.id.btnEditResource);
        }
    }
}
