package com.freezify.households;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who lives where, for modules that act on behalf of the application rather than of a user (scheduled jobs).
 * Requests coming from a user go through {@link HouseholdAccess} instead.
 */
public interface HouseholdDirectory {

    List<UUID> memberIds(UUID householdId);

    /** Households that no longer exist are absent from the result. */
    Map<UUID, String> names(Collection<UUID> householdIds);
}
