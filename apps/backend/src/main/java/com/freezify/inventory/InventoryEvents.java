package com.freezify.inventory;

import java.util.UUID;

/** Published after inventory changes, for modules that react to them (statistics, notifications). */
public final class InventoryEvents {

    private InventoryEvents() {}

    public record FoodItemAdded(UUID householdId, UUID userId, UUID itemId) {}

    public record FoodItemConsumed(UUID householdId, UUID userId, UUID itemId) {}

    public record FoodItemDiscarded(UUID householdId, UUID userId, UUID itemId) {}
}
