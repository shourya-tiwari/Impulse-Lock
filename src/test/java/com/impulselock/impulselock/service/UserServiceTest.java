package com.impulselock.impulselock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.impulselock.impulselock.dto.UserPreferencesUpdateRequest;
import com.impulselock.impulselock.entity.Role;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.exception.UserNotFoundException;
import com.impulselock.impulselock.repository.RoleRepository;
import com.impulselock.impulselock.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService newService() {
        return new UserService(userRepository, roleRepository, passwordEncoder);
    }

    @Test
    void registerNewUserHashesThePasswordAssignsRoleUserAndDefaultRestrictedCategory() {
        UserService service = newService();
        when(passwordEncoder.encode("plaintext")).thenReturn("hashed");
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(new Role("ROLE_USER")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = service.registerNewUser("alice", "alice@example.com", "plaintext");

        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getRoles()).extracting(Role::getName).containsExactly("ROLE_USER");
        assertThat(saved.getRestrictedCategoryNames()).containsExactly("LUXURY");
    }

    @Test
    void registerNewUserFailsFastWhenTheSeedRoleIsMissing() {
        UserService service = newService();
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerNewUser("alice", "alice@example.com", "plaintext"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ROLE_USER");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updatePreferencesAppliesAllThreeFields() {
        UserService service = newService();
        User existing = new User();
        existing.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserPreferencesUpdateRequest request = new UserPreferencesUpdateRequest();
        request.setDailyLimit(BigDecimal.valueOf(3000));
        request.setNightSpendingAllowed(true);
        request.setSensitivityLevel(8);

        User updated = service.updatePreferences("alice", request);

        assertThat(updated.getDailyLimit()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        assertThat(updated.isNightSpendingAllowed()).isTrue();
        assertThat(updated.getSensitivityLevel()).isEqualTo(8);
    }

    @Test
    void updatePreferencesRejectsANullRequest() {
        UserService service = newService();
        assertThatThrownBy(() -> service.updatePreferences("alice", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updatePreferencesThrowsWhenTheUserDoesNotExist() {
        UserService service = newService();
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePreferences("ghost", new UserPreferencesUpdateRequest()))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getProfileThrowsWhenTheUserDoesNotExist() {
        UserService service = newService();
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile("ghost")).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void addRestrictedCategoryRejectsABlankCategory() {
        UserService service = newService();
        assertThatThrownBy(() -> service.addRestrictedCategory("alice", "  "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).findByUsername(anyString());
    }

    @Test
    void addRestrictedCategoryIsCaseInsensitivelyIdempotent() {
        UserService service = newService();
        User existing = new User();
        existing.setUsername("alice");
        existing.getRestrictedCategories().add(new com.impulselock.impulselock.entity.RestrictedCategory(existing, "Luxury"));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = service.addRestrictedCategory("alice", "LUXURY");

        assertThat(updated.getRestrictedCategoryNames()).containsExactly("Luxury");
    }

    @Test
    void addRestrictedCategoryAddsANewOne() {
        UserService service = newService();
        User existing = new User();
        existing.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = service.addRestrictedCategory("alice", " gaming ");

        assertThat(updated.getRestrictedCategoryNames()).containsExactly("gaming");
    }

    @Test
    void removeRestrictedCategoryIsCaseInsensitive() {
        UserService service = newService();
        User existing = new User();
        existing.setUsername("alice");
        existing.getRestrictedCategories().add(new com.impulselock.impulselock.entity.RestrictedCategory(existing, "Luxury"));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = service.removeRestrictedCategory("alice", "luxury");

        assertThat(updated.getRestrictedCategoryNames()).isEmpty();
    }
}
