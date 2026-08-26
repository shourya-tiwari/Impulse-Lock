package com.impulselock.impulselock.service;

import com.impulselock.impulselock.dto.UserUpsertRequest;
import com.impulselock.impulselock.entity.RestrictedCategory;
import com.impulselock.impulselock.entity.Role;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.exception.DatabaseOperationException;
import com.impulselock.impulselock.repository.RoleRepository;
import com.impulselock.impulselock.repository.UserRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transitional home for user-preference upserts while Phase 1 (real registration/login) doesn't
 * exist yet - see {@code UserUpsertRequest} and docs/v2/security-design.md. V1 had no service
 * layer here at all (UserController talked to the repository directly, see docs/v1/backend.md);
 * this class exists now because creating a user involves more than one repository call (hashing
 * a password, assigning a default role, seeding a default restricted category).
 */
@Service
public class UserService {

    private static final String DEFAULT_RESTRICTED_CATEGORY = "LUXURY";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User upsertUser(UserUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("User request body is required");
        }
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("username is required");
        }

        User user = userRepository.findByUsername(request.getUsername()).orElse(null);

        if (user == null) {
            user = createUser(request);
        } else {
            applyPreferences(user, request);
        }

        try {
            return userRepository.save(user);
        } catch (DataAccessException exception) {
            throw new DatabaseOperationException("Failed to save user in database", exception);
        }
    }

    private User createUser(UserUpsertRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("email is required to create a new user");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("password is required to create a new user");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("ROLE_USER is missing - seed migration did not run"));
        user.setRoles(new HashSet<>(List.of(userRole)));

        applyPreferences(user, request);

        List<String> categories = request.getRestrictedCategories();
        if (categories == null || categories.isEmpty()) {
            // Preserves V1's default behavior (see docs/v1/rule-engine.md#categoryrestrictionrule)
            // as an explicit, visible, editable row instead of an implicit rule-level fallback.
            user.getRestrictedCategories().add(new RestrictedCategory(user, DEFAULT_RESTRICTED_CATEGORY));
        }

        return user;
    }

    private void applyPreferences(User user, UserUpsertRequest request) {
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
    }
}
