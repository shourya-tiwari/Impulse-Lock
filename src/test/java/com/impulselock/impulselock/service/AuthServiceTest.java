package com.impulselock.impulselock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.impulselock.impulselock.dto.LoginRequest;
import com.impulselock.impulselock.dto.RegisterRequest;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.exception.UserAlreadyExistsException;
import com.impulselock.impulselock.repository.UserRepository;
import com.impulselock.impulselock.security.SecurityUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private Authentication authentication;

    private AuthService newService() {
        return new AuthService(userService, userRepository, authenticationManager);
    }

    private RegisterRequest registerRequest(String username, String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    @Test
    void registerRejectsATakenUsernameBeforeCheckingEmail() {
        AuthService service = newService();
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> service.register(registerRequest("alice", "alice@example.com", "password123")))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("alice");
        verify(userRepository, never()).existsByEmail(any());
        verify(userService, never()).registerNewUser(any(), any(), any());
    }

    @Test
    void registerRejectsATakenEmail() {
        AuthService service = newService();
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(registerRequest("alice", "alice@example.com", "password123")))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("alice@example.com");
        verify(userService, never()).registerNewUser(any(), any(), any());
    }

    @Test
    void registerDelegatesToUserServiceWhenBothAreFree() {
        AuthService service = newService();
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        User created = new User();
        when(userService.registerNewUser("alice", "alice@example.com", "password123")).thenReturn(created);

        User result = service.register(registerRequest("alice", "alice@example.com", "password123"));

        assertThat(result).isSameAs(created);
    }

    @Test
    void loginDelegatesToTheAuthenticationManagerAndUnwrapsTheSecurityUser() {
        AuthService service = newService();
        User user = new User();
        user.setUsername("alice");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(new SecurityUser(user));

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("password123");

        User result = service.login(request);

        assertThat(result).isSameAs(user);
    }
}
