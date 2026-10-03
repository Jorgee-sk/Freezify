package com.freezify.inventory;

import com.freezify.food.FoodCategory;
import com.freezify.food.Quantity;
import com.freezify.food.StorageLocation;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What other modules may put in the inventory of a household on behalf of a member (what was just bought). */
public interface InventoryIntake {

    /**
     * Adds food bought today. Its date is estimated from the shelf-life rules, as for any item added without one,
     * and is labelled as an estimate.
     *
     * @throws com.freezify.common.ApiException 404 {@code HOUSEHOLD_NOT_FOUND} when the user is not a member
     * @return the id of the new inventory item
     */
    UUID stock(UUID householdId, UUID userId, NewItem item);

    /**
     * @param foodId the catalog food, when it is one
     */
    record NewItem(
            @Nullable UUID foodId,
            String name,
            FoodCategory category,
            Quantity quantity,
            StorageLocation storageLocation) {}
}
