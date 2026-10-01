package com.freezify.inventory;

public enum ItemStatus {
    AVAILABLE,
    OPENED,
    /** Past its date but still in the house. Set by the expiration engine (phase 3). */
    EXPIRED,
    CONSUMED,
    DISCARDED;

    /** Whether the food is still physically in the household. */
    public boolean isActive() {
        return this == AVAILABLE || this == OPENED || this == EXPIRED;
    }
}
