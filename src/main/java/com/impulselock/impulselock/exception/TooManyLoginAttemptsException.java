package com.impulselock.impulselock.exception;

/** Thrown by {@link com.impulselock.impulselock.security.LoginRateLimiter} - mapped to 429. */
public class TooManyLoginAttemptsException extends RuntimeException {

    public TooManyLoginAttemptsException(String message) {
        super(message);
    }
}
