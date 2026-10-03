package com.freezify.mealplanning.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.freezify.food.Quantity;
import com.freezify.food.Unit;
import com.freezify.mealplanning.internal.Pantry.Lot;
import com.freezify.recipes.Recipe;
import com.freezify.recipes.Recipe.Course;
import com.freezify.recipes.Recipe.Difficulty;
import com.freezify.recipes.Recipe.Ingredient;
import com.freezify.recipes.RecipeScorer.FoodStock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PantryTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    private static final UUID CHICKEN = UUID.randomUUID();
    private static final UUID TOMATO = UUID.randomUUID();
    private static final UUID SALT = UUID.randomUUID();

    @Test
    void foodStopsCountingOnceItsDateHasPassed() {
        Pantry pantry = new Pantry(Map.of(
                CHICKEN,
                List.of(grams("300", TODAY.plusDays(1), true), grams("500", TODAY.plusDays(4), false), grams("200", null, false))));

        FoodStock today = pantry.on(TODAY).get(CHICKEN);
        assertThat(today.amounts()).containsEntry(Unit.Dimension.MASS, new BigDecimal("1000.000"));
        assertThat(today.soonestExpiration()).isEqualTo(TODAY.plusDays(1));
        assertThat(today.soonestEstimated()).isTrue();

        // Food is still good on the day of its date.
        assertThat(pantry.on(TODAY.plusDays(1)).get(CHICKEN).amounts())
                .containsEntry(Unit.Dimension.MASS, new BigDecimal("1000.000"));

        FoodStock later = pantry.on(TODAY.plusDays(2)).get(CHICKEN);
        assertThat(later.amounts()).containsEntry(Unit.Dimension.MASS, new BigDecimal("700.000"));
        assertThat(later.soonestExpiration()).isEqualTo(TODAY.plusDays(4));
        assertThat(later.soonestEstimated()).isFalse();

        FoodStock muchLater = pantry.on(TODAY.plusDays(30)).get(CHICKEN);
        assertThat(muchLater.amounts()).containsEntry(Unit.Dimension.MASS, new BigDecimal("200.000"));
        assertThat(muchLater.soonestExpiration()).isNull();
    }

    @Test
    void foodWithNothingLeftOnADayIsNotInThePantryThatDay() {
        Pantry pantry = new Pantry(Map.of(CHICKEN, List.of(grams("300", TODAY.plusDays(1), false))));

        assertThat(pantry.on(TODAY.plusDays(2))).doesNotContainKey(CHICKEN);
    }

    @Test
    void cookingTakesFirstWhatExpiresFirst() {
        Pantry pantry = new Pantry(Map.of(
                CHICKEN,
                List.of(grams("500", null, false), grams("500", TODAY.plusDays(4), false), grams("200", TODAY.plusDays(1), false))));

        pantry.cook(recipe(new Ingredient(CHICKEN, Quantity.of("300", Unit.GRAM), false)), TODAY);

        // The 200 g that expire tomorrow are gone, and 100 g of the next lot with them.
        FoodStock left = pantry.on(TODAY).get(CHICKEN);
        assertThat(left.amounts()).containsEntry(Unit.Dimension.MASS, new BigDecimal("900.000"));
        assertThat(left.soonestExpiration()).isEqualTo(TODAY.plusDays(4));
    }

    @Test
    void cookingConvertsUnitsAndNeverTakesMoreThanThereIs() {
        Pantry pantry = new Pantry(Map.of(CHICKEN, List.of(grams("300", null, false))));

        pantry.cook(recipe(new Ingredient(CHICKEN, Quantity.of("0.2", Unit.KILOGRAM), false)), TODAY);
        assertThat(pantry.on(TODAY).get(CHICKEN).amounts())
                .containsEntry(Unit.Dimension.MASS, new BigDecimal("100.000"));

        pantry.cook(recipe(new Ingredient(CHICKEN, Quantity.of("1", Unit.KILOGRAM), false)), TODAY);
        assertThat(pantry.on(TODAY)).doesNotContainKey(CHICKEN);
    }

    @Test
    void cookingDoesNotUseFoodPastItsDate() {
        Pantry pantry = new Pantry(Map.of(
                CHICKEN, List.of(grams("300", TODAY.plusDays(1), false), grams("300", TODAY.plusDays(5), false))));

        pantry.cook(recipe(new Ingredient(CHICKEN, Quantity.of("300", Unit.GRAM), false)), TODAY.plusDays(3));

        // It came out of the lot that was still good on that day.
        assertThat(pantry.on(TODAY.plusDays(3))).doesNotContainKey(CHICKEN);
        assertThat(pantry.on(TODAY).get(CHICKEN).amounts())
                .containsEntry(Unit.Dimension.MASS, new BigDecimal("300.000"));
    }

    @Test
    void foodMeasuredInAWayTheRecipeDoesNotUseIsLeftAsItIs() {
        Pantry pantry = new Pantry(Map.of(TOMATO, List.of(grams("500", null, false))));

        // The recipe asks for two tomatoes and the household has half a kilo: how much it uses is not known.
        pantry.cook(recipe(new Ingredient(TOMATO, Quantity.of("2", Unit.UNIT), false)), TODAY);

        assertThat(pantry.on(TODAY).get(TOMATO).amounts())
                .containsEntry(Unit.Dimension.MASS, new BigDecimal("500.000"));
    }

    @Test
    void staplesAreNeverTakenOutOfThePantry() {
        Pantry pantry = new Pantry(Map.of(SALT, List.of(grams("100", null, false))));

        pantry.cook(recipe(new Ingredient(SALT, Quantity.of("5", Unit.GRAM), true)), TODAY);

        assertThat(pantry.on(TODAY).get(SALT).amounts())
                .containsEntry(Unit.Dimension.MASS, new BigDecimal("100.000"));
    }

    @Test
    void cookingSaysWhatThePantryCouldNotGive() {
        Pantry pantry = new Pantry(Map.of(CHICKEN, List.of(grams("200", null, false))));
        Recipe recipe = recipe(
                new Ingredient(CHICKEN, Quantity.of("0.5", Unit.KILOGRAM), false),
                new Ingredient(TOMATO, Quantity.of("2", Unit.UNIT), false),
                new Ingredient(SALT, Quantity.of("5", Unit.GRAM), true));

        Map<UUID, Quantity> lacking = pantry.cook(recipe, TODAY);

        // In the base unit of the recipe's measure; staples are never lacking.
        assertThat(lacking)
                .containsOnlyKeys(CHICKEN, TOMATO)
                .containsEntry(CHICKEN, Quantity.of("300", Unit.GRAM))
                .containsEntry(TOMATO, Quantity.of("2", Unit.UNIT));
        // Once the chicken is gone, a second meal lacks all of it.
        assertThat(pantry.cook(recipe, TODAY)).containsEntry(CHICKEN, Quantity.of("500", Unit.GRAM));
    }

    @Test
    void foodPastItsDateIsLackingAsIfItWereNotThere() {
        Pantry pantry = new Pantry(Map.of(CHICKEN, List.of(grams("500", TODAY.plusDays(1), false))));

        assertThat(pantry.cook(recipe(new Ingredient(CHICKEN, Quantity.of("300", Unit.GRAM), false)), TODAY.plusDays(2)))
                .containsEntry(CHICKEN, Quantity.of("300", Unit.GRAM));
    }

    @Test
    void foodMeasuredInAWayThatCannotBeComparedIsNeverSaidToBeLacking() {
        Pantry pantry = new Pantry(Map.of(TOMATO, List.of(grams("500", null, false))));

        // Two tomatoes against half a kilo: whether it is enough is not known, so nothing is asked for.
        assertThat(pantry.cook(recipe(new Ingredient(TOMATO, Quantity.of("2", Unit.UNIT), false)), TODAY)).isEmpty();
    }

    @Test
    void aCopyIsCookedFromWithoutTouchingTheOriginal() {
        Pantry pantry = new Pantry(Map.of(CHICKEN, List.of(grams("300", null, false))));

        pantry.copy().cook(recipe(new Ingredient(CHICKEN, Quantity.of("300", Unit.GRAM), false)), TODAY);

        assertThat(pantry.on(TODAY)).containsKey(CHICKEN);
    }

    private static Lot grams(String amount, LocalDate expires, boolean estimated) {
        return new Lot(Unit.Dimension.MASS, new BigDecimal(amount).setScale(3), expires, estimated);
    }

    private static Recipe recipe(Ingredient... ingredients) {
        return new Recipe(
                UUID.randomUUID(),
                "recipe",
                "Receta",
                "Recipe",
                "",
                "",
                2,
                10,
                10,
                Difficulty.EASY,
                Course.MAIN,
                List.of(ingredients),
                Set.of(),
                List.of(),
                List.of());
    }
}
