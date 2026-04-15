package com.impulselock.impulselock.repository;

import com.impulselock.impulselock.model.UserProfile;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<UserProfile> userRowMapper = (rs, rowNum) -> {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(rs.getString("user_id"));
        userProfile.setDailyLimit(rs.getDouble("daily_limit"));
        userProfile.setNightSpendingAllowed(rs.getBoolean("night_spending_allowed"));
        userProfile.setSensitivityLevel(rs.getInt("sensitivity_level"));
        try {
            String restricted = rs.getString("restricted_categories");
            if (restricted != null && !restricted.isBlank()) {
                List<String> categories = Arrays.stream(restricted.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .toList();
                userProfile.setRestrictedCategories(categories);
            }
        } catch (Exception ignored) {
            // Column may not exist yet; keep backward compatibility.
        }
        return userProfile;
    };

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserProfile> getUserById(String userId) {
        String sql = "SELECT user_id, daily_limit, night_spending_allowed, sensitivity_level, restricted_categories FROM users WHERE user_id = ?";
        return jdbcTemplate.query(sql, userRowMapper, userId).stream().findFirst();
    }

    public UserProfile upsertUser(UserProfile userProfile) {
        if (userProfile == null) {
            throw new IllegalArgumentException("UserProfile request body is required");
        }
        if (userProfile.getUserId() == null || userProfile.getUserId().isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }

        String restricted = "";
        if (userProfile.getRestrictedCategories() != null && !userProfile.getRestrictedCategories().isEmpty()) {
            restricted = String.join(",", userProfile.getRestrictedCategories());
        }

        String sql = """
                INSERT INTO users (user_id, daily_limit, night_spending_allowed, sensitivity_level, restricted_categories)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    daily_limit = VALUES(daily_limit),
                    night_spending_allowed = VALUES(night_spending_allowed),
                    sensitivity_level = VALUES(sensitivity_level),
                    restricted_categories = VALUES(restricted_categories)
                """;

        jdbcTemplate.update(
                sql,
                userProfile.getUserId(),
                userProfile.getDailyLimit(),
                userProfile.isNightSpendingAllowed(),
                userProfile.getSensitivityLevel(),
                restricted
        );
        return userProfile;
    }
}
