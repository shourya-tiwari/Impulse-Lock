package com.impulselock.impulselock.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.impulselock.impulselock.entity.RestrictedCategory;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class UserRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndFindsByUsername() {
        userRepository.save(newUser("alice"));

        assertThat(userRepository.findByUsername("alice")).isPresent();
        assertThat(userRepository.existsByUsername("alice")).isTrue();
        assertThat(userRepository.existsByEmail("alice@example.com")).isTrue();
    }

    @Test
    void rejectsDuplicateUsername() {
        userRepository.saveAndFlush(newUser("bob"));

        User duplicate = newUser("bob");
        duplicate.setEmail("different@example.com");

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateEmail() {
        userRepository.saveAndFlush(newUser("carol"));

        User duplicate = newUser("different-username");
        duplicate.setEmail("carol@example.com");

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cascadesDeleteToRestrictedCategories() {
        User user = newUser("dave");
        user.getRestrictedCategories().add(new RestrictedCategory(user, "LUXURY"));
        userRepository.saveAndFlush(user);

        userRepository.delete(user);
        userRepository.flush();

        assertThat(userRepository.findByUsername("dave")).isEmpty();
    }

    @Test
    void populatesCreatedAtAndUpdatedAtViaAuditing() {
        User saved = userRepository.saveAndFlush(newUser("erin"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    private User newUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hashed");
        user.setDailyLimit(BigDecimal.valueOf(2000));
        user.setNightSpendingAllowed(false);
        user.setSensitivityLevel(5);
        return user;
    }
}
