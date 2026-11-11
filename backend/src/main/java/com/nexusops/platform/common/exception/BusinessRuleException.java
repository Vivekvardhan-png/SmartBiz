package com.nexusops.platform.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a business rule is violated that does not map to a validation or conflict error.
 * Maps to HTTP 422 Unprocessable Entity.
 *
 * <p>Examples: attempting to convert an already-converted lead,
 * generating a second invoice for an order that already has one.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}

