package com.freezify.inventory;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Published after inventory changes, for modules that react to them (statistics, notifications). */
public final class InventoryEvents {

    private InventoryEvents() {}

    /**
     * Anything about the inventory of the household changed. Published by every write.
     *
     * @param userId who made the change; {@code null} when the application did (the expiration sweep)
     */
    public record InventoryChanged(UUID householdId, @Nullable UUID userId) {}

    public record FoodItemAdded(UUID householdId, UUID userId, UUID itemId) {}

    public record FoodItemConsumed(UUID householdId, UUID userId, UUID itemId) {}

    public record FoodItemDiscarded(UUID householdId, UUID userId, UUID itemId) {}
}
