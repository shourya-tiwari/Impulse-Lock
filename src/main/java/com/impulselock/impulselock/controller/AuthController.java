package com.impulselock.impulselock.controller;

import com.impulselock.impulselock.dto.AuthResponse;
import com.impulselock.impulselock.dto.LoginRequest;
import com.impulselock.impulselock.dto.RegisterRequest;
import com.impulselock.impulselock.dto.UserProfileResponse;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.exception.InvalidRefreshTokenException;
import com.impulselock.impulselock.security.JwtService;
import com.impulselock.impulselock.security.RefreshTokenService;
import com.impulselock.impulselock.service.AuthService;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * New in Phase 1 - placed directly under {@code /api/v2/auth} (the eventual V2 base path,
 * see docs/v2/api-design.md) since there is no legacy V1 auth endpoint to preserve. The
 * still-transitional {@code /transaction} and {@code /users} endpoints stay at their existing
 * paths until Phase 3's full API redesign moves them under {@code /api/v2} too.
 */
@RestController
@RequestMapping("/api/v2/auth")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/v2/auth";

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final long refreshTokenTtlDays;

    public AuthController(AuthService authService,
                           RefreshTokenService refreshTokenService,
                           JwtService jwtService,
                           @Value("${app.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.jwtService = jwtService;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return respondWithNewSession(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return respondWithNewSession(authService.login(request));
    }

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
