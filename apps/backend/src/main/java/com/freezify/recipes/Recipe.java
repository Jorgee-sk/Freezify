package com.freezify.recipes;

import com.freezify.food.FoodTrait;
import com.freezify.food.Quantity;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A recipe as the application reasons about it: texts in both languages and ingredients tied to the catalog.
 *
 * @param contains everything its ingredients contain that someone may not eat, staples included
 */
public record Recipe(
        UUID id,
        String slug,
        String nameEs,
        String nameEn,
        String descriptionEs,
        String descriptionEn,
        int servings,
        int prepMinutes,
        int cookMinutes,
        Difficulty difficulty,
        Course course,
        List<Ingredient> ingredients,
        Set<FoodTrait> contains,
        List<String> stepsEs,
        List<String> stepsEn) {

    public enum Difficulty {
        EASY,
        MEDIUM,
        HARD
    }

    public enum Course {
        BREAKFAST,
        MAIN,
        DESSERT
    }

    /**
     * @param staple something a kitchen is assumed to have (salt, oil, water): listed, but never counted as missing
     */
    public record Ingredient(UUID foodId, Quantity quantity, boolean staple) {}

    public Recipe {
        ingredients = List.copyOf(ingredients);
        contains = Set.copyOf(contains);
        stepsEs = List.copyOf(stepsEs);
        stepsEn = List.copyOf(stepsEn);
    }

    public int totalMinutes() {
        return prepMinutes + cookMinutes;
    }

    public String name(String language) {
        return "es".equals(language) ? nameEs : nameEn;
    }

    public String description(String language) {
        return "es".equals(language) ? descriptionEs : descriptionEn;
    }

    public List<String> steps(String language) {
        return "es".equals(language) ? stepsEs : stepsEn;
    }
}
