package com.nexusops.platform.security;

import com.nexusops.platform.common.exception.AccessDeniedException;
import com.nexusops.platform.identity.Permission;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Provides server-verified tenant and permission accessors for services and controllers.
 */
@Component
public class TenantContext {

    public TenantPrincipal requirePrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new AccessDeniedException("Authenticated organization context is required.");
        }
        return principal;
    }

    public UUID currentOrganizationId() {
        return requirePrincipal().organizationId();
    }

    public UUID currentUserId() {
        return requirePrincipal().userId();
    }

    public void requirePermission(Permission permission) {
        TenantPrincipal principal = requirePrincipal();
        if (!principal.hasPermission(permission)) {
            throw new AccessDeniedException(
                    "Missing required permission: " + permission.name());
        }
    }
}

