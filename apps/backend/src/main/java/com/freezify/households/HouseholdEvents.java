package com.freezify.households;

import java.util.UUID;

/** Published after membership changes, for modules that hold per-member state (open event streams). */
public final class HouseholdEvents {

    private HouseholdEvents() {}

    /** The user left or was removed. */
    public record MemberRemoved(UUID householdId, UUID userId) {}

    public record HouseholdDeleted(UUID householdId) {}
}
