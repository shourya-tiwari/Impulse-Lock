package com.impulselock.impulselock.security;

import com.impulselock.impulselock.exception.TooManyLoginAttemptsException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * In-memory, per-username fixed-window brute-force guard for {@code POST /auth/login} - the
 * "rate limiting / brute-force lockout on /auth/login" item docs/v2/security-design.md left as a
 * decision to make once deployment shape was settled (see
 * docs/v2/security-design.md#whats-explicitly-out-of-scope-for-v2). Phase 6 settled on a single
 * backend instance with no shared cache, so an in-memory map is sufficient here - a
 * Redis-backed version would only be needed if the backend were ever horizontally scaled.
 *
 * <p>Keyed by username alone, not per-IP: this stops credential stuffing against one account
 * regardless of how many source IPs an attacker rotates through, and avoids the false-positive
 * risk of IP-based limiting locking out an entire NAT/office network after a few genuine typos.
 * Usernames are case-folded so {@code Alice}/{@code alice} share one bucket, matching
 * {@code UserRepository}'s username lookup.
 */
@Component
public class LoginRateLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final Map<String, Window> attemptsByUsername = new ConcurrentHashMap<>();

    public LoginRateLimiter(@Value("${app.security.login-rate-limit.max-attempts:5}") int maxAttempts,
                             @Value("${app.security.login-rate-limit.window-minutes:15}") long windowMinutes) {
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    /** @throws TooManyLoginAttemptsException if the username is currently locked out. */
    public void checkAllowed(String username) {
        Window current = attemptsByUsername.get(normalize(username));
        if (current != null && !current.isExpired(window) && current.count.get() >= maxAttempts) {
            throw new TooManyLoginAttemptsException(
                    "Too many failed login attempts. Try again in a few minutes.");
        }
    }

    public void recordFailure(String username) {
        String key = normalize(username);
        attemptsByUsername.compute(key, (k, existing) -> {
            if (existing == null || existing.isExpired(window)) {
                return new Window();
            }
            existing.count.incrementAndGet();
            return existing;
        });
    }

    public void recordSuccess(String username) {
        attemptsByUsername.remove(normalize(username));
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Window {
        private final Instant windowStart = Instant.now();
        private final AtomicInteger count = new AtomicInteger(1);

        boolean isExpired(Duration window) {
            // !isBefore (i.e. "at or after"), not isAfter: a zero-minute window must count as
            // expired immediately, but two back-to-back Instant.now() calls can return the exact
            // same instant on a coarse system clock, which isAfter() (strict >) would treat as
            // "not yet expired".
            return !Instant.now().isBefore(windowStart.plus(window));
        }
    }
}
