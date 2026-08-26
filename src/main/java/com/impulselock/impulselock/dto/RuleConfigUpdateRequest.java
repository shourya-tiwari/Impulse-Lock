package com.impulselock.impulselock.dto;

import java.math.BigDecimal;
import java.util.Map;

public class RuleConfigUpdateRequest {

    private BigDecimal weight;
    private boolean enabled;
    private Map<String, Object> params;

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
