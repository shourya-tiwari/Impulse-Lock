package com.impulselock.impulselock.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Replaces V1's {@code model.UserProfile} (see docs/v1/database.md). Identity is now a
 * server-generated id + unique username, not a client-chosen free-text userId.
 */
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "daily_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyLimit = BigDecimal.ZERO;

    @Column(name = "night_spending_allowed", nullable = false)
    private boolean nightSpendingAllowed;

    @Column(name = "sensitivity_level", nullable = false, columnDefinition = "TINYINT")
    private int sensitivityLevel = 5;

    @Column(nullable = false)
    private boolean enabled = true;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    /**
     * EAGER deliberately, matching {@link #roles} above and for the same reason: essentially every
     * path that loads a User immediately needs this collection. {@code UserProfileResponse} reads
     * it for every user-facing response (login, refresh, /users/me, preferences, the admin user
     * list), and {@code CategoryRestrictionRule} reads it on every transaction evaluation.
     *
     * <p>It was LAZY, which threw {@code LazyInitializationException} on all of those paths:
     * {@code spring.jpa.open-in-view=false} (correctly) closes the persistence session when the
     * service's {@code @Transactional} boundary ends, but the DTOs are constructed afterwards in
     * the controller layer, so touching the collection there had no session to initialize from.
     * Registration masked the bug - a just-built entity carries an already-initialized collection,
     * so only entities re-loaded from the database (i.e. every subsequent request) actually failed.
     *
     * <p>Fetching eagerly here fixes all four load paths at once - {@code findByUsername},
     * {@code findById}, the paginated {@code findAll}, and the LAZY {@code RefreshToken.user}
     * traversed by /auth/refresh - rather than requiring an {@code @EntityGraph} on each finder,
     * which the next finder added would silently miss. The collection is a handful of rows per
     * user, so the cost is negligible at this scale.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<RestrictedCategory> restrictedCategories = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public List<RestrictedCategory> getRestrictedCategories() {
        return restrictedCategories;
    }

    /** Convenience accessor used by {@code CategoryRestrictionRule} - see docs/v2/rules. */
    public List<String> getRestrictedCategoryNames() {
        return restrictedCategories.stream()
                .map(RestrictedCategory::getCategory)
                .collect(Collectors.toList());
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
