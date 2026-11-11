package com.nexusops.platform.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a user lacks permission to perform an action on an organization-owned resource.
 * Maps to HTTP 403 Forbidden.
 *
 * <p>Use this for business-level authorization failures (e.g., accessing another
 * organization's record). Spring Security handles authentication-level 401 rejections
 * automatically via the configured entry point (Prompt 02).
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }

    public AccessDeniedException() {
        super("Access denied: insufficient permissions for this operation.");
    }
}

