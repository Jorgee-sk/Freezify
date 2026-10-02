package com.freezify.recipes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What other modules may ask about recipes (the meal planner). None of it is scoped to a user: the caller must
 * have checked that whoever asks belongs to the household.
 */
public interface RecipeCatalog {

    Optional<Recipe> find(UUID recipeId);

    /** Every recipe without anything the household does not eat, in the order of their Spanish name. */
    List<Recipe> eatenBy(UUID householdId);

    /** The last day the household cooked each recipe it ever cooked. */
    Map<UUID, LocalDate> lastCooked(UUID householdId);
}
