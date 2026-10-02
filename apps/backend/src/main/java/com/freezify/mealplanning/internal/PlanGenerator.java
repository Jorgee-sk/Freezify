package com.freezify.mealplanning.internal;

import com.freezify.expiration.ExpirationPriority;
import com.freezify.food.FoodCategory;
import com.freezify.recipes.Recipe;
import com.freezify.recipes.Recipe.Ingredient;
import com.freezify.recipes.RecipeScorer;
import com.freezify.recipes.RecipeScorer.Assessment;
import com.freezify.recipes.RecipeScorer.Availability;
import com.freezify.recipes.RecipeScorer.FoodStock;
import com.freezify.recipes.RecipeScorer.IngredientMatch;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Fills the empty meals of a plan. A pure function: the same meals, recipes, pantry and history always give the
 * same plan.
 *
 * <p>Meals are filled in the order they will be eaten. For each one, every recipe is scored against what the
 * pantry would hold on that day, the best one is taken and its ingredients leave the pantry before the next
 * meal is looked at. Food that expires soon scores high on the days before its date and is gone after it, which
 * is what makes the plan use first what expires first. It is a greedy choice, meal by meal: a good plan, not the
 * best possible one.
 *
 * <pre>
 * score = Σ weight · factor / Σ weight        (every factor and the score are between 0 and 1)
 * </pre>
 */
final class PlanGenerator {

    /** A recipe may appear this many times in the same stretch of days, and only when nothing else is left. */
    static final int MAX_REPEATS = 2;

    /**
     * How much each factor counts. Only the proportions matter.
     *
     * @param waste   using food that is about to expire
     * @param pantry  having to buy little that is not already being bought for another meal of the plan
     * @param variety not eating the same kind of dish as in the meals around
     * @param novelty not having cooked or planned the recipe recently
     * @param effort  being quick and easy
     */
    record Weights(double waste, double pantry, double variety, double novelty, double effort) {
        Weights {
            if (waste < 0 || pantry < 0 || variety < 0 || novelty < 0 || effort < 0) {
                throw new IllegalArgumentException("Weights cannot be negative");
            }
            if (waste + pantry + variety + novelty + effort <= 0) {
                throw new IllegalArgumentException("At least one weight must be positive");
            }
        }
    }

    record Meal(LocalDate date, MealSlot slot) implements Comparable<Meal> {
        @Override
        public int compareTo(Meal other) {
            return Comparator.comparing(Meal::date).thenComparing(Meal::slot).compare(this, other);
        }
    }

    record Planned(Meal meal, Recipe recipe) {}

    record Factors(double waste, double pantry, double variety, double novelty, double effort) {}

    record Choice(Meal meal, Recipe recipe, double score, Factors factors) {}

    /** The categories that say what kind of dish something is: two chicken dishes in a row are a repetition. */
    private static final Set<FoodCategory> DEFINING = Set.of(FoodCategory.MEAT, FoodCategory.FISH, FoodCategory.EGGS);

    private final Weights weights;
    private final Function<UUID, @Nullable FoodCategory> categoryOf;

    PlanGenerator(Weights weights, Function<UUID, @Nullable FoodCategory> categoryOf) {
        this.weights = weights;
        this.categoryOf = categoryOf;
    }

