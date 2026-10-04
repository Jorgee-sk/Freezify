package com.freezify.ai;

import com.freezify.food.Food;
import com.freezify.food.Quantity;
import com.freezify.food.Unit;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The concrete things a language model is asked to do. Callers never talk to a model: they get a validated answer
 * or nothing, and must always have a way to go on without it, because the model is optional, may be down and may
 * be wrong.
 */
public interface AiService {

    /** Whether a model is configured at all. Without one every use case answers nothing. */
    boolean enabled();

    /**
     * Reads the purchased products of the text of a shopping receipt.
     *
     * @param userId who asked: each person has a daily allowance of calls
     * @return empty when no model is configured, the allowance is spent, the call failed or the answer did not
     *     pass validation; in that case the caller falls back to its own parsing
     */
    Optional<List<ReceiptLine>> readReceipt(String text, UUID userId);

    /**
     * Writes a recipe that only uses the given ingredients, in amounts no larger than the ones given.
     *
     * @param userId who asked: each person has a daily allowance of calls
     */
    Answer<WrittenRecipe> writeRecipe(RecipeRequest request, UUID userId);

    /**
     * Says which food a photo shows, as candidates with how sure the model is of each. Never one rotund answer
     * when the model is not sure: then several, each with its own confidence.
     *
     * @param mediaType {@code image/jpeg}, {@code image/png} or {@code image/webp}, already checked by content
     * @param catalog   the foods a candidate may be; anything else is "other", with a name
     * @param language  {@code es} or {@code en}: the language of the names of other foods
     * @param userId    who asked: each person has a daily allowance of calls
     */
    Answer<List<FoodGuess>> identifyFood(
            byte[] image, String mediaType, List<Food> catalog, String language, UUID userId);

    /**
     * @param foodId     the catalog food, or {@code null} for food that is not in the catalog
     * @param name       for food that is not in the catalog, what the model calls it; otherwise its catalog name
     * @param confidence between 0 and 1
     */
    record FoodGuess(@Nullable UUID foodId, String name, double confidence) {}

    /**
     * A purchased product as the model read it. Every line quotes the text it comes from.
     *
     * @param text   the receipt text of the product, as printed
     * @param name   the product written in full ("Pechuga de pollo" for "PECH POLLO")
     * @param amount how much was bought, when the receipt says it
     * @param price  what was paid for the line, when the receipt says it
     */
    record ReceiptLine(
            String text, String name, @Nullable BigDecimal amount, @Nullable Unit unit, @Nullable BigDecimal price) {}

    /** How a call to the model went. Only {@code OK} carries a value. */
    enum Outcome {
        OK,
        /** No model is configured. */
        NOT_CONFIGURED,
        /** The person used up today's calls. */
        LIMIT_REACHED,
        /** The model could not be reached, or its answer did not pass validation. */
        FAILED
    }

    record Answer<T>(Outcome outcome, @Nullable T value) {

        public static <T> Answer<T> ok(T value) {
            return new Answer<>(Outcome.OK, value);
        }

        public static <T> Answer<T> not(Outcome outcome) {
            return new Answer<>(outcome, null);
        }
    }

    /**
     * @param language    {@code es} or {@code en}: the language of the recipe
     * @param ingredients everything the recipe may use; nothing else
     * @param avoided     what the household does not eat, in plain words, as a reminder for the model
     * @param mustUse     keys of the ingredients the recipe has to use, not as optional
     */
    record RecipeRequest(
            String language, int servings, List<Ingredient> ingredients, List<String> avoided, List<String> mustUse) {}

    /**
     * @param key      how the model refers to it
     * @param quantity how much there is; the recipe may not use more
     * @param daysLeft days until its date, when it has one; the fewer, the more it should be used
     * @param staple   salt, oil, water: assumed to be in any kitchen, in any amount
     */
    record Ingredient(String key, String name, Quantity quantity, @Nullable Long daysLeft, boolean staple) {}

    enum Difficulty {
        EASY,
        MEDIUM,
        HARD
    }

    record WrittenRecipe(
            String title,
            String summary,
            int minutes,
            Difficulty difficulty,
            List<UsedIngredient> ingredients,
            List<String> steps) {}

    /** @param key one of the keys of the request */
    record UsedIngredient(String key, Quantity quantity, boolean optional) {}
}
