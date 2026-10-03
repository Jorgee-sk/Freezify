package com.freezify.scanning.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.freezify.food.Food;
import com.freezify.food.FoodCategory;
import com.freezify.food.StorageLocation;
import com.freezify.food.Unit;
import com.freezify.scanning.internal.FoodMatcher.Match;
import com.freezify.scanning.internal.ScanViews.MatchedBy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FoodMatcherTests {

    private static final Food TOMATO = food("Tomate", "Tomato");
    private static final Food CRUSHED_TOMATO = food("Tomate triturado", "Crushed tomato");
    private static final Food MILK = food("Leche", "Milk");
    private static final Food CHOCOLATE = food("Chocolate", "Chocolate");
    private static final Food CHICKEN_BREAST = food("Pechuga de pollo", "Chicken breast");
    private static final Food MUSHROOMS = food("Champiñones", "Mushrooms");
    private static final Food CHEESE = food("Queso", "Cheese");
    private static final Food MOZZARELLA = food("Mozzarella", "Mozzarella");

    private final FoodMatcher matcher = new FoodMatcher(
            List.of(TOMATO, CRUSHED_TOMATO, MILK, CHOCOLATE, CHICKEN_BREAST, MUSHROOMS, CHEESE, MOZZARELLA),
            Map.of("pech pollo", CHICKEN_BREAST.id()),
            Map.of());

    @Test
    void aLineIsTheFoodWhoseNameItContains() {
        assertMatch("TOMATE PERA", TOMATO, MatchedBy.NAME);
        assertMatch("TOMATES RAMA", TOMATO, MatchedBy.NAME);
        assertMatch("LECHE ENTERA", MILK, MatchedBy.NAME);
        // Accents, plurals and small words do not get in the way.
        assertMatch("CHAMPINON LAMINADO", MUSHROOMS, MatchedBy.NAME);
        assertMatch("PECHUGA POLLO", CHICKEN_BREAST, MatchedBy.NAME);
    }

    @Test
    void theMostSpecificNameWins() {
        assertMatch("TOMATE TRITURADO HACENDADO", CRUSHED_TOMATO, MatchedBy.NAME);
    }

    @Test
    void betweenEquallySpecificNamesTheProductComesFirst() {
        Match match = assertMatch("CHOCOLATE CON LECHE", CHOCOLATE, MatchedBy.NAME);
        assertThat(match.candidates()).containsExactly(CHOCOLATE.id(), MILK.id());
    }

    @Test
    void abbreviationsAreFoundThroughAliases() {
        assertMatch("PECH POLLO FILETES", CHICKEN_BREAST, MatchedBy.ALIAS);
    }

    @Test
    void nothingIsSuggestedForUnknownProducts() {
        Match match = matcher.match(List.of("BOLSA PLASTICO"));
        assertThat(match.foodId()).isNull();
        assertThat(match.how()).isEqualTo(MatchedBy.NONE);
        assertThat(match.candidates()).isEmpty();
    }

    @Test
    void whatTheHouseholdConfirmedBeforeWinsOverEverythingElse() {
        FoodMatcher learnedOne = new FoodMatcher(
                List.of(TOMATO, CHEESE, MOZZARELLA),
                Map.of(),
                Map.of(FoodMatcher.key("QUESO MOZZ RALLADO"), MOZZARELLA.id()));

        Match match = learnedOne.match(List.of("Queso mozz. rallado"));
        assertThat(match.foodId()).isEqualTo(MOZZARELLA.id());
        assertThat(match.how()).isEqualTo(MatchedBy.LEARNED);
        // Without it, the product word comes first.
        assertThat(learnedOne.match(List.of("QUESO MOZZ")).foodId()).isEqualTo(CHEESE.id());
    }

    @Test
    void aFullNameIsTriedBeforeThePrintedText() {
        Match match = matcher.match(List.of("Pechuga de pollo", "PCHG PLL"));
        assertThat(match.foodId()).isEqualTo(CHICKEN_BREAST.id());
        assertThat(matcher.match(List.of("Leche de vaca", "LCH")).foodId()).isEqualTo(MILK.id());
    }

    private Match assertMatch(String text, Food food, MatchedBy how) {
        Match match = matcher.match(List.of(text));
        assertThat(match.foodId()).as(text).isEqualTo(food.id());
        assertThat(match.how()).as(text).isEqualTo(how);
        assertThat(match.candidates()).as(text).startsWith(food.id());
        return match;
    }

    private static Food food(String es, String en) {
        return new Food(UUID.randomUUID(), en.toLowerCase(), es, en, FoodCategory.OTHER, Unit.UNIT,
                StorageLocation.PANTRY, Set.of());
    }
}
