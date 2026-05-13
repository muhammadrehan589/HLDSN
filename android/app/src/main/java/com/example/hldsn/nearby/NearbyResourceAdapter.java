package com.example.hldsn.nearby;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.hldsn.R;
import java.util.List;

/**
 * Adapter for displaying nearby resources in RecyclerView
 */
public class NearbyResourceAdapter extends RecyclerView.Adapter<NearbyResourceAdapter.ViewHolder> {
    private List<NearbyResource> resources;
    private final Context context;

    public NearbyResourceAdapter(Context context, List<NearbyResource> resources) {
        this.context = context;
        this.resources = resources;
    }

    public void setResources(List<NearbyResource> resources) {
        this.resources = resources;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_nearby_resource, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NearbyResource resource = resources.get(position);
        holder.bind(resource);
    }

    @Override
    public int getItemCount() {
        return resources == null ? 0 : resources.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView iconImageView;
        private final TextView nameTextView;
        private final TextView categoryTextView;
        private final TextView distanceTextView;
        private final TextView addressTextView;
        private final TextView verifiedBadge;
        private final Button directionsButton;
        private final Button callButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconImageView = itemView.findViewById(R.id.nearby_resource_icon);
            nameTextView = itemView.findViewById(R.id.nearby_resource_name);
            categoryTextView = itemView.findViewById(R.id.nearby_resource_category);
            distanceTextView = itemView.findViewById(R.id.nearby_resource_distance);
            addressTextView = itemView.findViewById(R.id.nearby_resource_address);
            verifiedBadge = itemView.findViewById(R.id.nearby_verified_badge);
            directionsButton = itemView.findViewById(R.id.nearby_directions_btn);
            callButton = itemView.findViewById(R.id.nearby_call_btn);
        }

        void bind(NearbyResource resource) {
            setIconForCategory(resource.category);

            nameTextView.setText(resource.name);
            categoryTextView.setText(resource.category);
            distanceTextView.setText(context.getString(R.string.nearby_help_away_km, resource.distance));

            if (resource.address != null && !resource.address.isEmpty()) {
                addressTextView.setText(resource.address);
                addressTextView.setVisibility(View.VISIBLE);
            } else {
                addressTextView.setVisibility(View.GONE);
            }

            if (resource.isVerified) {
                verifiedBadge.setVisibility(View.VISIBLE);
            } else {
                verifiedBadge.setVisibility(View.GONE);
            }

            directionsButton.setOnClickListener(v -> openDirections(resource.latitude, resource.longitude, resource.name));

            if (resource.phone != null && !resource.phone.isEmpty()) {
                callButton.setVisibility(View.VISIBLE);
                callButton.setOnClickListener(v -> openDialer(resource.phone));
            } else {
                callButton.setVisibility(View.GONE);
            }
        }

        private void setIconForCategory(String category) {
            int iconRes = R.drawable.ic_location;
            switch (category) {
                case "Hospitals": iconRes = R.drawable.ic_hospital; break;
                case "Pharmacies": iconRes = R.drawable.ic_pharmacy; break;
                case "Police": iconRes = R.drawable.ic_police; break;
                case "Fire": iconRes = R.drawable.ic_fire; break;
                case "Clinics": iconRes = R.drawable.ic_hospital; break;
                case "Shelters": iconRes = R.drawable.ic_shelter; break;
                case "Blood Banks": iconRes = R.drawable.ic_blood_bank; break;
                case "Water": iconRes = R.drawable.ic_water; break;
                case "Government": iconRes = R.drawable.ic_government; break;
                case "Rescue": iconRes = R.drawable.ic_emergency_numbers; break;
            }
            iconImageView.setImageResource(iconRes);
        }

        private void openDirections(double latitude, double longitude, String label) {
            try {
                Uri gmmIntentUri = Uri.parse("google.navigation:q=" + latitude + "," + longitude);
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                context.startActivity(mapIntent);
            } catch (Exception e) {
                try {
                    Uri geoUri = Uri.parse("geo:" + latitude + "," + longitude + "?q=" + label);
                    Intent intent = new Intent(Intent.ACTION_VIEW, geoUri);
                    context.startActivity(intent);
                } catch (Exception ex) {
                    Uri browserUri = Uri.parse("https://maps.google.com/?q=" + latitude + "," + longitude);
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, browserUri);
                    context.startActivity(browserIntent);
                }
            }
        }

        private void openDialer(String phone) {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + phone));
            context.startActivity(intent);
        }
    }
}
