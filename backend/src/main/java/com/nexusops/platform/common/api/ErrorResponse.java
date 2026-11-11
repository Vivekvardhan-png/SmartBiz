package com.nexusops.platform.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

/**
 * Standard error envelope returned by {@link com.nexusops.platform.common.exception.GlobalExceptionHandler}
 * for all 4xx and 5xx responses.
 *
 * <pre>
 * {
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "Validation failed for 2 field(s).",
 *   "fieldErrors": [ { "field": "email", "message": "must not be blank" } ],
 *   "timestamp": "2026-11-11T13:00:00Z"
 * }
 * </pre>
 *
 * <p>Stack traces, internal exception classes, and sensitive system details are
 * never included in this response body.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String error,
        String message,
        List<FieldError> fieldErrors,
        Instant timestamp
) {

    /**
     * Individual field validation error within a request body or query parameter.
     */
    public record FieldError(String field, String message) {}

    public static ErrorResponse of(HttpStatus httpStatus, String message) {
        return new ErrorResponse(
                httpStatus.value(),
                httpStatus.getReasonPhrase(),
                message,
                null,
                Instant.now()
        );
    }

    public static ErrorResponse withFieldErrors(HttpStatus httpStatus, String message,
                                                List<FieldError> fieldErrors) {
        return new ErrorResponse(
                httpStatus.value(),
                httpStatus.getReasonPhrase(),
                message,
                fieldErrors,
                Instant.now()
        );
    }
}

