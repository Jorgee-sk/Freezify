package com.freezify.mealplanning;

import java.util.UUID;

/** Published after a meal plan changes, for modules that react to it (statistics, open event streams). */
public final class MealPlanEvents {

    private MealPlanEvents() {}

    /** Anything about what the household plans to eat changed. Published by every write. */
    public record MealPlanChanged(UUID householdId, UUID userId) {}

    /** A week that had nothing planned now has something. */
    public record MealPlanCreated(UUID householdId, UUID userId) {}
}
