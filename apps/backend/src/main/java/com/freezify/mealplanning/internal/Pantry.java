package com.freezify.mealplanning.internal;

import com.freezify.food.Quantity;
import com.freezify.food.Unit;
import com.freezify.recipes.Recipe;
import com.freezify.recipes.Recipe.Ingredient;
import com.freezify.recipes.RecipeScorer.FoodStock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What a household has, as the planner sees it across a week: food that runs out as meals are cooked and that
 * stops counting once its date has passed. It is a simulation of the inventory, never the inventory itself:
 * nothing here is written back.
 */
final class Pantry {

    /**
     * Some amount of a food with one date.
     *
     * @param itemId  the inventory item it stands for, when it matters which one it is
     * @param amount  in the base unit of {@code dimension}
     * @param expires {@code null} when the item has no date
     */
    record Lot(
            @Nullable UUID itemId,
            Unit.Dimension dimension,
            BigDecimal amount,
            @Nullable LocalDate expires,
            boolean estimated) {

        Lot(Unit.Dimension dimension, BigDecimal amount, @Nullable LocalDate expires, boolean estimated) {
            this(null, dimension, amount, expires, estimated);
        }

        boolean usableOn(LocalDate day) {
            return amount.signum() > 0 && (expires == null || !expires.isBefore(day));
        }
    }

    /** The lots that go first come first: the ones with the earliest date, those without a date last. */
    private static final Comparator<Lot> FIRST_TO_EXPIRE =
            Comparator.comparing(Lot::expires, Comparator.nullsLast(Comparator.naturalOrder()));

    private final Map<UUID, List<Lot>> lotsByFood;

    Pantry(Map<UUID, List<Lot>> lotsByFood) {
        this.lotsByFood = new HashMap<>();
        lotsByFood.forEach((foodId, lots) -> {
            List<Lot> sorted = new ArrayList<>(lots);
            sorted.sort(FIRST_TO_EXPIRE);
            this.lotsByFood.put(foodId, sorted);
        });
    }

    Pantry copy() {
        return new Pantry(lotsByFood);
    }

    /** What can still be eaten on {@code day}, in the shape recipes are assessed against. */
    Map<UUID, FoodStock> on(LocalDate day) {
        Map<UUID, FoodStock> stock = new HashMap<>();
        lotsByFood.forEach((foodId, lots) -> {
            Map<Unit.Dimension, BigDecimal> amounts = new EnumMap<>(Unit.Dimension.class);
            Lot soonest = null;
            for (Lot lot : lots) {
                if (!lot.usableOn(day)) {
                    continue;
                }
                amounts.merge(lot.dimension(), lot.amount(), BigDecimal::add);
                if (soonest == null) {
                    soonest = lot;
                }
            }
            if (soonest != null) {
                stock.put(foodId, new FoodStock(amounts, soonest.expires(), soonest.estimated()));
            }
        });
        return stock;
    }

    /** How much of an inventory item no meal has taken, in the base unit of its dimension. */
    BigDecimal left(UUID itemId) {
        return lotsByFood.values().stream()
                .flatMap(List::stream)
                .filter(lot -> itemId.equals(lot.itemId()))
                .map(Lot::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Takes what the recipe needs out of the pantry, the food that expires first going first. Food measured in a
     * way that cannot be compared with the recipe (pieces against grams) is left as it is: how much of it a
     * recipe uses is not known, and guessing would make the plan claim something false.
     *
     * @return what the pantry could not give, by food, in the base unit of the recipe's measure. Food at home
     *     measured in a way that cannot be compared is not in it: whether more is needed is not known.
     */
    Map<UUID, Quantity> cook(Recipe recipe, LocalDate day) {
        Map<UUID, Quantity> lacking = new HashMap<>();
        for (Ingredient ingredient : recipe.ingredients()) {
            if (ingredient.staple()) {
                continue;
            }
            Unit base = ingredient.quantity().unit().baseUnit();
            BigDecimal needed = ingredient.quantity().convertTo(base).amount();
            List<Lot> lots = lotsByFood.getOrDefault(ingredient.foodId(), List.of());
            boolean comparable = lots.stream()
                    .anyMatch(lot -> lot.usableOn(day) && lot.dimension() == base.dimension());
            boolean somethingElse = lots.stream().anyMatch(lot -> lot.usableOn(day));
            if (!comparable) {
                if (!somethingElse) {
                    lacking.put(ingredient.foodId(), new Quantity(needed, base));
                }
                continue;
            }
            for (int i = 0; i < lots.size() && needed.signum() > 0; i++) {
                Lot lot = lots.get(i);
                if (!lot.usableOn(day) || lot.dimension() != base.dimension()) {
                    continue;
                }
                BigDecimal taken = lot.amount().min(needed);
                lots.set(
                        i,
                        new Lot(
                                lot.itemId(),
                                lot.dimension(),
                                lot.amount().subtract(taken),
                                lot.expires(),
                                lot.estimated()));
                needed = needed.subtract(taken);
            }
            if (needed.signum() > 0) {
                lacking.put(ingredient.foodId(), new Quantity(needed, base));
            }
        }
        return lacking;
    }
}
