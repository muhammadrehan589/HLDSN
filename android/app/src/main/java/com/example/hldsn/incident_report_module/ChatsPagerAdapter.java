package com.example.hldsn.incident_report_module;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ChatsPagerAdapter extends FragmentStateAdapter {

    public ChatsPagerAdapter(@NonNull AppCompatActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return position == 0 ? new OnlineUsersFragment() : new NearbyUsersFragment();
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}

