package com.freezify.mealplanning.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.freezify.food.FoodCategory;
import com.freezify.food.Quantity;
import com.freezify.food.Unit;
import com.freezify.mealplanning.internal.Pantry.Lot;
import com.freezify.mealplanning.internal.PlanGenerator.Choice;
import com.freezify.mealplanning.internal.PlanGenerator.Meal;
import com.freezify.mealplanning.internal.PlanGenerator.Planned;
import com.freezify.mealplanning.internal.PlanGenerator.Weights;
import com.freezify.recipes.Recipe;
import com.freezify.recipes.Recipe.Course;
import com.freezify.recipes.Recipe.Difficulty;
import com.freezify.recipes.Recipe.Ingredient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanGeneratorTests {

    /** A Monday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    private static final Weights WEIGHTS = new Weights(0.35, 0.25, 0.20, 0.10, 0.10);

    private static final UUID CHICKEN = UUID.randomUUID();
    private static final UUID HAKE = UUID.randomUUID();
    private static final UUID EGG = UUID.randomUUID();
    private static final UUID TOMATO = UUID.randomUUID();
    private static final UUID LETTUCE = UUID.randomUUID();
    private static final UUID RICE = UUID.randomUUID();
    private static final UUID LENTILS = UUID.randomUUID();
    private static final UUID CREAM = UUID.randomUUID();
    private static final UUID LEEK = UUID.randomUUID();
    private static final UUID PUMPKIN = UUID.randomUUID();

    private static final Map<UUID, FoodCategory> CATEGORIES = Map.of(
            CHICKEN, FoodCategory.MEAT,
            HAKE, FoodCategory.FISH,
            EGG, FoodCategory.EGGS,
            TOMATO, FoodCategory.VEGETABLES,
            LETTUCE, FoodCategory.VEGETABLES,
            RICE, FoodCategory.PANTRY,
            LENTILS, FoodCategory.PANTRY,
            CREAM, FoodCategory.DAIRY,
            LEEK, FoodCategory.VEGETABLES,
            PUMPKIN, FoodCategory.VEGETABLES);

    private final PlanGenerator generator = new PlanGenerator(WEIGHTS, CATEGORIES::get);
    private final Map<UUID, List<Lot>> lots = new HashMap<>();
    private final Map<UUID, LocalDate> lastCooked = new HashMap<>();
    private final List<Planned> kept = new ArrayList<>();

    @Test
    void whatExpiresFirstIsPlannedFirst() {
        // The example of the specification: chicken expires in two days, tomatoes in three, lettuce in four and
        // rice keeps.
        have(CHICKEN, "300", Unit.GRAM, TODAY.plusDays(2));
        have(TOMATO, "2", Unit.UNIT, TODAY.plusDays(3));
        have(LETTUCE, "1", Unit.UNIT, TODAY.plusDays(4));
        have(RICE, "1", Unit.KILOGRAM, null);
        Recipe chicken = recipe("grilled-chicken", grams(CHICKEN, 300));
        Recipe tomato = recipe("tomato-salad", units(TOMATO, 2));
        Recipe lettuce = recipe("lettuce-salad", units(LETTUCE, 1));
        Recipe rice = recipe("white-rice", grams(RICE, 200));

        List<Choice> plan = generate(
                List.of(lunch(0), lunch(1), lunch(2), lunch(3)), List.of(rice, lettuce, tomato, chicken));

        assertThat(slugs(plan)).containsExactly("grilled-chicken", "tomato-salad", "lettuce-salad", "white-rice");
        assertThat(plan.get(0).factors().waste()).isEqualTo(0.5);
        // By Tuesday the tomatoes have two days left, not three.
        assertThat(plan.get(1).factors().waste()).isEqualTo(0.5);
        assertThat(plan.get(3).factors().waste()).isZero();
    }

    @Test
    void foodIsNeverPlannedForAfterItsDate() {
        have(CHICKEN, "300", Unit.GRAM, TODAY.plusDays(1));
        have(RICE, "1", Unit.KILOGRAM, null);
        Recipe chicken = recipe("grilled-chicken", grams(CHICKEN, 300));
        Recipe rice = recipe("white-rice", grams(RICE, 200));

        List<Choice> plan = generate(List.of(lunch(2)), List.of(chicken, rice));

        // On Wednesday the chicken is past its date: cooking it would mean buying it again.
        assertThat(slugs(plan)).containsExactly("white-rice");
        assertThat(generate(List.of(lunch(1)), List.of(chicken, rice)))
                .extracting(choice -> choice.recipe().slug())
                .containsExactly("grilled-chicken");
    }

    @Test
    void theSameKindOfDishIsNotServedOnConsecutiveDays() {
        // Plenty of chicken and no hurry to use it: nothing but variety keeps it from being served every day.
        have(CHICKEN, "3", Unit.KILOGRAM, TODAY.plusDays(6));
        List<Recipe> recipes = List.of(
                recipe("chicken-a", grams(CHICKEN, 300)),
                recipe("chicken-b", grams(CHICKEN, 300), units(TOMATO, 1)),
                recipe("chicken-c", grams(RICE, 200), grams(CHICKEN, 300)),
                recipe("hake", grams(HAKE, 300)),
                recipe("lentils", grams(LENTILS, 250)),
                recipe("omelette", units(EGG, 4)));

        List<Choice> plan = generate(
                List.of(lunch(0), dinner(0), lunch(1), dinner(1), lunch(2), dinner(2)), recipes);

        assertThat(slugs(plan).get(0)).isEqualTo("chicken-a");
        assertThat(slugs(plan).subList(1, 4)).containsExactlyInAnyOrder("hake", "lentils", "omelette");
        // Chicken comes back two days later, and takes both meals only because nothing else is left.
        assertThat(slugs(plan).subList(4, 6)).containsExactly("chicken-b", "chicken-c");
        assertThat(plan.get(5).factors().variety()).isZero();
    }

    @Test
    void theSameKindOfDishFollowsOnTheNextDayWhenItSavesFoodAboutToExpire() {
        // Enough chicken for two meals, and it expires tomorrow.
        have(CHICKEN, "600", Unit.GRAM, TODAY.plusDays(1));
        List<Recipe> recipes = List.of(
                recipe("chicken-a", grams(CHICKEN, 300)),
                recipe("chicken-b", grams(CHICKEN, 300)),
                recipe("hake", grams(HAKE, 300)),
                recipe("lentils", grams(LENTILS, 250)));

        assertThat(slugs(generate(List.of(lunch(0), lunch(1)), recipes))).containsExactly("chicken-a", "chicken-b");
        // Even then, never twice on the same day while there is something else.
        assertThat(slugs(generate(List.of(lunch(0), dinner(0)), recipes))).containsExactly("chicken-a", "hake");
    }

    @Test
    void aDishIsTheSameKindWhenItSharesItsBaseOrItsMeatFishOrEggs() {
        have(RICE, "1", Unit.KILOGRAM, null);
        have(TOMATO, "10", Unit.UNIT, null);
        Recipe riceWithTomato = recipe("rice-tomato", grams(RICE, 200), units(TOMATO, 2));
        Recipe riceWithLeek = recipe("rice-leek", grams(RICE, 200), units(LEEK, 1));
        Recipe leekWithTomato = recipe("leek-tomato", units(LEEK, 1), units(TOMATO, 2));
        keep(lunch(0), riceWithTomato);

        // Both have everything to buy but one thing; the one built on rice again is passed over, although
        // sharing the tomatoes, which no dish is built on, is fine.
        List<Choice> plan = generate(List.of(dinner(0)), List.of(riceWithLeek, leekWithTomato));

        assertThat(slugs(plan)).containsExactly("leek-tomato");
        assertThat(plan.get(0).factors().variety()).isEqualTo(1);
    }

    @Test
    void whatIsAlreadyBeingBoughtForAnotherMealCostsNothingMore() {
        Recipe pumpkinSoup = recipe("pumpkin-soup", grams(PUMPKIN, 600), grams(CREAM, 100));
        Recipe leekSoup = recipe("leek-soup", units(LEEK, 2), grams(CREAM, 100));
        Recipe lentils = recipe("lentils", grams(LENTILS, 250), units(TOMATO, 1));
        keep(lunch(0), pumpkinSoup);

        List<Choice> plan = generate(List.of(lunch(3)), List.of(lentils, leekSoup));

        // Nothing is at home: the leek soup wins because the cream is on the shopping list anyway.
        assertThat(slugs(plan)).containsExactly("leek-soup");
        assertThat(plan.get(0).factors().pantry()).isEqualTo(0.5);
    }

    @Test
    void theMealsMembersChoseTakeTheirIngredientsFirst() {
        have(CHICKEN, "300", Unit.GRAM, TODAY.plusDays(5));
        have(RICE, "1", Unit.KILOGRAM, null);
        Recipe roast = recipe("roast-chicken", grams(CHICKEN, 300));
        Recipe stew = recipe("chicken-stew", grams(CHICKEN, 300));
        Recipe rice = recipe("white-rice", grams(RICE, 200));

        assertThat(slugs(generate(List.of(lunch(0)), List.of(stew, rice)))).containsExactly("chicken-stew");

        // Someone already planned to roast that chicken on Thursday.
        keep(dinner(3), roast);
        assertThat(slugs(generate(List.of(lunch(0)), List.of(stew, rice)))).containsExactly("white-rice");
    }

    @Test
    void aMealChosenForAPastDayTakesNothingFromThePantry() {
        have(CHICKEN, "300", Unit.GRAM, TODAY.plusDays(5));
        have(RICE, "1", Unit.KILOGRAM, null);
        Recipe roast = recipe("roast-chicken", grams(CHICKEN, 300));
        Recipe stew = recipe("chicken-stew", grams(CHICKEN, 300));
        Recipe rice = recipe("white-rice", grams(RICE, 200));
        keep(dinner(-3), roast);

        assertThat(slugs(generate(List.of(lunch(0)), List.of(stew, rice)))).containsExactly("chicken-stew");
    }

    @Test
    void anIngredientUsedUpByOneMealIsNotCountedForTheNext() {
        have(CHICKEN, "300", Unit.GRAM, TODAY.plusDays(6));
        have(RICE, "1", Unit.KILOGRAM, null);
        List<Recipe> recipes = List.of(
                recipe("chicken-a", grams(CHICKEN, 300)),
                recipe("chicken-b", grams(CHICKEN, 300)),
                recipe("white-rice", grams(RICE, 200)));

        List<Choice> plan = generate(List.of(lunch(0), lunch(3), lunch(6)), recipes);

        assertThat(slugs(plan)).containsExactly("chicken-a", "white-rice", "chicken-b");
        // The second chicken dish has to be bought whole.
        assertThat(plan.get(2).factors().pantry()).isZero();
        assertThat(plan.get(2).factors().waste()).isZero();
    }

    @Test
    void aRecipeCookedLatelyWaits() {
        Recipe hake = recipe("hake", grams(HAKE, 300));
        Recipe lentils = recipe("lentils", grams(LENTILS, 250));
        lastCooked.put(hake.id(), TODAY.minusDays(1));

        List<Choice> plan = generate(List.of(lunch(0), lunch(3)), List.of(hake, lentils));

        assertThat(slugs(plan)).containsExactly("lentils", "hake");
        assertThat(plan.get(1).factors().novelty()).isCloseTo(4.0 / 14, within(1e-9));
    }

    @Test
    void quickAndEasyRecipesComeFirstWhenNothingElseTellsThemApart() {
        Recipe slow = recipe("a-slow", 30, 60, Difficulty.HARD, grams(HAKE, 300));
        Recipe quick = recipe("b-quick", 5, 10, Difficulty.EASY, grams(LENTILS, 250));

        List<Choice> plan = generate(List.of(lunch(0)), List.of(slow, quick));

        assertThat(slugs(plan)).containsExactly("b-quick");
        assertThat(plan.get(0).factors().effort()).isEqualTo(1);
    }

    @Test
    void aMealIsLeftEmptyRatherThanRepeatingARecipeTooMuch() {
        Recipe only = recipe("lentils", grams(LENTILS, 250));

        List<Choice> plan = generate(
                List.of(lunch(0), dinner(0), lunch(1), lunch(2), lunch(4), lunch(6)), List.of(only));

        // Once, and once more two days later; never the same day, the next day or a third time.
        assertThat(plan).extracting(Choice::meal).containsExactly(lunch(0), lunch(2));
    }

    @Test
    void recipesAlreadyInThePlanAreNotChosenAgainWhileThereAreOthers() {
        Recipe hake = recipe("hake", grams(HAKE, 300));
        Recipe lentils = recipe("lentils", grams(LENTILS, 250));
        keep(lunch(0), hake);

        assertThat(slugs(generate(List.of(lunch(4)), List.of(hake, lentils)))).containsExactly("lentils");
    }

    @Test
    void theSameInputsAlwaysGiveTheSamePlanWhateverTheirOrder() {
        have(CHICKEN, "300", Unit.GRAM, TODAY.plusDays(2));
        List<Recipe> recipes = List.of(
                recipe("b-dish", grams(LENTILS, 250)),
                recipe("a-dish", grams(HAKE, 300)),
                recipe("grilled-chicken", grams(CHICKEN, 300)));
        List<Recipe> reversed = new ArrayList<>(recipes);
        java.util.Collections.reverse(reversed);

        List<Choice> plan = generate(List.of(dinner(1), lunch(0), lunch(1)), recipes);

        // Meals are filled in the order they are eaten; the slug breaks the tie between the two equal dishes.
        assertThat(plan).extracting(Choice::meal).containsExactly(lunch(0), lunch(1), dinner(1));
        assertThat(slugs(plan)).containsExactly("grilled-chicken", "a-dish", "b-dish");
        assertThat(generate(List.of(lunch(1), dinner(1), lunch(0)), reversed)).isEqualTo(plan);
    }

    @Test
    void theScoreIsTheWeightedAverageOfTheFactors() {
        have(CHICKEN, "300", Unit.GRAM, TODAY.plusDays(2));
        Recipe chicken = recipe("chicken-tomato", grams(CHICKEN, 300), units(TOMATO, 2));

        Choice choice = generate(List.of(lunch(0)), List.of(chicken)).get(0);

        // 20 minutes and easy.
        double effort = 0.7 * (70.0 / 75) + 0.3;
        assertThat(choice.factors().waste()).isEqualTo(0.5);
        assertThat(choice.factors().pantry()).isEqualTo(0.5);
        assertThat(choice.factors().variety()).isEqualTo(1);
        assertThat(choice.factors().novelty()).isEqualTo(1);
        assertThat(choice.factors().effort()).isCloseTo(effort, within(1e-9));
        assertThat(choice.score())
                .isCloseTo(0.35 * 0.5 + 0.25 * 0.5 + 0.20 + 0.10 + 0.10 * effort, within(1e-9));
    }

    @Test
    void thePantryGivenIsNotChanged() {
        have(CHICKEN, "300", Unit.GRAM, TODAY.plusDays(2));
        Pantry pantry = new Pantry(lots);

        generator.generate(
                List.of(lunch(0)), kept, List.of(recipe("grilled-chicken", grams(CHICKEN, 300))), pantry, lastCooked, TODAY);

        assertThat(pantry.on(TODAY)).containsKey(CHICKEN);
    }

    @Test
    void weightsMustMakeSense() {
        assertThatThrownBy(() -> new Weights(-0.1, 0.3, 0.1, 0.1, 0.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Weights(0, 0, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private List<Choice> generate(List<Meal> empty, List<Recipe> candidates) {
        return generator.generate(empty, kept, candidates, new Pantry(lots), lastCooked, TODAY);
    }

    private void have(UUID food, String amount, Unit unit, LocalDate expires) {
        Quantity base = Quantity.of(amount, unit).convertTo(unit.baseUnit());
        lots.computeIfAbsent(food, id -> new ArrayList<>())
                .add(new Lot(base.unit().dimension(), base.amount(), expires, false));
    }

    private void keep(Meal meal, Recipe recipe) {
        kept.add(new Planned(meal, recipe));
    }

    private static Meal lunch(int daysFromToday) {
        return new Meal(TODAY.plusDays(daysFromToday), MealSlot.LUNCH);
    }

    private static Meal dinner(int daysFromToday) {
        return new Meal(TODAY.plusDays(daysFromToday), MealSlot.DINNER);
    }

    private static Ingredient grams(UUID food, int amount) {
        return new Ingredient(food, Quantity.of(String.valueOf(amount), Unit.GRAM), false);
    }

    private static Ingredient units(UUID food, int amount) {
        return new Ingredient(food, Quantity.of(String.valueOf(amount), Unit.UNIT), false);
    }

    private static List<String> slugs(List<Choice> plan) {
        return plan.stream().map(choice -> choice.recipe().slug()).toList();
    }

    private static Recipe recipe(String slug, Ingredient... ingredients) {
        return recipe(slug, 10, 10, Difficulty.EASY, ingredients);
    }

    private static Recipe recipe(
            String slug, int prepMinutes, int cookMinutes, Difficulty difficulty, Ingredient... ingredients) {
        return new Recipe(
                UUID.nameUUIDFromBytes(slug.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                slug,
                slug,
                slug,
                "",
                "",
                2,
                prepMinutes,
                cookMinutes,
                difficulty,
                Course.MAIN,
                List.of(ingredients),
                Set.of(),
                List.of(),
                List.of());
    }
}
