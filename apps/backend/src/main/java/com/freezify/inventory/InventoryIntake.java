package com.freezify.inventory;

import com.freezify.food.FoodCategory;
import com.freezify.food.Quantity;
import com.freezify.food.StorageLocation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What other modules may put in the inventory of a household on behalf of a member (what was just bought). */
public interface InventoryIntake {

    /**
     * Adds food that was bought. Without an expiration date, its date is estimated from the shelf-life rules, as
     * for any item added without one, and is labelled as an estimate.
     *
     * @throws com.freezify.common.ApiException 404 {@code HOUSEHOLD_NOT_FOUND} when the user is not a member,
     *     400 {@code FOOD_NOT_FOUND} when the food is not in the catalog
     * @return the id of the new inventory item
     */
    UUID stock(UUID householdId, UUID userId, NewItem item);

    /**
     * @param foodId         the catalog food, when it is one
     * @param purchaseDate   when it was bought; {@code null} means today
     * @param expirationDate the date the person read on the package; {@code null} to estimate one
     * @param price          what was paid for it
     */
    record NewItem(
            @Nullable UUID foodId,
            String name,
            FoodCategory category,
            Quantity quantity,
            StorageLocation storageLocation,
            @Nullable LocalDate purchaseDate,
            @Nullable LocalDate expirationDate,
            @Nullable BigDecimal price) {

        /** Bought today, at an unknown price, with an estimated date. */
        public NewItem(
                @Nullable UUID foodId,
                String name,
                FoodCategory category,
                Quantity quantity,
                StorageLocation storageLocation) {
            this(foodId, name, category, quantity, storageLocation, null, null, null);
        }
    }
}
