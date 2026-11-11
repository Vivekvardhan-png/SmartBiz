package com.nexusops.platform.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a business operation would violate uniqueness or state constraints.
 * Maps to HTTP 409 Conflict.
 *
 * <p>Examples: duplicate customer email, duplicate invoice generation,
 * illegal status transition.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

