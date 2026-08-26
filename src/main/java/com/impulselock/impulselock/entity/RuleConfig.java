package com.impulselock.impulselock.entity;

import com.impulselock.impulselock.entity.converter.JsonMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * DB-backed rule weight/threshold configuration. Seeded in V2__seed_roles_and_rule_configs.sql
 * with values matching V1's hardcoded literals (see docs/v1/rule-engine.md). Not yet consulted
 * by the rule engine in Phase 0 - {@code rules/} still uses the same hardcoded constructor
 * literals as V1 until Phase 2 wires this entity in (see docs/v2/tasks.md, Phase 2).
 */
@Entity
@Table(name = "rule_configs")
@EntityListeners(AuditingEntityListener.class)
public class RuleConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_code", nullable = false, unique = true, length = 50)
    private String ruleCode;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(nullable = false)
    private boolean enabled = true;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "JSON")
    private Map<String, Object> params;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