    /**
     * @param empty      the meals to fill
     * @param kept       the meals already planned around them, which are not touched: they take their
     *                   ingredients first, and count for variety
     * @param candidates the recipes to choose from; what the household does not eat must already be left out
     * @param pantry     what the household has today
     * @param lastCooked the last day the household cooked each recipe
     * @return a choice for every meal that could be filled; a meal is left empty rather than filled with a
     *     recipe that is already in the plan too many times or too close
     */
    List<Choice> generate(
            List<Meal> empty,
            List<Planned> kept,
            List<Recipe> candidates,
            Pantry pantry,
            Map<UUID, LocalDate> lastCooked,
            LocalDate today) {
        Pantry remaining = pantry.copy();
        List<Planned> planned = new ArrayList<>(kept);
        planned.sort(Comparator.comparing(Planned::meal));
        Set<UUID> toBuy = new HashSet<>();
        // What members chose is served first: the generator only plans with what those meals leave.
        for (Planned meal : planned) {
            if (!meal.meal().date().isBefore(today)) {
                cook(meal.recipe(), meal.meal().date(), remaining, toBuy);
            }
        }

        List<Choice> choices = new ArrayList<>();
        for (Meal meal : empty.stream().sorted().toList()) {
            Map<UUID, FoodStock> stock = remaining.on(meal.date());
            Choice best = null;
            for (Recipe recipe : pool(candidates, planned, meal, stock)) {
                Choice choice = score(recipe, meal, stock, planned, toBuy, lastCooked);
                // The slug breaks ties, so that the plan never depends on chance.
                if (best == null
                        || choice.score() > best.score()
                        || (choice.score() == best.score()
                                && recipe.slug().compareTo(best.recipe().slug()) < 0)) {
                    best = choice;
                }
            }
            if (best == null) {
                continue;
            }
            choices.add(best);
            planned.add(new Planned(meal, best.recipe()));
            cook(best.recipe(), meal.date(), remaining, toBuy);
        }
        return choices;
    }

    /**
     * The recipes a meal may be filled with. Variety is a rule before it is a factor, because having plenty of
     * chicken should not give chicken on Monday, Tuesday and Wednesday. The first of these that is not empty:
     *
     * <ol>
     *   <li>recipes not in the plan that are a different kind of dish from the other meal of that day, and
     *       from the meals of the days before and after unless they use food that is about to expire: saving
     *       it is worth eating alike two days in a row;
     *   <li>recipes not in the plan that are a different kind of dish from the other meal of that day;
     *   <li>recipes not in the plan;
     *   <li>recipes that are in it once, not on that day nor on the days next to it.
     * </ol>
     */
    private List<Recipe> pool(List<Recipe> candidates, List<Planned> planned, Meal meal, Map<UUID, FoodStock> stock) {
        List<Recipe> fresh = candidates.stream()
                .filter(recipe -> planned.stream().noneMatch(p -> p.recipe().id().equals(recipe.id())))
                .toList();
        List<Recipe> differentToday = fresh.stream()
                .filter(recipe -> isDifferentKind(recipe, planned, meal, 0))
                .toList();
        List<Recipe> different = differentToday.stream()
                .filter(recipe -> isDifferentKind(recipe, planned, meal, 1) || savesFood(recipe, meal, stock))
                .toList();
        if (!different.isEmpty()) {
            return different;
        }
        if (!differentToday.isEmpty()) {
            return differentToday;
        }
        if (!fresh.isEmpty()) {
            return fresh;
        }
        return candidates.stream()
                .filter(recipe -> {
                    List<Planned> same = planned.stream()
                            .filter(p -> p.recipe().id().equals(recipe.id()))
                            .toList();
                    return same.size() < MAX_REPEATS
                            && same.stream().allMatch(p -> daysApart(p.meal().date(), meal.date()) > 1);
                })
                .toList();
    }

    /** Whether no meal planned within {@code days} of this one is the same kind of dish as the recipe. */
    private boolean isDifferentKind(Recipe recipe, List<Planned> planned, Meal meal, int days) {
        Set<UUID> kind = kindOf(recipe);
        return planned.stream()
                .filter(p -> daysApart(p.meal().date(), meal.date()) <= days)
                .allMatch(p -> Collections.disjoint(kind, kindOf(p.recipe())));
    }

    /** Whether the recipe uses food at home that has two days or less left on the day of the meal. */
    private static boolean savesFood(Recipe recipe, Meal meal, Map<UUID, FoodStock> stock) {
        return RecipeScorer.assess(recipe, stock, null, meal.date()).factors().expiryUrgency()
                >= RecipeScorer.urgency(ExpirationPriority.URGENT);
    }

