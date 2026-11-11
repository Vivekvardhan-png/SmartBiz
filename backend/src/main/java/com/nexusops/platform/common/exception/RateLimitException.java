package com.nexusops.platform.common.exception;

/**
 * Thrown when an actor exceeds an API or AI assistant rate limit.
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }
}
