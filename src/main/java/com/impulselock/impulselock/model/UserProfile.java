package com.impulselock.impulselock.model;

import java.util.List;

public class UserProfile {

    private String userId;
    private double dailyLimit;
    private boolean nightSpendingAllowed;
    private int sensitivityLevel;
    private List<String> restrictedCategories;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public double getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(double dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public boolean isNightSpendingAllowed() {
        return nightSpendingAllowed;
    }

    public void setNightSpendingAllowed(boolean nightSpendingAllowed) {
        this.nightSpendingAllowed = nightSpendingAllowed;
    }

    public int getSensitivityLevel() {
        return sensitivityLevel;
    }

    public void setSensitivityLevel(int sensitivityLevel) {
        this.sensitivityLevel = sensitivityLevel;
    }

    public List<String> getRestrictedCategories() {
        return restrictedCategories;
    }

    public void setRestrictedCategories(List<String> restrictedCategories) {
        this.restrictedCategories = restrictedCategories;
    }
}
