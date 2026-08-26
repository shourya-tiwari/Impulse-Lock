package com.impulselock.impulselock.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class SecurityUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void loadsAKnownUsernameAsASecurityUser() {
        SecurityUserDetailsService service = new SecurityUserDetailsService(userRepository);
        User user = new User();
        user.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("alice");

        assertThat(result).isInstanceOf(SecurityUser.class);
        assertThat(result.getUsername()).isEqualTo("alice");
    }

    @Test
    void throwsForAnUnknownUsername() {
        SecurityUserDetailsService service = new SecurityUserDetailsService(userRepository);
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
