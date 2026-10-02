package com.freezify.inventory;

import com.freezify.expiration.ExpirationSource;
import com.freezify.food.Quantity;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What other modules may ask about the food a household has. */
public interface HouseholdStock {

    /**
     * Everything still in the house, including food past its date. It is not scoped to a user: the caller must
     * have checked that whoever asks belongs to the household.
     */
    List<StockItem> of(UUID householdId);

    /**
     * @param foodId the catalog food, when the item was added from the catalog
     */
    record StockItem(
            UUID itemId,
            @Nullable UUID foodId,
            String name,
            Quantity quantity,
            @Nullable LocalDate expirationDate,
            @Nullable ExpirationSource expirationSource) {

        public boolean estimated() {
            return expirationSource == ExpirationSource.ESTIMATED;
        }
    }
}
