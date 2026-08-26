package com.impulselock.impulselock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Normalized replacement for V1's comma-separated {@code users.restricted_categories} string
 * column (see docs/v1/database.md#schema-code-mismatch-restricted_categories).
 */
@Entity
@Table(name = "restricted_categories", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "category"}))
@EntityListeners(AuditingEntityListener.class)
public class RestrictedCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String category;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RestrictedCategory() {
        // JPA
    }

    public RestrictedCategory(User user, String category) {
        this.user = user;
        this.category = category;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getCategory() {
        return category;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
