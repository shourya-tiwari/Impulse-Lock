package com.impulselock.impulselock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Single-row config replacing {@code DecisionEngine}'s hardcoded BLOCK/DELAY literals (80/40 -
 * see docs/v1/rule-engine.md). Seeded in V3__add_decision_thresholds.sql.
 */
@Entity
@Table(name = "decision_thresholds")
@EntityListeners(AuditingEntityListener.class)
public class DecisionThresholds {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "block_threshold", nullable = false, precision = 5, scale = 2)
    private BigDecimal blockThreshold;

    @Column(name = "delay_threshold", nullable = false, precision = 5, scale = 2)
    private BigDecimal delayThreshold;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public BigDecimal getBlockThreshold() {
        return blockThreshold;
    }

    public void setBlockThreshold(BigDecimal blockThreshold) {
        this.blockThreshold = blockThreshold;
    }

    public BigDecimal getDelayThreshold() {
        return delayThreshold;
    }

    public void setDelayThreshold(BigDecimal delayThreshold) {
        this.delayThreshold = delayThreshold;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
