package com.example.hldsn.firstaid;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class EmergencyFirstAidActivity extends AppCompatActivity {

    public static final String EXTRA_TIP_ID = "extra_tip_id";
    private static final String PREFS = "first_aid_prefs";
    private static final String KEY_RECENTS = "recent_tip_ids";

    private RecyclerView recyclerView;
    private TextView emptyText;
    private EditText search;
    private ChipGroup chipGroup;

    private final List<FirstAidTip> allTips = new ArrayList<>();
    private final List<FirstAidTip> quickTips = new ArrayList<>();
    private final Set<Integer> quickTipIds = new LinkedHashSet<>(Arrays.asList(1, 2, 3, 5, 6));

    private FirstAidTipsAdapter adapter;
    private String selectedCategory = "All";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_first_aid);

        allTips.addAll(FirstAidTipsRepository.getTips());
        for (FirstAidTip tip : allTips) {
            if (quickTipIds.contains(tip.getId())) {
                quickTips.add(tip);
            }
        }

        bindViews();
        setupRecycler();
        setupFilters();
        refreshList();
    }

    private void bindViews() {
        recyclerView = findViewById(R.id.first_aid_recycler);
        emptyText = findViewById(R.id.first_aid_empty_text);
        search = findViewById(R.id.first_aid_search);
        chipGroup = findViewById(R.id.first_aid_chip_group);
        ImageView back = findViewById(R.id.first_aid_back);
        back.setOnClickListener(v -> finish());
    }

    private void setupRecycler() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FirstAidTipsAdapter(this, this::openTip);
        recyclerView.setAdapter(adapter);
    }

    private void setupFilters() {
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshList();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds == null || checkedIds.isEmpty()) return;
            int chipId = checkedIds.get(0);
            Chip chip = findViewById(chipId);
            if (chip != null) {
                selectedCategory = chip.getText().toString();
                updateChipStyles(chipId);
                refreshList();
            }
        });

        int checkedId = chipGroup.getCheckedChipId();
        if (checkedId != View.NO_ID) {
            updateChipStyles(checkedId);
        }
    }

    private void refreshList() {
        String q = search.getText() == null ? "" : search.getText().toString().trim().toLowerCase(Locale.US);
        List<FirstAidTip> filtered = new ArrayList<>();
        for (FirstAidTip tip : allTips) {
            if (!"All".equalsIgnoreCase(selectedCategory)
                    && !tip.getCategory().equalsIgnoreCase(selectedCategory)) {
                continue;
            }
            if (q.isEmpty() || matchesQuery(tip, q)) {
                filtered.add(tip);
            }
        }

        List<FirstAidTipsAdapter.DisplayItem> items = new ArrayList<>();

        List<FirstAidTip> recentMatches = filterByRecent(filtered, loadRecents());
        if (!recentMatches.isEmpty()) {
            items.add(new FirstAidTipsAdapter.HeaderItem("Recently Viewed"));
            for (FirstAidTip tip : recentMatches) {
                items.add(new FirstAidTipsAdapter.TipItem(tip));
            }
        }

        List<FirstAidTip> quickMatches = new ArrayList<>();
        for (FirstAidTip tip : quickTips) {
            if (containsTip(filtered, tip.getId()) && !containsTip(recentMatches, tip.getId())) {
                quickMatches.add(tip);
            }
        }
        if (!quickMatches.isEmpty()) {
            items.add(new FirstAidTipsAdapter.HeaderItem("Quick Emergency Help"));
            for (FirstAidTip tip : quickMatches) {
                items.add(new FirstAidTipsAdapter.TipItem(tip));
            }
        }

        List<FirstAidTip> remaining = new ArrayList<>();
        for (FirstAidTip tip : filtered) {
            if (!quickTipIds.contains(tip.getId()) && !containsTip(recentMatches, tip.getId())) {
                remaining.add(tip);
            }
        }
        if (!remaining.isEmpty()) {
            items.add(new FirstAidTipsAdapter.HeaderItem("All First Aid Tips"));
            for (FirstAidTip tip : remaining) {
                items.add(new FirstAidTipsAdapter.TipItem(tip));
            }
        }

        adapter.submit(items);
        boolean isEmpty = items.isEmpty();
        emptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private boolean matchesQuery(FirstAidTip tip, String q) {
        if (tip.getTitle().toLowerCase(Locale.US).contains(q)) return true;
        if (tip.getCategory().toLowerCase(Locale.US).contains(q)) return true;
        if (tip.getShortDescription().toLowerCase(Locale.US).contains(q)) return true;
        for (String keyword : tip.getKeywords()) {
            if (keyword.toLowerCase(Locale.US).contains(q)) return true;
        }
        return false;
    }

    private List<Integer> loadRecents() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String raw = prefs.getString(KEY_RECENTS, "");
        List<Integer> ids = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return ids;
        for (String part : raw.split(",")) {
            try {
                ids.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }

    private List<FirstAidTip> filterByRecent(List<FirstAidTip> source, List<Integer> recents) {
        List<FirstAidTip> result = new ArrayList<>();
        for (Integer id : recents) {
            for (FirstAidTip tip : source) {
                if (tip.getId() == id) {
                    result.add(tip);
                    break;
                }
            }
            if (result.size() >= 4) {
                break;
            }
        }
        return result;
    }

    private boolean containsTip(List<FirstAidTip> tips, int id) {
        for (FirstAidTip tip : tips) {
            if (tip.getId() == id) return true;
        }
        return false;
    }

    private void openTip(FirstAidTip tip) {
        Intent intent = new Intent(this, FirstAidDetailActivity.class);
        intent.putExtra(EXTRA_TIP_ID, tip.getId());
        startActivity(intent);
    }

    private void updateChipStyles(int selectedChipId) {
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            View child = chipGroup.getChildAt(i);
            if (!(child instanceof Chip)) continue;
            Chip chip = (Chip) child;
            boolean selected = chip.getId() == selectedChipId;
            if (selected) {
                chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_red)));
                chip.setTextColor(ContextCompat.getColor(this, R.color.text_on_primary));
            } else {
                chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.surface_primary)));
                chip.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }
            chip.setChipStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_red)));
            chip.setChipStrokeWidth(1f);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }
}


