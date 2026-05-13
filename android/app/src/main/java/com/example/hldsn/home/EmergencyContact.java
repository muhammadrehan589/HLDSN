package com.example.hldsn.home;

public class EmergencyContact {
    private String name;
    private String number;
    private String province;
    private String city;
    private String category;
    private boolean isDefault;
    private int iconResId;

    public EmergencyContact(String name, String number, String province, String city, String category, boolean isDefault, int iconResId) {
        this.name = name;
        this.number = number;
        this.province = province;
        this.city = city;
        this.category = category;
        this.isDefault = isDefault;
        this.iconResId = iconResId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public int getIconResId() {
        return iconResId;
    }

    public void setIconResId(int iconResId) {
        this.iconResId = iconResId;
    }

}
