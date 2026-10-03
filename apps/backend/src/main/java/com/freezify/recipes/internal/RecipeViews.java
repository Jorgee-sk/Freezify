package com.freezify.recipes.internal;

import com.freezify.recipes.RecipeScorer;
import com.freezify.recipes.Recipe;
import com.freezify.expiration.ExpirationPriority;
import com.freezify.food.FoodTrait;
import com.freezify.food.Unit;
import com.freezify.recipes.Recipe.Course;
import com.freezify.recipes.Recipe.Difficulty;
import com.freezify.recipes.RecipeScorer.Availability;
import com.freezify.recipes.RecipeScorer.Factors;
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

    /**
     * Food a generated recipe may use.
     *
     * @param foodId   the catalog food, when it is one: only those can be required
     * @param daysLeft days until the earliest date of that food at home, when it has one
     */
    public record AvailableFood(
            @Nullable UUID foodId, String name, BigDecimal amount, Unit unit, @Nullable Long daysLeft, boolean estimated) {}

    /**
     * A recipe a language model wrote with what the household has. Not part of the catalog and not stored.
     *
     * @param ingredients the staples last, and among the rest what the recipe needs before what is optional
     */
    public record GeneratedRecipe(
            String title,
            String summary,
            int servings,
            int minutes,
            Recipe.Difficulty difficulty,
            List<GeneratedIngredient> ingredients,
            List<String> steps) {}

    /**
     * An ingredient of a generated recipe, with the date of what the household has of it: the real reason it was
     * used, which the model only saw as a number of days.
     *
     * @param foodId    the catalog food, when it is one
     * @param staple    salt, oil or water, assumed in any kitchen
     * @param daysLeft  days until the earliest date of that food at home, when it has one
     * @param estimated whether that date is an estimate
     */
    public record GeneratedIngredient(
            @Nullable UUID foodId,
            String name,
            BigDecimal amount,
            Unit unit,
            boolean optional,
            boolean staple,
            @Nullable Long daysLeft,
            @Nullable ExpirationPriority priority,
            boolean estimated) {}
}
