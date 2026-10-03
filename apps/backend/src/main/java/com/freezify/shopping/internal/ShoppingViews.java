package com.freezify.shopping.internal;

import com.freezify.food.FoodCategory;
import com.freezify.food.Unit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The shopping list as clients receive it, in the language they asked for. */
public final class ShoppingViews {

    private ShoppingViews() {}

    /** Every line, aisle by aisle (fresh food first) and by name within each aisle. */
    public record ShoppingList(List<Item> items) {}

    /**
     * @param foodId   the catalog food, when it is one
     * @param name     from the catalog in the language asked for, or as a person wrote it
     * @param quantity {@code null} when nobody said how much
     * @param neededOn for lines that come from the plan, the day of the first meal that needs it
     */
    public record Item(
            UUID id,
            @Nullable UUID foodId,
            String name,
            FoodCategory category,
            @Nullable Amount quantity,
            ShoppingItemOrigin origin,
            @Nullable LocalDate neededOn,
            boolean checked,
            @Nullable Instant checkedAt) {}

    public record Amount(BigDecimal amount, Unit unit) {}

    /**
     * @param lines how many lines of the list are there now for the meals of that week; 0 when nothing is lacking
     */
    public record FilledFromPlan(int lines) {}

    /**
     * @param stocked how many bought lines went into the inventory and left the list
     * @param left    the bought lines that stay on the list because nobody said how much was bought
     */
    public record Stocked(int stocked, List<String> left) {}
}
