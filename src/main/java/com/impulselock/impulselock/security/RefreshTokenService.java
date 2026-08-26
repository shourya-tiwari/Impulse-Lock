package com.impulselock.impulselock.security;

import com.impulselock.impulselock.entity.RefreshToken;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issue/rotate/revoke opaque refresh tokens (see docs/v2/security-design.md#token-lifecycle).
 * Only the SHA-256 hash of a token is ever persisted - the raw value exists solely in the
 * httpOnly cookie handed back to the client.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int RAW_TOKEN_BYTES = 64;

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenTtlDays;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                                @Value("${app.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    @Transactional
    public String issue(User user) {
        String rawToken = generateRawToken();
        RefreshToken refreshToken = new RefreshToken(user, hash(rawToken), LocalDateTime.now().plusDays(refreshTokenTtlDays));
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    /**
     * Validates the raw refresh token, revokes it, and issues a fresh one for the same user -
     * rotation limits a leaked refresh token to a single use. Empty if the token is unknown,
     * already revoked, or expired.
     */
    @Transactional
    public Optional<RotatedToken> rotate(String rawToken) {
        return refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> token.getRevokedAt() == null)
                .filter(token -> token.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(token -> {
                    token.revoke();
                    User user = token.getUser();
                    return new RotatedToken(user, issue(user));
                });
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(RefreshToken::revoke);
    }

    @Transactional
    public void revokeAllForUser(User user) {
        refreshTokenRepository.findAllByUserAndRevokedAtIsNull(user).forEach(RefreshToken::revoke);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record RotatedToken(User user, String rawToken) {
    }
}
