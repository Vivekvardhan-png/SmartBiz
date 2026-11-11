package com.nexusops.platform.crm;

import java.util.EnumSet;
import java.util.Set;

/**
 * Pipeline stages for CRM leads and their allowed manual transitions.
 */
public enum LeadStatus {
    NEW,
    CONTACTED,
    QUALIFIED,
    PROPOSAL,
    CONVERTED,
    LOST;

    public static final Set<LeadStatus> OPEN_STATUSES = EnumSet.of(
            NEW, CONTACTED, QUALIFIED, PROPOSAL
    );

    public boolean isOpen() {
        return OPEN_STATUSES.contains(this);
    }

    public Set<LeadStatus> allowedManualTransitions() {
        return switch (this) {
            case NEW -> EnumSet.of(CONTACTED, QUALIFIED, PROPOSAL, LOST);
            case CONTACTED -> EnumSet.of(QUALIFIED, PROPOSAL, LOST);
            case QUALIFIED -> EnumSet.of(PROPOSAL, LOST);
            case PROPOSAL -> EnumSet.of(QUALIFIED, LOST);
            case LOST -> EnumSet.of(CONTACTED, QUALIFIED);
            case CONVERTED -> EnumSet.noneOf(LeadStatus.class);
        };
    }

    public boolean canManuallyTransitionTo(LeadStatus target) {
        return this == target || allowedManualTransitions().contains(target);
    }
}

