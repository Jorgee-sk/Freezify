package com.freezify.expiration;

import com.freezify.food.FoodCategory;
import com.freezify.food.StorageLocation;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Decides by which date a food should be eaten: the date the user gave, or an estimate from shelf-life rules
 * when there is none or when opening the food shortens its life.
 */
public interface ExpirationEstimator {

    /** Empty when the user gave no date and no rule applies: the food simply has no known date. */
    Optional<Expiration> resolve(Input input);

    /**
     * @param foodId     the catalog food, when the item is one
     * @param userDate   the date the user read on the package or decided, if any
     * @param openedDate when the food was opened, if it was
     */
    record Input(
            @Nullable UUID foodId,
            FoodCategory category,
            StorageLocation storageLocation,
            LocalDate purchaseDate,
            @Nullable LocalDate openedDate,
            @Nullable LocalDate userDate) {}

    /** A date together with where it comes from, so an estimate can never pass for a real date. */
    record Expiration(LocalDate date, ExpirationSource source) {}
}
