package com.impulselock.impulselock.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Replaces the Phase 0 transitional {@code UserUpsertRequest} now that account creation
 * belongs solely to {@code /auth/register} - this DTO only ever updates preferences on the
 * already-authenticated caller's own account (see docs/v2/api-design.md#conventions).
 *
 * <p><b>Phase 4 cleanup</b>: dropped {@code restrictedCategories} - api-design.md always scoped
 * this endpoint to {@code dailyLimit}/{@code nightSpendingAllowed}/{@code sensitivityLevel} only;
 * restricted-category management belongs to the granular
 * {@code /users/me/restricted-categories} endpoints Phase 3 already built. Also changed
 * {@code sensitivityLevel} from primitive {@code int} to {@code Integer}: the earlier "0 means
 * use the default of 5" sentinel was incompatible with real {@code @Min(1)}/{@code @Max(10)}
 * validation (a request that omits the field would fail validation before ever reaching that
 * fallback) - PUT semantics now require it explicitly, like {@code dailyLimit} already did.
 */
public class UserPreferencesUpdateRequest {

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal dailyLimit;

    private boolean nightSpendingAllowed;

    @NotNull
    @Min(1)
    @Max(10)
    private Integer sensitivityLevel;

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

    public Integer getSensitivityLevel() {
        return sensitivityLevel;
    }

    public void setSensitivityLevel(Integer sensitivityLevel) {
        this.sensitivityLevel = sensitivityLevel;
    }
}
