package com.freezify.recipes.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.freezify.food.Quantity;
import com.freezify.food.Unit;
import com.freezify.recipes.internal.Recipe.Course;
import com.freezify.recipes.internal.Recipe.Difficulty;
import com.freezify.recipes.internal.Recipe.Ingredient;
import com.freezify.recipes.internal.RecipeScorer.Availability;
import com.freezify.recipes.internal.RecipeScorer.FoodStock;
import com.freezify.recipes.internal.RecipeScorer.Scored;
import com.freezify.recipes.internal.RecipeScorer.Weights;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipeScorerTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);
    private static final Weights WEIGHTS = new Weights(0.35, 0.30, 0.10, 0.10);

    private static final UUID PASTA = UUID.randomUUID();
    private static final UUID ZUCCHINI = UUID.randomUUID();
    private static final UUID TOMATO = UUID.randomUUID();
    private static final UUID MOZZARELLA = UUID.randomUUID();
    private static final UUID SALT = UUID.randomUUID();

    private final RecipeScorer scorer = new RecipeScorer(WEIGHTS);
    private final Map<UUID, FoodStock> stock = new HashMap<>();

    private final Recipe pasta = recipe(
            10,
            15,
            Difficulty.EASY,
            new Ingredient(PASTA, Quantity.of("200", Unit.GRAM), false),
            new Ingredient(ZUCCHINI, Quantity.of("1", Unit.UNIT), false),
            new Ingredient(TOMATO, Quantity.of("2", Unit.UNIT), false),
            new Ingredient(MOZZARELLA, Quantity.of("100", Unit.GRAM), false),
            new Ingredient(SALT, Quantity.of("3", Unit.GRAM), true));

    @Test
    void ingredientMatchIsTheShareOfIngredientsAtHome() {
        have(PASTA, "500", Unit.GRAM, null);
        have(ZUCCHINI, "1", Unit.UNIT, null);
        have(TOMATO, "2", Unit.UNIT, null);

        Scored scored = score(pasta);

        assertThat(scored.factors().ingredientMatch()).isEqualTo(0.75);
        assertThat(availabilities(scored))
                .containsExactly(
                        Availability.ENOUGH,
                        Availability.ENOUGH,
                        Availability.ENOUGH,
                        Availability.MISSING,
                        Availability.ASSUMED);
    }

    @Test
    void staplesAreNeitherLookedForNorCountedAsMissing() {
        have(PASTA, "200", Unit.GRAM, null);
        have(ZUCCHINI, "1", Unit.UNIT, null);
        have(TOMATO, "2", Unit.UNIT, null);
        have(MOZZARELLA, "100", Unit.GRAM, null);

        // No salt in the inventory, and the recipe is still fully covered.
        assertThat(score(pasta).factors().ingredientMatch()).isEqualTo(1);
    }

    @Test
    void havingLessThanNeededCountsHalf() {
        have(PASTA, "100", Unit.GRAM, null);

        Scored scored = score(pasta);

        assertThat(scored.ingredients().get(0).availability()).isEqualTo(Availability.PARTIAL);
        assertThat(scored.factors().ingredientMatch()).isEqualTo(0.125);
    }

    @Test
    void quantitiesAreComparedAcrossUnitsOfTheSameKind() {
        have(PASTA, "0.2", Unit.KILOGRAM, null);

        assertThat(score(pasta).ingredients().get(0).availability()).isEqualTo(Availability.ENOUGH);
    }

    @Test
    void foodMeasuredInAnotherWayCountsAsAtHomeWithoutClaimingItIsEnough() {
        // Half a kilo of tomatoes against "2 tomatoes": there is no honest way to compare them.
        have(TOMATO, "500", Unit.GRAM, null);

        Scored scored = score(pasta);

        assertThat(scored.ingredients().get(2).availability()).isEqualTo(Availability.UNKNOWN_QUANTITY);
        assertThat(scored.factors().ingredientMatch()).isEqualTo(0.25);
    }

    @Test
    void aRecipeWithNothingAtHomeUsesNothingAtHome() {
        assertThat(score(pasta).usesSomethingAtHome()).isFalse();
        assertThat(score(pasta).factors().ingredientMatch()).isZero();

        have(TOMATO, "1", Unit.UNIT, null);
        assertThat(score(pasta).usesSomethingAtHome()).isTrue();
    }

    @Test
    void usingFoodThatIsRunningOutOfTimeRaisesTheUrgency() {
        have(PASTA, "500", Unit.GRAM, TODAY.plusDays(200));
        assertThat(score(pasta).factors().expiryUrgency()).isZero();

        have(ZUCCHINI, "1", Unit.UNIT, TODAY.plusDays(2));
        assertThat(score(pasta).factors().expiryUrgency()).isEqualTo(0.5);

        // Two foods in a hurry count for more than one, without ever passing 1.
        have(TOMATO, "2", Unit.UNIT, TODAY.plusDays(4));
        assertThat(score(pasta).factors().expiryUrgency()).isCloseTo(0.65, within(1e-9));

        have(MOZZARELLA, "100", Unit.GRAM, TODAY);
        assertThat(score(pasta).factors().expiryUrgency()).isCloseTo(0.895, within(1e-9));
    }

    @Test
    void theUrgencyOfEachLevelFollowsItsPriority() {
        assertThat(urgencyWithZucchiniExpiringIn(0)).isCloseTo(0.7, within(1e-9));
        assertThat(urgencyWithZucchiniExpiringIn(1)).isCloseTo(0.5, within(1e-9));
        assertThat(urgencyWithZucchiniExpiringIn(5)).isCloseTo(0.3, within(1e-9));
        assertThat(urgencyWithZucchiniExpiringIn(8)).isCloseTo(0.1, within(1e-9));
        assertThat(urgencyWithZucchiniExpiringIn(30)).isZero();
    }

    @Test
    void reportsTheDateBehindTheUrgencyAndWhetherItIsAnEstimate() {
        stock.put(ZUCCHINI, new FoodStock(Map.of(Unit.Dimension.COUNT, BigDecimal.ONE), TODAY.plusDays(2), true));

        var zucchini = score(pasta).ingredients().get(1);

        assertThat(zucchini.expirationDate()).isEqualTo(TODAY.plusDays(2));
        assertThat(zucchini.estimated()).isTrue();
        assertThat(score(pasta).ingredients().get(0).expirationDate()).isNull();
    }

    @Test
    void quickAndEasyRecipesAreMoreConvenient() {
        assertThat(score(recipe(5, 10, Difficulty.EASY)).factors().convenience()).isEqualTo(1);
        assertThat(score(recipe(30, 60, Difficulty.HARD)).factors().convenience()).isZero();
        assertThat(score(recipe(60, 120, Difficulty.EASY)).factors().convenience()).isCloseTo(0.3, within(1e-9));
        // 52 minutes: 38 of the 75 that separate a long recipe from a quick one.
        assertThat(score(recipe(15, 37, Difficulty.MEDIUM)).factors().convenience())
                .isCloseTo(0.7 * (38.0 / 75) + 0.15, within(1e-9));
    }

    @Test
    void aRecipeCookedLatelyIsLessOfANovelty() {
        assertThat(scorer.score(pasta, stock, null, TODAY).factors().novelty()).isEqualTo(1);
        assertThat(scorer.score(pasta, stock, null, TODAY).daysSinceCooked()).isNull();
        assertThat(scorer.score(pasta, stock, TODAY, TODAY).factors().novelty()).isZero();
        assertThat(scorer.score(pasta, stock, TODAY, TODAY).daysSinceCooked()).isZero();
        assertThat(scorer.score(pasta, stock, TODAY.minusDays(7), TODAY).factors().novelty())
                .isEqualTo(0.5);
        assertThat(scorer.score(pasta, stock, TODAY.minusDays(40), TODAY).factors().novelty())
                .isEqualTo(1);
    }

    @Test
    void theScoreIsTheWeightedAverageOfTheFactors() {
        have(PASTA, "500", Unit.GRAM, null);
        have(ZUCCHINI, "1", Unit.UNIT, TODAY.plusDays(2));

        Scored scored = score(pasta);

        // match 0.5, urgency 0.5, convenience (25 min, easy) and novelty 1.
        double convenience = 0.7 * (65.0 / 75) + 0.3;
        assertThat(scored.factors().convenience()).isCloseTo(convenience, within(1e-9));
        assertThat(scored.score())
                .isCloseTo((0.35 * 0.5 + 0.30 * 0.5 + 0.10 * convenience + 0.10 * 1) / 0.85, within(1e-9));
        assertThat(scored.score()).isBetween(0.0, 1.0);
    }

    @Test
    void onlyTheProportionsOfTheWeightsMatter() {
        have(PASTA, "500", Unit.GRAM, null);
        have(ZUCCHINI, "1", Unit.UNIT, TODAY.plusDays(2));

        double doubled = new RecipeScorer(new Weights(0.70, 0.60, 0.20, 0.20))
                .score(pasta, stock, null, TODAY)
                .score();

        assertThat(doubled).isCloseTo(score(pasta).score(), within(1e-9));
    }

    @Test
    void aFactorWithoutWeightDoesNotCount() {
        have(PASTA, "500", Unit.GRAM, null);
        have(ZUCCHINI, "1", Unit.UNIT, TODAY);

        Scored scored =
                new RecipeScorer(new Weights(1, 0, 0, 0)).score(pasta, stock, TODAY, TODAY);

        assertThat(scored.score()).isEqualTo(scored.factors().ingredientMatch());
    }

    @Test
    void theSameInputsAlwaysGiveTheSameScore() {
        have(PASTA, "500", Unit.GRAM, null);
        have(ZUCCHINI, "1", Unit.UNIT, TODAY.plusDays(2));

        assertThat(score(pasta)).isEqualTo(score(pasta));
    }

    @Test
    void weightsMustMakeSense() {
        assertThatThrownBy(() -> new Weights(-0.1, 0.3, 0.1, 0.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Weights(0, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private double urgencyWithZucchiniExpiringIn(int days) {
        stock.clear();
        have(ZUCCHINI, "1", Unit.UNIT, TODAY.plusDays(days));
        return score(pasta).factors().expiryUrgency();
    }

    private Scored score(Recipe recipe) {
        return scorer.score(recipe, stock, null, TODAY);
    }

    private void have(UUID food, String amount, Unit unit, LocalDate expires) {
        Quantity base = Quantity.of(amount, unit).convertTo(unit.baseUnit());
        stock.put(food, new FoodStock(Map.of(base.unit().dimension(), base.amount()), expires, false));
    }

    private static List<Availability> availabilities(Scored scored) {
        return scored.ingredients().stream().map(match -> match.availability()).toList();
    }

    private static Recipe recipe(int prepMinutes, int cookMinutes, Difficulty difficulty, Ingredient... ingredients) {
        return new Recipe(
                UUID.randomUUID(),
                "test",
                "Receta",
                "Recipe",
                "",
                "",
                2,
                prepMinutes,
                cookMinutes,
                difficulty,
                Course.MAIN,
                List.of(ingredients),
                java.util.Set.of(),
                List.of(),
                List.of());
    }
}
