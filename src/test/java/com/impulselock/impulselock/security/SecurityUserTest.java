package com.impulselock.impulselock.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.impulselock.impulselock.entity.Role;
import com.impulselock.impulselock.entity.User;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class SecurityUserTest {

    private User user(boolean enabled, String... roleNames) {
        User user = new User();
        user.setUsername("alice");
        user.setPasswordHash("hashed");
        user.setEnabled(enabled);
        Set<Role> roles = new HashSet<>();
        for (String name : roleNames) {
            roles.add(new Role(name));
        }
        user.setRoles(roles);
        return user;
    }

    @Test
    void delegatesTheBasicUserDetailsFieldsToTheUnderlyingUser() {
        SecurityUser securityUser = new SecurityUser(user(true, "ROLE_USER"));

        assertThat(securityUser.getUsername()).isEqualTo("alice");
        assertThat(securityUser.getPassword()).isEqualTo("hashed");
        assertThat(securityUser.isEnabled()).isTrue();
        assertThat(securityUser.isAccountNonExpired()).isTrue();
        assertThat(securityUser.isAccountNonLocked()).isTrue();
        assertThat(securityUser.isCredentialsNonExpired()).isTrue();
        assertThat(securityUser.getUser()).isNotNull();
    }

    @Test
    void aDisabledUserIsReportedAsDisabled() {
        SecurityUser securityUser = new SecurityUser(user(false, "ROLE_USER"));
        assertThat(securityUser.isEnabled()).isFalse();
    }

    @Test
    void authoritiesMirrorTheUsersRoleNamesVerbatim() {
        SecurityUser securityUser = new SecurityUser(user(true, "ROLE_USER", "ROLE_ADMIN"));

        assertThat(securityUser.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void isAdminIsTrueOnlyWithTheAdminRole() {
        assertThat(new SecurityUser(user(true, "ROLE_USER")).isAdmin()).isFalse();
        assertThat(new SecurityUser(user(true, "ROLE_USER", "ROLE_ADMIN")).isAdmin()).isTrue();
    }
}
