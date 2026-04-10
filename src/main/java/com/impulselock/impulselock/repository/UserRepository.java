package com.impulselock.impulselock.repository;

import com.impulselock.impulselock.model.UserProfile;
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
        return userProfile;
    };

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserProfile> getUserById(String userId) {
        String sql = "SELECT user_id, daily_limit, night_spending_allowed, sensitivity_level FROM users WHERE user_id = ?";
        return jdbcTemplate.query(sql, userRowMapper, userId).stream().findFirst();
    }
}
