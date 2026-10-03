package com.freezify.shopping;

import java.util.UUID;

/** Published after the shopping list changes, for modules that react to it (statistics, open event streams). */
public final class ShoppingEvents {

    private ShoppingEvents() {}

    /** Anything on the shopping list of the household changed. Published by every write. */
    public record ShoppingListChanged(UUID householdId, UUID userId) {}

    /** The list was filled from the meal plan while it was empty. */
    public record ShoppingListCreated(UUID householdId, UUID userId) {}
}
