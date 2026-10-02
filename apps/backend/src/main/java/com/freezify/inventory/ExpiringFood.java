package com.freezify.inventory;

import com.freezify.expiration.ExpirationSource;
import com.freezify.food.FoodCategory;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What other modules may ask about food that is running out of time. */
public interface ExpiringFood {

    /**
     * Food still in the house, in every household, whose date is on or before {@code until}; food already past
     * its date is included. Meant for scheduled jobs: it is not scoped to a user, so nothing it returns may
     * reach a client without checking membership first.
     */
    List<ExpiringItem> until(LocalDate until);

    record ExpiringItem(
            UUID householdId,
            UUID itemId,
            String name,
            FoodCategory category,
            LocalDate expirationDate,
            ExpirationSource expirationSource) {

        public boolean estimated() {
            return expirationSource == ExpirationSource.ESTIMATED;
        }
    }
}
