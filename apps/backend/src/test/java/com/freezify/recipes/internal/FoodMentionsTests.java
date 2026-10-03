package com.freezify.recipes.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.freezify.food.Food;
import com.freezify.food.FoodCategory;
import com.freezify.food.StorageLocation;
import com.freezify.food.Unit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FoodMentionsTests {

    private static final Food TOMATO = food("Tomate", "Tomato");
    private static final Food CRUSHED_TOMATO = food("Tomate triturado", "Crushed tomato");
    private static final Food EGGS = food("Huevos", "Eggs");
    private static final Food CREAM = food("Nata para cocinar", "Cooking cream");
    private static final List<Food> CATALOG = List.of(TOMATO, CRUSHED_TOMATO, EGGS, CREAM);

    @Test
    void findsFoodThatIsNotAllowed() {
        assertThat(mentions(Set.of(TOMATO.id()), "Bate un huevo y mézclalo con el tomate.")).isTrue();
        assertThat(mentions(Set.of(TOMATO.id()), "Añade la nata para cocinar.")).isTrue();
        assertThat(mentions(Set.of(EGGS.id()), "Fríe los huevos.")).isFalse();
    }

    @Test
    void aNameInsideTheNameOfAnAllowedFoodDoesNotCount() {
        assertThat(mentions(Set.of(CRUSHED_TOMATO.id()), "Calienta el tomate triturado.")).isFalse();
        // But "tomate" alone is another food.
        assertThat(mentions(Set.of(CRUSHED_TOMATO.id()), "Calienta el tomate triturado y añade un tomate.")).isTrue();
    }

    @Test
    void onlyNamesInTheLanguageOfTheRecipeCount() {
        assertThat(FoodMentions.mentionsOtherFood(CATALOG, Set.of(), "en", "Whisk two eggs.")).isTrue();
        assertThat(FoodMentions.mentionsOtherFood(CATALOG, Set.of(), "en", "Bate dos huevos.")).isFalse();
    }

    private static boolean mentions(Set<UUID> allowed, String text) {
        return FoodMentions.mentionsOtherFood(CATALOG, allowed, "es", text);
    }

    private static Food food(String es, String en) {
        return new Food(UUID.randomUUID(), en.toLowerCase(), es, en, FoodCategory.OTHER, Unit.UNIT,
                StorageLocation.PANTRY, Set.of());
    }
}
