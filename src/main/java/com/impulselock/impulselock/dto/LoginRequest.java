package com.impulselock.impulselock.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Only presence is validated here (@NotBlank) - a missing username/password is a malformed
 * request (400), distinct from a wrong username/password combination (401, handled by
 * AuthenticationException -> GlobalExceptionHandler with a deliberately generic message). This
 * distinction doesn't leak whether an account exists, so it's not a security concern.
 */
public class LoginRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
