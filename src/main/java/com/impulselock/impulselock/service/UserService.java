package com.impulselock.impulselock.service;

import com.impulselock.impulselock.dto.UserPreferencesUpdateRequest;
import com.impulselock.impulselock.entity.RestrictedCategory;
import com.impulselock.impulselock.entity.Role;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.exception.DatabaseOperationException;
import com.impulselock.impulselock.exception.UserNotFoundException;
import com.impulselock.impulselock.repository.RoleRepository;
import com.impulselock.impulselock.repository.UserRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account creation now lives here only via {@link #registerNewUser} (called from
 * {@code AuthService.register}, see docs/v2/security-design.md) - the Phase 0 transitional
 * {@code upsertUser(UserUpsertRequest)} that both created and updated accounts through one
 * unauthenticated endpoint is gone; {@link #updatePreferences} only ever operates on an
 * already-authenticated caller's own account.
 */
@Service
public class UserService {

    private static final String DEFAULT_RESTRICTED_CATEGORY = "LUXURY";
    private static final String USER_ROLE_NAME = "ROLE_USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User registerNewUser(String username, String email, String rawPassword) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));

        Role userRole = roleRepository.findByName(USER_ROLE_NAME)
                .orElseThrow(() -> new IllegalStateException(USER_ROLE_NAME + " is missing - seed migration did not run"));
        user.setRoles(new HashSet<>(Set.of(userRole)));

        // Preserves V1's default behavior (see docs/v1/rule-engine.md#categoryrestrictionrule)
        // as an explicit, visible, editable row instead of an implicit rule-level fallback.
        user.getRestrictedCategories().add(new RestrictedCategory(user, DEFAULT_RESTRICTED_CATEGORY));

        try {
            return userRepository.save(user);
        } catch (DataAccessException exception) {
            throw new DatabaseOperationException("Failed to save user in database", exception);
        }
    }

    @Transactional
    public User updatePreferences(String username, UserPreferencesUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Preferences request body is required");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found for username: " + username));

        user.setDailyLimit(request.getDailyLimit() != null ? request.getDailyLimit() : BigDecimal.ZERO);
        user.setNightSpendingAllowed(request.isNightSpendingAllowed());
        user.setSensitivityLevel(request.getSensitivityLevel() == 0 ? 5 : request.getSensitivityLevel());

        List<String> categories = request.getRestrictedCategories();
        if (categories != null) {
            user.getRestrictedCategories().clear();
            for (String category : categories) {
                if (category != null && !category.isBlank()) {
                    user.getRestrictedCategories().add(new RestrictedCategory(user, category.trim()));
                }
            }
        }

        try {
            return userRepository.save(user);
        } catch (DataAccessException exception) {
            throw new DatabaseOperationException("Failed to save user in database", exception);
        }
    }

    @Transactional(readOnly = true)
    public User getProfile(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found for username: " + username));
    }

    @Transactional
    public User addRestrictedCategory(String username, String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category is required");
        }

        User user = getProfile(username);
        String normalized = category.trim();
        boolean alreadyPresent = user.getRestrictedCategoryNames().stream().anyMatch(normalized::equalsIgnoreCase);
        if (!alreadyPresent) {
            user.getRestrictedCategories().add(new RestrictedCategory(user, normalized));
        }

        try {
            return userRepository.save(user);
        } catch (DataAccessException exception) {
            throw new DatabaseOperationException("Failed to save user in database", exception);
        }
    }

    @Transactional
    public User removeRestrictedCategory(String username, String category) {
        User user = getProfile(username);
        user.getRestrictedCategories().removeIf(rc -> rc.getCategory().equalsIgnoreCase(category));

        try {
            return userRepository.save(user);
        } catch (DataAccessException exception) {
            throw new DatabaseOperationException("Failed to save user in database", exception);
        }
    }
}
