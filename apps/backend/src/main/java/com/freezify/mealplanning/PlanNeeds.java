package com.freezify.mealplanning;

import com.freezify.food.Quantity;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What the planned meals of a household lack, for modules that act on it (the shopping list). It is not scoped to
 * a user: the caller must have checked that whoever asks belongs to the household.
 */
public interface PlanNeeds {

    /**
     * What has to be bought to cook every meal planned from {@code from} to {@code to}, once the food at home has
     * been used, the food that expires first going first, and food past its date no longer counts. Meals before
     * today are history and take nothing; meals between today and {@code from} take their share first.
     *
     * <p>Food at home measured in a way that cannot be compared with a recipe (pieces against grams) is never
     * said to be lacking: whether it is enough is not known.
     *
     * @return one need per food and kind of measure, in its base unit (grams, milliliters, units)
     */
    List<Need> needs(UUID householdId, LocalDate from, LocalDate to);

    /**
     * @param firstNeededOn the day of the first meal that lacks it
     */
    record Need(UUID foodId, Quantity quantity, LocalDate firstNeededOn) {}
}
