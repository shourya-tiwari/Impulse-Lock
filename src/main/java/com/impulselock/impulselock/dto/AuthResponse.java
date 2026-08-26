package com.impulselock.impulselock.dto;

/**
 * Returned by {@code /auth/register}, {@code /auth/login}, and {@code /auth/refresh}. The
 * refresh token itself is never in this body - it travels only as an httpOnly cookie (see
 * docs/v2/security-design.md#why-the-access-token-isnt-also-a-cookie).
 */
public class AuthResponse {

    private final String accessToken;
    private final String tokenType = "Bearer";
    private final long expiresInSeconds;
    private final UserProfileResponse user;

    public AuthResponse(String accessToken, long expiresInSeconds, UserProfileResponse user) {
        this.accessToken = accessToken;
        this.expiresInSeconds = expiresInSeconds;
        this.user = user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public UserProfileResponse getUser() {
        return user;
    }
}
