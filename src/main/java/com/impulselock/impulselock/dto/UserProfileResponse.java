package com.impulselock.impulselock.dto;

import com.impulselock.impulselock.entity.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response shape for user profile endpoints. Deliberately excludes {@code passwordHash} -
 * never map a {@code User} entity directly to JSON (see docs/v2/security-design.md's password
 * storage principle: the hash must never leave the service layer).
 */
public class UserProfileResponse {

    private final Long id;
    private final String username;
    private final String email;
    private final BigDecimal dailyLimit;
    private final boolean nightSpendingAllowed;
    private final int sensitivityLevel;
    private final boolean enabled;
    private final List<String> restrictedCategories;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public UserProfileResponse(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.dailyLimit = user.getDailyLimit();
        this.nightSpendingAllowed = user.isNightSpendingAllowed();
        this.sensitivityLevel = user.getSensitivityLevel();
        this.enabled = user.isEnabled();
        this.restrictedCategories = user.getRestrictedCategoryNames();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public boolean isNightSpendingAllowed() {
        return nightSpendingAllowed;
    }

    public int getSensitivityLevel() {
        return sensitivityLevel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getRestrictedCategories() {
        return restrictedCategories;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