    private Choice score(
            Recipe recipe,
            Meal meal,
            Map<UUID, FoodStock> stock,
            List<Planned> planned,
            Set<UUID> toBuy,
            Map<UUID, LocalDate> lastCooked) {
        Assessment assessment = RecipeScorer.assess(recipe, stock, null, meal.date());
        Factors factors = new Factors(
                assessment.factors().expiryUrgency(),
                pantryFactor(assessment.ingredients(), toBuy),
                variety(recipe, meal, planned),
                novelty(recipe, meal, planned, lastCooked.get(recipe.id())),
                assessment.factors().convenience());
        double total = weights.waste() + weights.pantry() + weights.variety() + weights.novelty() + weights.effort();
        double score = (weights.waste() * factors.waste()
                        + weights.pantry() * factors.pantry()
                        + weights.variety() * factors.variety()
                        + weights.novelty() * factors.novelty()
                        + weights.effort() * factors.effort())
                / total;
        return new Choice(meal, recipe, score, factors);
    }

    /**
     * The share of the ingredients that add nothing to the shopping: they are at home, or another meal of the
     * plan already needs them bought. It stands for both cost and reuse of ingredients; there are no prices to
     * do better.
     */
    private static double pantryFactor(List<IngredientMatch> ingredients, Set<UUID> toBuy) {
        int required = 0;
        double newPurchases = 0;
        for (IngredientMatch match : ingredients) {
            if (match.availability() == Availability.ASSUMED) {
                continue;
            }
            required++;
            if (toBuy.contains(match.ingredient().foodId())) {
                continue;
            }
            newPurchases += switch (match.availability()) {
                case MISSING -> 1;
                case PARTIAL -> 0.5;
                case ENOUGH, UNKNOWN_QUANTITY, ASSUMED -> 0;
            };
        }
        return required == 0 ? 1 : 1 - newPurchases / required;
    }

    /** 0 when the other meal of the day is the same kind of dish, 0.5 when a meal of the day before or after is. */
    private double variety(Recipe recipe, Meal meal, List<Planned> planned) {
        Set<UUID> kind = kindOf(recipe);
        double variety = 1;
        for (Planned other : planned) {
            long days = daysApart(other.meal().date(), meal.date());
            if (days > 1 || Collections.disjoint(kind, kindOf(other.recipe()))) {
                continue;
            }
            variety = Math.min(variety, days == 0 ? 0 : 0.5);
        }
        return variety;
    }

    /**
     * What makes two dishes feel the same: the ingredient the dish is built on (the first one listed) and its
     * meat, fish or eggs.
     */
    private Set<UUID> kindOf(Recipe recipe) {
        Set<UUID> kind = new HashSet<>();
        boolean first = true;
        for (Ingredient ingredient : recipe.ingredients()) {
            if (ingredient.staple()) {
                continue;
            }
            FoodCategory category = categoryOf.apply(ingredient.foodId());
            if (first || (category != null && DEFINING.contains(category))) {
                kind.add(ingredient.foodId());
            }
            first = false;
        }
        return kind;
    }

    /** How far the meal is from the nearest day the recipe was cooked or is planned. */
    private static double novelty(Recipe recipe, Meal meal, List<Planned> planned, @Nullable LocalDate lastCooked) {
        long nearest = lastCooked == null ? Long.MAX_VALUE : daysApart(lastCooked, meal.date());
        for (Planned other : planned) {
            if (other.recipe().id().equals(recipe.id())) {
                nearest = Math.min(nearest, daysApart(other.meal().date(), meal.date()));
            }
        }
        return nearest >= RecipeScorer.NOVELTY_DAYS ? 1 : (double) nearest / RecipeScorer.NOVELTY_DAYS;
    }

    private static void cook(Recipe recipe, LocalDate day, Pantry pantry, Set<UUID> toBuy) {
        for (IngredientMatch match : RecipeScorer.assess(recipe, pantry.on(day), null, day).ingredients()) {
            if (match.availability() == Availability.MISSING || match.availability() == Availability.PARTIAL) {
                toBuy.add(match.ingredient().foodId());
            }
        }
        pantry.cook(recipe, day);
    }

    private static long daysApart(LocalDate one, LocalDate other) {
        return Math.abs(ChronoUnit.DAYS.between(one, other));
    }
}
