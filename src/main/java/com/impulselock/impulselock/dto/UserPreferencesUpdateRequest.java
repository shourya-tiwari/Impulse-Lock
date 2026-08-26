package com.impulselock.impulselock.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Replaces the Phase 0 transitional {@code UserUpsertRequest} now that account creation
 * belongs solely to {@code /auth/register} - this DTO only ever updates preferences on the
 * already-authenticated caller's own account (see docs/v2/api-design.md#conventions).
 */
public class UserPreferencesUpdateRequest {

    private BigDecimal dailyLimit;
    private boolean nightSpendingAllowed;
    private int sensitivityLevel;
    private List<String> restrictedCategories;

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(BigDecimal dailyLimit) {
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
