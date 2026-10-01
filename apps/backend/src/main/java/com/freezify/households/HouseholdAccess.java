package com.freezify.households;

import java.util.UUID;

/**
 * The single gate to household data. Every module that reads or writes anything belonging to a household
 * calls this first; an id taken from a request is never trusted on its own.
 */
public interface HouseholdAccess {

    /**
     * @return the role of the user in the household
     * @throws com.freezify.common.ApiException 404 {@code HOUSEHOLD_NOT_FOUND} when the household does not exist
     *     or the user is not a member. Both cases are indistinguishable so that ids cannot be probed.
     */
    HouseholdRole requireMember(UUID householdId, UUID userId);

    /**
     * @throws com.freezify.common.ApiException 404 as in {@link #requireMember}, or 403 {@code NOT_HOUSEHOLD_OWNER}
     *     when the user is a member without the owner role
     */
    void requireOwner(UUID householdId, UUID userId);
}
