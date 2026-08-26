package com.impulselock.impulselock.controller;

import com.impulselock.impulselock.dto.AuthResponse;
import com.impulselock.impulselock.dto.LoginRequest;
import com.impulselock.impulselock.dto.RegisterRequest;
import com.impulselock.impulselock.dto.UserProfileResponse;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.exception.InvalidRefreshTokenException;
import com.impulselock.impulselock.security.JwtService;
import com.impulselock.impulselock.security.LoginRateLimiter;
import com.impulselock.impulselock.security.RefreshTokenService;
import com.impulselock.impulselock.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * New in Phase 1 - placed directly under {@code /api/v2/auth} (the eventual V2 base path,
 * see docs/v2/api-design.md) since there is no legacy V1 auth endpoint to preserve.
 *
 * <p>Every method here clears the global {@code bearerAuth} requirement (see
 * {@code OpenApiConfig}) - none of these endpoints take an access token; {@code refresh}/
 * {@code logout} authenticate via the httpOnly refresh-token cookie instead, which isn't
 * represented as a bearer scheme.
 */
@Tag(name = "Auth", description = "Registration, login, and JWT/refresh-token lifecycle - see docs/v2/security-design.md")
@SecurityRequirements
@RestController
@RequestMapping("/api/v2/auth")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/v2/auth";

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;
    private final long refreshTokenTtlDays;

    public AuthController(AuthService authService,
                           RefreshTokenService refreshTokenService,
                           JwtService jwtService,
                           LoginRateLimiter loginRateLimiter,
                           @Value("${app.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.jwtService = jwtService;
        this.loginRateLimiter = loginRateLimiter;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    @Operation(summary = "Register a new account",
            description = "Always assigns ROLE_USER - there is no self-service way to become an "
                    + "admin (see docs/v2/security-design.md). Returns 409 if the username or email "
                    + "is already taken. Also sets the refresh-token cookie, like /login.")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return respondWithNewSession(authService.register(request));
    }

    @Operation(summary = "Log in with username and password",
            description = "Returns 401 with a deliberately generic message for any failure reason "
                    + "(wrong username, wrong password, or disabled account) to avoid leaking which case applied. "
                    + "Returns 429 after too many failed attempts for the same username in a short window "
                    + "(see LoginRateLimiter).")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        loginRateLimiter.checkAllowed(request.getUsername());
        try {
            User user = authService.login(request);
            loginRateLimiter.recordSuccess(request.getUsername());
            return respondWithNewSession(user);
        } catch (AuthenticationException exception) {
            loginRateLimiter.recordFailure(request.getUsername());
            throw exception;
        }
    }

    @Operation(summary = "Exchange the refresh-token cookie for a new access token",
            description = "Not explorable via Swagger UI's \"Try it out\" the normal way - the "
                    + "refresh token travels only as an httpOnly cookie set by /register or /login, "
                    + "never as a request parameter. Rotates the refresh token: the previous one is "
                    + "revoked and cannot be reused.")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token cookie is missing");
        }

        RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate(refreshToken)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid, expired, or revoked"));

        String accessToken = jwtService.generateAccessToken(rotated.user());
        AuthResponse body = new AuthResponse(accessToken, jwtService.getAccessTokenTtlSeconds(),
                new UserProfileResponse(rotated.user()));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(rotated.rawToken()).toString())
                .body(body);
    }

    @Operation(summary = "Revoke the current refresh token",
            description = "Clears the refresh-token cookie and revokes it server-side. Does not "
                    + "invalidate the caller's still-live access token (JWTs are stateless and expire "
                    + "on their own after app.jwt.access-token-ttl-minutes).")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revoke(refreshToken);
        }

        ResponseCookie cleared = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(0)
                .build();

        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cleared.toString()).build();
    }

    private ResponseEntity<AuthResponse> respondWithNewSession(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = refreshTokenService.issue(user);
        AuthResponse body = new AuthResponse(accessToken, jwtService.getAccessTokenTtlSeconds(), new UserProfileResponse(user));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(rawRefreshToken).toString())
                .body(body);
    }

    private ResponseCookie refreshTokenCookie(String rawRefreshToken) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(Duration.ofDays(refreshTokenTtlDays))
                .build();
    }
}
