package com.impulselock.impulselock.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Transitional request shape for {@code POST /users} while Phase 1 auth doesn't exist yet.
 * Carries a raw {@code password} only because there is no registration endpoint yet to have
 * set one properly - Phase 1 replaces this endpoint with a real {@code /auth/register} and
 * removes password handling from here entirely (see docs/v2/security-design.md).
 */
public class UserUpsertRequest {

    private String username;
    private String email;
    private String password;
    private BigDecimal dailyLimit;
    private boolean nightSpendingAllowed;
    private int sensitivityLevel;
    private List<String> restrictedCategories;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

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
