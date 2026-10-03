package com.freezify.recipes;

import java.util.UUID;

/** Published when users do something with a recipe, for modules that react to it (statistics). */
public final class RecipeEvents {

    private RecipeEvents() {}

    public record RecipeViewed(UUID userId, UUID recipeId) {}

    public record RecipeCooked(UUID userId, UUID householdId, UUID recipeId) {}

    /** A language model wrote a recipe for the household with what it has. */
    public record RecipeGenerated(UUID userId, UUID householdId) {}
}
