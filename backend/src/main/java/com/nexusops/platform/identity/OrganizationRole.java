package com.nexusops.platform.identity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Organization-scoped roles matching the database CHECK constraint on
 * {@code organization_members.role}.
 */
public enum OrganizationRole {

    OWNER(EnumSet.allOf(Permission.class)),

    ADMIN(EnumSet.allOf(Permission.class)),

    MANAGER(EnumSet.of(
            Permission.MEMBERS_READ,
            Permission.CRM_READ,
            Permission.CRM_WRITE,
            Permission.INVENTORY_READ,
            Permission.INVENTORY_WRITE,
            Permission.FINANCE_READ,
            Permission.FINANCE_WRITE,
            Permission.INSIGHTS_READ,
            Permission.AI_USE
    )),

    EMPLOYEE(EnumSet.of(
            Permission.CRM_READ,
            Permission.CRM_WRITE,
            Permission.INVENTORY_READ,
            Permission.INVENTORY_WRITE
    ));

    private final Set<Permission> permissions;

    OrganizationRole(Set<Permission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }
}
