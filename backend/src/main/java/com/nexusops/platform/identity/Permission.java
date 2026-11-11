package com.nexusops.platform.identity;

/**
 * Explicit fine-grained permissions enforced across API endpoints and services.
 */
public enum Permission {
    ORG_MANAGE,
    MEMBERS_READ,
    MEMBERS_MANAGE,
    CRM_READ,
    CRM_WRITE,
    INVENTORY_READ,
    INVENTORY_WRITE,
    FINANCE_READ,
    FINANCE_WRITE,
    INSIGHTS_READ,
    AI_USE
}

