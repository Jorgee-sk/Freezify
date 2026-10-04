package com.freezify.ai.internal;

import com.freezify.ai.AiService;
import com.freezify.food.Food;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the prompt of each use case, keeps the daily allowance and validates every answer. An answer that does
 * not follow the schema, or quotes or uses something that is not in the input, is thrown away whole: a model that
 * got part of it wrong may have got the rest wrong too.
 */
@Service
class DefaultAiService implements AiService {

    private final AiProvider provider;
    private final AiAllowance allowance;
    private final JsonMapper jsonMapper;

    DefaultAiService(AiProvider provider, AiAllowance allowance, JsonMapper jsonMapper) {
        this.provider = provider;
        this.allowance = allowance;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean enabled() {
        return !(provider instanceof NoAiProvider);
    }

    @Override
    public Optional<List<ReceiptLine>> readReceipt(String text, UUID userId) {
        if (!enabled() || !allowance.take(userId)) {
            return Optional.empty();
        }
        String input = ReceiptReading.input(text);
        return provider.complete(
                        new AiProvider.StructuredPrompt(ReceiptReading.INSTRUCTIONS, input, "receipt", ReceiptReading.SCHEMA))
                .flatMap(this::json)
                .flatMap(answer -> ReceiptReading.lines(answer, input));
    }

    @Override
    public Answer<WrittenRecipe> writeRecipe(RecipeRequest request, UUID userId) {
        if (!enabled()) {
            return Answer.not(Outcome.NOT_CONFIGURED);
        }
        if (!allowance.take(userId)) {
            return Answer.not(Outcome.LIMIT_REACHED);
        }
        return provider.complete(new AiProvider.StructuredPrompt(
                        RecipeWriting.INSTRUCTIONS, RecipeWriting.input(request, jsonMapper), "recipe", RecipeWriting.SCHEMA))
                .flatMap(this::json)
                .flatMap(answer -> RecipeWriting.recipe(answer, request))
                .map(Answer::ok)
                .orElseGet(() -> Answer.not(Outcome.FAILED));
    }

    @Override
    public Answer<List<FoodGuess>> identifyFood(
            byte[] image, String mediaType, List<Food> catalog, String language, UUID userId) {
        if (!enabled()) {
            return Answer.not(Outcome.NOT_CONFIGURED);
        }
        if (!allowance.take(userId)) {
            return Answer.not(Outcome.LIMIT_REACHED);
        }
        return provider.complete(new AiProvider.StructuredPrompt(
                        FoodIdentification.INSTRUCTIONS,
                        FoodIdentification.input(catalog, language, jsonMapper),
                        "food_photo",
                        FoodIdentification.schema(catalog),
                        new AiProvider.Picture(image, mediaType)))
                .flatMap(this::json)
                .flatMap(answer -> FoodIdentification.guesses(answer, catalog, language))
                .map(Answer::ok)
                .orElseGet(() -> Answer.not(Outcome.FAILED));
    }

    private Optional<JsonNode> json(String answer) {
        try {
            return Optional.of(jsonMapper.readTree(answer));
        } catch (JacksonException e) {
            return Answers.rejected("not JSON");
        }
    }
}
