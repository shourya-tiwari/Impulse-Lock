package com.impulselock.impulselock.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.impulselock.impulselock.exception.TooManyLoginAttemptsException;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    @Test
    void allowsAttemptsBelowTheThreshold() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, 15);

        assertThatCode(() -> limiter.checkAllowed("alice")).doesNotThrowAnyException();
        limiter.recordFailure("alice");
        assertThatCode(() -> limiter.checkAllowed("alice")).doesNotThrowAnyException();
        limiter.recordFailure("alice");
        assertThatCode(() -> limiter.checkAllowed("alice")).doesNotThrowAnyException();
    }

    @Test
    void locksOutAfterReachingMaxAttempts() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, 15);

        limiter.recordFailure("alice");
        limiter.recordFailure("alice");
        limiter.recordFailure("alice");

        assertThatThrownBy(() -> limiter.checkAllowed("alice"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void aSuccessfulLoginResetsTheCounter() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, 15);

        limiter.recordFailure("alice");
        limiter.recordFailure("alice");
        limiter.recordFailure("alice");
        limiter.recordSuccess("alice");

        assertThatCode(() -> limiter.checkAllowed("alice")).doesNotThrowAnyException();
    }

    @Test
    void usernamesAreCaseAndWhitespaceNormalized() {
        LoginRateLimiter limiter = new LoginRateLimiter(2, 15);

        limiter.recordFailure(" Alice ");
        limiter.recordFailure("alice");

        assertThatThrownBy(() -> limiter.checkAllowed("ALICE"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void lockoutIsPerUsername() {
        LoginRateLimiter limiter = new LoginRateLimiter(1, 15);

        limiter.recordFailure("alice");

        assertThatThrownBy(() -> limiter.checkAllowed("alice")).isInstanceOf(TooManyLoginAttemptsException.class);
        assertThatCode(() -> limiter.checkAllowed("bob")).doesNotThrowAnyException();
    }

    @Test
    void anExpiredWindowIsTreatedAsAFreshStart() {
        // A zero-minute window expires immediately, so the very next check should never see the
        // prior failure as still counting - this exercises the fixed-window expiry path without
        // needing to sleep in the test.
        LoginRateLimiter limiter = new LoginRateLimiter(1, 0);

        limiter.recordFailure("alice");

        assertThatCode(() -> limiter.checkAllowed("alice")).doesNotThrowAnyException();
    }
}
