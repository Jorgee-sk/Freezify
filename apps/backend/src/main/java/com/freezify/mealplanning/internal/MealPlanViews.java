package com.freezify.mealplanning.internal;

import com.freezify.food.FoodTrait;
import com.freezify.food.Unit;
import com.freezify.recipes.Recipe.Difficulty;
import com.freezify.recipes.RecipeScorer.Availability;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A meal plan as clients receive it, in the language they asked for. */
public final class MealPlanViews {

    private MealPlanViews() {}

    /**
     * A week of a household, Monday to Sunday.
     *
     * @param today          the day the plan was looked at: meals before it are history
     * @param unusedExpiring what is at home, expires before the week is over and the meals planned up to its
     *                       date do not use, or do not use up
     */
    public record MealPlanView(
            LocalDate weekStart,
            LocalDate weekEnd,
            LocalDate today,
            List<PlannedMeal> meals,
            List<UnusedFood> unusedExpiring) {}

    /**
     * @param ingredients what the recipe needs and what the household would have of each on that day, once the
     *                    meals planned before it have taken theirs. Empty for a meal in the past.
     * @param cooked      whether the household said it cooked that recipe on that day
     */
    public record PlannedMeal(
            UUID id,
            LocalDate date,
            MealSlot slot,
            MealOrigin origin,
            PlannedRecipe recipe,
            List<PlannedIngredient> ingredients,
            boolean cooked) {}

    public record PlannedRecipe(
            UUID id, String name, int servings, int totalMinutes, Difficulty difficulty, List<FoodTrait> contains) {}

    /**
     * @param expirationDate the earliest date of what the household would have of this food on the day of the
     *                       meal, if any of it has a date
     * @param estimated      whether that date is an estimate rather than the one the user gave
     */
    public record PlannedIngredient(
            UUID foodId,
            String name,
            BigDecimal amount,
            Unit unit,
            boolean staple,
            Availability availability,
            @Nullable LocalDate expirationDate,
            boolean estimated) {}

    /**
     * @param name      as the household wrote it in its inventory
     * @param amount    how much of it would be left on its date
     * @param estimated whether the date is an estimate rather than the one the user gave
     */
    public record UnusedFood(String name, BigDecimal amount, Unit unit, LocalDate expirationDate, boolean estimated) {}

    /**
     * @param filled   meals the generator chose a recipe for
     * @param unfilled meals it left empty, for want of recipes that were not already in the plan
     */
    public record Generated(int filled, int unfilled) {}
}
