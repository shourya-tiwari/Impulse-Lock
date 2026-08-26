package com.impulselock.impulselock.dto;

/**
 * Bean Validation annotations arrive in Phase 4 (see docs/v2/tasks.md, Phase 1's own note:
 * "the DTO classes are created here" - validation is deferred deliberately, not forgotten).
 */
public class RegisterRequest {

    private String username;
    private String email;
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
