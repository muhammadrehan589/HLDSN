package com.example.hldsn.incident_report_module;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.example.hldsn.R;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ChatsActivity extends AppCompatActivity {

    private ChatsViewModel chatsViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chats);

        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) { finish(); return; }
        chatsViewModel = new ViewModelProvider(this).get(ChatsViewModel.class);


        // ── Header buttons ───────────────────────────────────────────────────
        ImageView backIcon = findViewById(R.id.backButton);
        if (backIcon != null) backIcon.setOnClickListener(v -> finish());

        setupTabs();

        // Search updates both tabs through the shared ViewModel query.
        EditText searchChats = findViewById(R.id.searchChats);
        if (searchChats != null) {
            searchChats.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    chatsViewModel.setSearchQuery(s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        chatsViewModel.startDataStreams();
    }

    @Override
    protected void onStop() {
        super.onStop();
        chatsViewModel.stopDataStreams();
    }

    private void setupTabs() {
        TabLayout usersTabLayout = findViewById(R.id.usersTabLayout);
        ViewPager2 usersViewPager = findViewById(R.id.usersViewPager);
        usersViewPager.setAdapter(new ChatsPagerAdapter(this));
        usersViewPager.setCurrentItem(0, false);

        new TabLayoutMediator(usersTabLayout, usersViewPager,
                (tab, position) -> tab.setText(position == 0 ? "All Users" : "Nearby Users"))
                .attach();
    }
}

