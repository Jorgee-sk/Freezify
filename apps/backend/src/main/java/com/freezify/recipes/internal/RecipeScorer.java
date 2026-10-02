package com.freezify.recipes.internal;

import com.freezify.expiration.ExpirationPriority;
import com.freezify.food.Unit;
import com.freezify.recipes.internal.Recipe.Ingredient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Scores how well a recipe suits what a household has today. A pure function: the same recipe, stock, history
 * and day always give the same score, and every number in the result can be traced back to its inputs, which
 * is what the explanation shown to the user is built from.
 *
 * <pre>
 * score = Σ weight · factor / Σ weight        (every factor and the score are between 0 and 1)
 * </pre>
 */
final class RecipeScorer {

    /** A recipe this quick or quicker is as convenient as it gets; one this long or longer is not at all. */
    static final int QUICK_MINUTES = 15;

    static final int LONG_MINUTES = 90;
    /** After this many days a recipe feels new again. */
    static final int NOVELTY_DAYS = 14;

    /**
     * How much each factor counts. Only the proportions matter.
     */
    record Weights(double ingredientMatch, double expiryUrgency, double convenience, double novelty) {
        Weights {
            if (ingredientMatch < 0 || expiryUrgency < 0 || convenience < 0 || novelty < 0) {
                throw new IllegalArgumentException("Weights cannot be negative");
            }
            if (ingredientMatch + expiryUrgency + convenience + novelty <= 0) {
                throw new IllegalArgumentException("At least one weight must be positive");
            }
        }
    }

    /**
     * What a household has of one food that can still be eaten.
     *
     * @param amounts           how much, in the base unit of each dimension it is measured in
     * @param soonestExpiration the earliest date among those items; {@code null} when none has a date
     * @param soonestEstimated  whether that date is an estimate
     */
    record FoodStock(
            Map<Unit.Dimension, BigDecimal> amounts, @Nullable LocalDate soonestExpiration, boolean soonestEstimated) {}

    enum Availability {
        /** The household has at least what the recipe asks for. */
        ENOUGH,
        /** It has some, but less than the recipe asks for. */
        PARTIAL,
        /** It has some, measured in a way that cannot be compared with the recipe (pieces against grams). */
        UNKNOWN_QUANTITY,
        MISSING,
        /** A staple: assumed to be in the kitchen, not looked for in the inventory. */
        ASSUMED;

        boolean atHome() {
            return this == ENOUGH || this == PARTIAL || this == UNKNOWN_QUANTITY;
        }
    }

    /**
     * @param expirationDate the earliest date of the household's stock of this food, when it has any with a date
     */
    record IngredientMatch(
            Ingredient ingredient, Availability availability, @Nullable LocalDate expirationDate, boolean estimated) {}

    record Factors(double ingredientMatch, double expiryUrgency, double convenience, double novelty) {}

    /**
     * @param daysSinceCooked {@code null} when the household never cooked it
     */
    record Scored(
            Recipe recipe,
            double score,
            Factors factors,
            List<IngredientMatch> ingredients,
            @Nullable Long daysSinceCooked) {

        /** Whether the household has at least one of the ingredients it would have to look for. */
        boolean usesSomethingAtHome() {
            return ingredients.stream().anyMatch(match -> match.availability().atHome());
        }
    }

    private final Weights weights;

    RecipeScorer(Weights weights) {
        this.weights = weights;
    }

    /**
     * @param stock      what the household has, by food; food past its date must already be left out
     * @param lastCooked the last day the household cooked this recipe, if ever
     */
    Scored score(Recipe recipe, Map<UUID, FoodStock> stock, @Nullable LocalDate lastCooked, LocalDate today) {
        List<IngredientMatch> matches = new ArrayList<>();
        double matched = 0;
        int required = 0;
        double notUrgent = 1;
        for (Ingredient ingredient : recipe.ingredients()) {
            if (ingredient.staple()) {
                matches.add(new IngredientMatch(ingredient, Availability.ASSUMED, null, false));
                continue;
            }
            required++;
            FoodStock have = stock.get(ingredient.foodId());
            Availability availability = availability(ingredient, have);
            matched += switch (availability) {
                case ENOUGH, UNKNOWN_QUANTITY -> 1;
                case PARTIAL -> 0.5;
                case MISSING, ASSUMED -> 0;
            };
            LocalDate expires = have == null ? null : have.soonestExpiration();
            if (expires != null) {
                notUrgent *= 1 - urgency(ExpirationPriority.of(expires, today));
            }
            matches.add(new IngredientMatch(ingredient, availability, expires, have != null && have.soonestEstimated()));
        }

        Long daysSinceCooked = lastCooked == null ? null : ChronoUnit.DAYS.between(lastCooked, today);
        Factors factors = new Factors(
                required == 0 ? 0 : matched / required,
                1 - notUrgent,
                convenience(recipe),
                daysSinceCooked == null ? 1 : clamp((double) daysSinceCooked / NOVELTY_DAYS));
        double total = weights.ingredientMatch() + weights.expiryUrgency() + weights.convenience() + weights.novelty();
        double score = (weights.ingredientMatch() * factors.ingredientMatch()
                        + weights.expiryUrgency() * factors.expiryUrgency()
                        + weights.convenience() * factors.convenience()
                        + weights.novelty() * factors.novelty())
                / total;
        return new Scored(recipe, score, factors, List.copyOf(matches), daysSinceCooked);
    }

    private static Availability availability(Ingredient ingredient, @Nullable FoodStock have) {
        if (have == null || have.amounts().isEmpty()) {
            return Availability.MISSING;
        }
        Unit base = ingredient.quantity().unit().baseUnit();
        BigDecimal comparable = have.amounts().get(base.dimension());
        if (comparable == null) {
            return Availability.UNKNOWN_QUANTITY;
        }
        BigDecimal needed = ingredient.quantity().convertTo(base).amount();
        return comparable.compareTo(needed) >= 0 ? Availability.ENOUGH : Availability.PARTIAL;
    }

    /**
     * How much using a food at this level helps against waste. The urgencies of several foods combine like
     * independent chances, so two urgent foods count for more than one without ever passing 1.
     */
    static double urgency(ExpirationPriority priority) {
        return switch (priority) {
            case TODAY -> 0.7;
            case URGENT -> 0.5;
            case SOON -> 0.3;
            case UPCOMING -> 0.1;
            // Food past its date is never offered for cooking; it does not reach this point.
            case EXPIRED, OK -> 0;
        };
    }

    private static double convenience(Recipe recipe) {
        double time = clamp((double) (LONG_MINUTES - recipe.totalMinutes()) / (LONG_MINUTES - QUICK_MINUTES));
        double ease = switch (recipe.difficulty()) {
            case EASY -> 1;
            case MEDIUM -> 0.5;
            case HARD -> 0;
        };
        return 0.7 * time + 0.3 * ease;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
