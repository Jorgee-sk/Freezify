package com.freezify.recipes.internal;

import com.freezify.expiration.ExpirationPriority;
import com.freezify.food.FoodTrait;
import com.freezify.food.Unit;
import com.freezify.recipes.internal.Recipe.Course;
import com.freezify.recipes.internal.Recipe.Difficulty;
import com.freezify.recipes.internal.RecipeScorer.Availability;
import com.freezify.recipes.internal.RecipeScorer.Factors;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Recipes as clients receive them, in the language they asked for. */
// "contains" lists what the recipe has that someone may not eat, so that clients can say so.
public final class RecipeViews {

    private RecipeViews() {}

    public record RecipeSummary(
            UUID id,
            String name,
            String description,
            int servings,
            int prepMinutes,
            int cookMinutes,
            int totalMinutes,
            Difficulty difficulty,
            Course course,
            List<FoodTrait> contains) {

        static RecipeSummary of(Recipe recipe, String language) {
            return new RecipeSummary(
                    recipe.id(),
                    recipe.name(language),
                    recipe.description(language),
                    recipe.servings(),
                    recipe.prepMinutes(),
                    recipe.cookMinutes(),
                    recipe.totalMinutes(),
                    recipe.difficulty(),
                    recipe.course(),
                    recipe.contains().stream().sorted().toList());
        }
    }

    public record IngredientView(UUID foodId, String name, BigDecimal amount, Unit unit, boolean staple) {}

    public record RecipeDetail(RecipeSummary recipe, List<IngredientView> ingredients, List<String> steps) {}

    /**
     * An ingredient of a recommended recipe, with what the household has of it. This is the data the explanation
     * of the recommendation is made of: nothing in it is invented.
     *
     * @param expirationDate      the earliest date of the household's stock of this food, if it has any with a date
     * @param estimated           whether that date is an estimate rather than the one the user gave
     * @param daysUntilExpiration counted from today
     */
    public record MatchedIngredient(
            UUID foodId,
            String name,
            BigDecimal amount,
            Unit unit,
            boolean staple,
            Availability availability,
            @Nullable LocalDate expirationDate,
            boolean estimated,
            @Nullable Long daysUntilExpiration,
            @Nullable ExpirationPriority priority) {}

    /**
     * @param score           between 0 and 1
     * @param factors         what the score is made of, each between 0 and 1
     * @param daysSinceCooked {@code null} when the household never cooked it
     */
    public record Recommendation(
            RecipeSummary recipe,
            double score,
            Factors factors,
            List<MatchedIngredient> ingredients,
            @Nullable Long daysSinceCooked) {}
}
