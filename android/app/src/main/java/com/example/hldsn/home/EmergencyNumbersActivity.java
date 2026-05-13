package com.example.hldsn.home;

import android.graphics.Color;
import android.os.Bundle;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EmergencyNumbersActivity extends AppCompatActivity {

    private static final String PROVINCE_ALL = "All";
    private static final List<String> PROVINCE_ORDER = java.util.Arrays.asList(
            "Punjab",
            "Sindh",
            "KPK",
            "Balochistan",
            "Islamabad",
            "AJK",
            "Gilgit Baltistan"
    );

    private RecyclerView recyclerView;
    private EditText searchEditText;
    private ImageView backButton;
    private TextView emptyStateText;
    private ChipGroup provinceChipGroup;
    private Chip chipAll;
    private Chip chipPunjab;
    private Chip chipSindh;
    private Chip chipKpk;
    private Chip chipBalochistan;
    private Chip chipIslamabad;
    private Chip chipAjk;
    private Chip chipGb;

    private final List<EmergencyContact> allContacts = new ArrayList<>();
    private final List<EmergencyContact> filteredContacts = new ArrayList<>();
    private final List<EmergencyContactAdapter.DisplayItem> displayItems = new ArrayList<>();
    private EmergencyContactAdapter adapter;
    private String selectedProvince = PROVINCE_ALL;
    private String searchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_numbers);

        initViews();
        loadEmergencyContacts();
        setupRecyclerView();
        setupSearch();
        setupProvinceFilters();
        setupBackButton();
        refreshContacts();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.emergency_contacts_recycler);
        searchEditText = findViewById(R.id.emergency_search);
        backButton = findViewById(R.id.back_button);
        emptyStateText = findViewById(R.id.empty_state_text);
        provinceChipGroup = findViewById(R.id.province_chip_group);
        chipAll = findViewById(R.id.chip_all);
        chipPunjab = findViewById(R.id.chip_punjab);
        chipSindh = findViewById(R.id.chip_sindh);
        chipKpk = findViewById(R.id.chip_kpk);
        chipBalochistan = findViewById(R.id.chip_balochistan);
        chipIslamabad = findViewById(R.id.chip_islamabad);
        chipAjk = findViewById(R.id.chip_ajk);
        chipGb = findViewById(R.id.chip_gb);
    }

    private void loadEmergencyContacts() {
        addContact("Police Emergency", "15", "Pakistan", "All Pakistan", "Police", true, R.drawable.ic_police);
        addContact("Fire Brigade", "16", "Pakistan", "All Pakistan", "Fire", true, R.drawable.ic_fire);
        addContact("Rescue 1122", "1122", "Pakistan", "All Pakistan", "Rescue / Ambulance", true, R.drawable.ic_ambulance);
        addContact("Edhi Ambulance", "115", "Pakistan", "All Pakistan", "Ambulance", true, R.drawable.ic_ambulance);
        addContact("Motorway Police", "130", "Pakistan", "Highways / Motorways", "Traffic / Highway", true, R.drawable.ic_location);
        addContact("Medical Helpline", "1166", "Pakistan", "All Pakistan", "Medical", true, R.drawable.ic_hospital);

        addContact("Rescue 1122 Punjab", "1122", "Punjab", "All Punjab", "Rescue / Ambulance", false, R.drawable.ic_ambulance);
        addContact("Punjab Police", "15", "Punjab", "All Punjab", "Police", false, R.drawable.ic_police);
        addContact("Punjab Fire Brigade", "16", "Punjab", "All Punjab", "Fire", false, R.drawable.ic_fire);

        addContact("PIMS Hospital", "051-9261170", "Islamabad", "Islamabad", "Hospital", false, R.drawable.ic_hospital);
        addContact("Polyclinic Hospital", "051-9214965", "Islamabad", "Islamabad", "Hospital", false, R.drawable.ic_hospital);

        addContact("Sindh Rescue 1122", "1122", "Sindh", "All Sindh", "Rescue / Ambulance", false, R.drawable.ic_ambulance);
        addContact("Madadgar Police", "15", "Sindh", "All Sindh", "Police", false, R.drawable.ic_police);
        addContact("KMC Fire Brigade", "16", "Sindh", "Karachi", "Fire", false, R.drawable.ic_fire);
        addContact("Chhipa Ambulance", "1020", "Sindh", "Karachi", "Ambulance", false, R.drawable.ic_ambulance);
        addContact("Aman Ambulance", "1021", "Sindh", "Karachi", "Ambulance", false, R.drawable.ic_ambulance);
        addContact("SIEHS Online Doctor", "1123", "Sindh", "All Sindh", "Medical", false, R.drawable.ic_hospital);

        addContact("Rescue 1122 KPK", "1122", "KPK", "All KPK", "Rescue / Ambulance", false, R.drawable.ic_ambulance);
        addContact("KPK Police", "15", "KPK", "All KPK", "Police", false, R.drawable.ic_police);
        addContact("KPK Fire Brigade", "16", "KPK", "All KPK", "Fire", false, R.drawable.ic_fire);

        addContact("Rescue 1122 Balochistan", "1122", "Balochistan", "Available Areas", "Rescue / Ambulance", false, R.drawable.ic_ambulance);
        addContact("Balochistan Police", "15", "Balochistan", "All Balochistan", "Police", false, R.drawable.ic_police);
        addContact("Balochistan Fire Brigade", "16", "Balochistan", "All Balochistan", "Fire", false, R.drawable.ic_fire);

        addContact("Rescue 1122 AJK", "1122", "AJK", "All AJK", "Rescue / Ambulance", false, R.drawable.ic_ambulance);
        addContact("AJK Police", "15", "AJK", "All AJK", "Police", false, R.drawable.ic_police);

        addContact("Rescue 1122 Gilgit Baltistan", "1122", "Gilgit Baltistan", "Gilgit Baltistan", "Rescue / Ambulance", false, R.drawable.ic_ambulance);
        addContact("GB Police", "15", "Gilgit Baltistan", "Gilgit Baltistan", "Police", false, R.drawable.ic_police);
    }

    private void addContact(String name, String number, String province, String city, String category, boolean isDefault, int iconResId) {
        allContacts.add(new EmergencyContact(name, number, province, city, category, isDefault, iconResId));
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(false);
        adapter = new EmergencyContactAdapter(this);
        recyclerView.setAdapter(adapter);
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s == null ? "" : s.toString();
                refreshContacts();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setupProvinceFilters() {
        provinceChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds == null || checkedIds.isEmpty()) {
                return;
            }
            int checkedId = checkedIds.get(0);
            selectedProvince = provinceForChip(checkedId);
            updateProvinceChipStyles(checkedId);
            refreshContacts();
        });

        chipAll.setChecked(true);
        updateProvinceChipStyles(chipAll.getId());
    }

    private void updateProvinceChipStyles(int selectedId) {
        styleProvinceChip(chipAll, chipAll.getId() == selectedId);
        styleProvinceChip(chipPunjab, chipPunjab.getId() == selectedId);
        styleProvinceChip(chipSindh, chipSindh.getId() == selectedId);
        styleProvinceChip(chipKpk, chipKpk.getId() == selectedId);
        styleProvinceChip(chipBalochistan, chipBalochistan.getId() == selectedId);
        styleProvinceChip(chipIslamabad, chipIslamabad.getId() == selectedId);
        styleProvinceChip(chipAjk, chipAjk.getId() == selectedId);
        styleProvinceChip(chipGb, chipGb.getId() == selectedId);
    }

    private void styleProvinceChip(Chip chip, boolean selected) {
        if (chip == null) return;
        if (selected) {
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#C62828")));
            chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#C62828")));
            chip.setTextColor(Color.WHITE);
        } else {
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(Color.WHITE));
            chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#F2C2C2")));
            chip.setTextColor(Color.parseColor("#8E1D1D"));
        }
        chip.setChipStrokeWidth(1f);
    }

    private String provinceForChip(int chipId) {
        if (chipId == chipAll.getId()) return PROVINCE_ALL;
        if (chipId == chipPunjab.getId()) return "Punjab";
        if (chipId == chipSindh.getId()) return "Sindh";
        if (chipId == chipKpk.getId()) return "KPK";
        if (chipId == chipBalochistan.getId()) return "Balochistan";
        if (chipId == chipIslamabad.getId()) return "Islamabad";
        if (chipId == chipAjk.getId()) return "AJK";
        if (chipId == chipGb.getId()) return "Gilgit Baltistan";
        return PROVINCE_ALL;
    }

    private void refreshContacts() {
        filteredContacts.clear();
        filteredContacts.addAll(filterContacts());
        displayItems.clear();
        displayItems.addAll(buildDisplayItems(filteredContacts));
        adapter.submitItems(displayItems);

        boolean empty = displayItems.isEmpty();
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyStateText.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private List<EmergencyContact> filterContacts() {
        List<EmergencyContact> matches = new ArrayList<>();
        String query = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.US);
        String normalizedQueryNumbers = normalizeNumberSearch(query);

        for (EmergencyContact contact : allContacts) {
            boolean provinceAllowed = contact.isDefault()
                    || PROVINCE_ALL.equals(selectedProvince)
                    || selectedProvince.equalsIgnoreCase(contact.getProvince());

            if (!provinceAllowed) {
                continue;
            }

            if (query.isEmpty()) {
                matches.add(contact);
                continue;
            }

            String name = safeLower(contact.getName());
            String province = safeLower(contact.getProvince());
            String city = safeLower(contact.getCity());
            String category = safeLower(contact.getCategory());
            String number = normalizeNumberSearch(safeLower(contact.getNumber()));

            if (name.contains(query)
                    || province.contains(query)
                    || city.contains(query)
                    || category.contains(query)
                    || (!normalizedQueryNumbers.isEmpty() && number.contains(normalizedQueryNumbers))) {
                matches.add(contact);
            }
        }

        return matches;
    }

    private List<EmergencyContactAdapter.DisplayItem> buildDisplayItems(List<EmergencyContact> contacts) {
        List<EmergencyContactAdapter.DisplayItem> items = new ArrayList<>();

        List<EmergencyContact> important = new ArrayList<>();
        List<EmergencyContact> provincial = new ArrayList<>();

        for (EmergencyContact contact : contacts) {
            if (contact.isDefault()) {
                important.add(contact);
            } else {
                provincial.add(contact);
            }
        }

        if (!important.isEmpty()) {
            items.add(new EmergencyContactAdapter.HeaderItem(
                    getString(R.string.most_important_numbers),
                    getString(R.string.most_important_numbers_subtitle)));
            for (EmergencyContact contact : important) {
                items.add(new EmergencyContactAdapter.ContactItem(contact));
            }
        }

        Map<String, List<EmergencyContact>> grouped = new LinkedHashMap<>();
        for (String province : PROVINCE_ORDER) {
            grouped.put(province, new ArrayList<>());
        }

        for (EmergencyContact contact : provincial) {
            List<EmergencyContact> provinceList = grouped.get(contact.getProvince());
            if (provinceList != null) {
                provinceList.add(contact);
            }
        }

        for (Map.Entry<String, List<EmergencyContact>> entry : grouped.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }

            items.add(new EmergencyContactAdapter.HeaderItem(
                    entry.getKey(),
                    getString(R.string.province_contacts_subtitle)));
            for (EmergencyContact contact : entry.getValue()) {
                items.add(new EmergencyContactAdapter.ContactItem(contact));
            }
        }

        return items;
    }

    private void setupBackButton() {
        backButton.setOnClickListener(v -> finish());
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }

    private static String normalizeNumberSearch(String value) {
        return value == null ? "" : value.replace(" ", "").replace("-", "");
    }
}




