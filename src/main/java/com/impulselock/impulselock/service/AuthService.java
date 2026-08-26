package com.impulselock.impulselock.service;

import com.impulselock.impulselock.audit.Auditable;
import com.impulselock.impulselock.dto.LoginRequest;
import com.impulselock.impulselock.dto.RegisterRequest;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.exception.UserAlreadyExistsException;
import com.impulselock.impulselock.repository.UserRepository;
import com.impulselock.impulselock.security.SecurityUser;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration always assigns exactly ROLE_USER (see {@code UserService.registerNewUser}) -
 * there is no API path to self-assign ROLE_ADMIN (see docs/v2/security-design.md).
 *
 * <p>Phase 4 cleanup: the manual null/blank checks on {@code RegisterRequest}/{@code LoginRequest}
 * fields are gone - {@code @Valid} on {@code AuthController} now guarantees non-null, non-blank
 * fields before either method here is ever called, so re-checking was redundant (see the
 * project's "trust internal code and framework guarantees" principle).
 */
@Service
public class AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserService userService, UserRepository userRepository, AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
    }

    @Auditable(action = "USER_REGISTERED", entityType = "USER")
    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("Username is already taken: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email is already registered: " + request.getEmail());
        }

        return userService.registerNewUser(request.getUsername(), request.getEmail(), request.getPassword());
    }

    @Auditable(action = "LOGIN_SUCCESS", entityType = "USER", failureAction = "LOGIN_FAILURE")
    public User login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        return ((SecurityUser) authentication.getPrincipal()).getUser();
    }
}
