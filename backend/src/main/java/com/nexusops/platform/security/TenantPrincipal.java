package com.nexusops.platform.security;

import com.nexusops.platform.identity.OrganizationRole;
import com.nexusops.platform.identity.Permission;

import java.util.Set;
import java.util.UUID;

/**
 * Authenticated user and authoritative tenant membership bound to the current request.
 */
public record TenantPrincipal(
        UUID userId,
        String email,
        String displayName,
        UUID sessionId,
        UUID organizationId,
        String organizationName,
        String organizationSlug,
        OrganizationRole role,
        Set<Permission> permissions
) {

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }
}

