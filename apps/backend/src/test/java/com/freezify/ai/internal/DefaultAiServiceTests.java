package com.freezify.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.freezify.ai.AiService.Answer;
import com.freezify.ai.AiService.Difficulty;
import com.freezify.ai.AiService.Ingredient;
import com.freezify.ai.AiService.Outcome;
import com.freezify.ai.AiService.ReceiptLine;
import com.freezify.ai.AiService.RecipeRequest;
import com.freezify.ai.AiService.UsedIngredient;
import com.freezify.ai.AiService.WrittenRecipe;
import com.freezify.food.Quantity;
import com.freezify.food.Unit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class DefaultAiServiceTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final List<AiProvider.StructuredPrompt> prompts = new ArrayList<>();
    private String answer = "";

    private final AiProvider provider = prompt -> {
        prompts.add(prompt);
        return Optional.of(answer);
    };

    /** An allowance of {@code dailyCalls} for everyone; the real one, in the database, has its own test. */
    private DefaultAiService service(AiProvider provider, int dailyCalls) {
        Map<UUID, Integer> calls = new HashMap<>();
        AiAllowance allowance = userId -> calls.merge(userId, 1, Integer::sum) <= dailyCalls;
        return new DefaultAiService(provider, allowance, JSON);
    }

    @Nested
    class ReadingAReceipt {

        private static final String RECEIPT = """
                MERCADONA S.A. A-46103834
                TARJ: ************1234 AUT: 0812345
                1 PECH POLLO       5,10
                1 LECHE ENTERA 1L  0,89
                """;

        @Test
        void aValidAnswerBecomesTheLinesOfTheReceipt() {
            answer = """
                    {"lines": [
                      {"text": "PECH POLLO", "name": "Pechuga de pollo", "amount": 0, "unit": "UNIT", "price": 5.10},
                      {"text": "LECHE  ENTERA 1L", "name": "Leche entera", "amount": 1, "unit": "LITER", "price": 0.89}
                    ]}""";

            Optional<List<ReceiptLine>> lines = service(provider, 5).readReceipt(RECEIPT, UUID.randomUUID());

            assertThat(lines).hasValue(List.of(
                    // "0" means the receipt did not say: nothing is made up.
                    new ReceiptLine("PECH POLLO", "Pechuga de pollo", null, null, new BigDecimal("5.1")),
                    new ReceiptLine(
                            "LECHE  ENTERA 1L", "Leche entera", new BigDecimal("1"), Unit.LITER, new BigDecimal("0.89"))));
            AiProvider.StructuredPrompt prompt = prompts.getFirst();
            assertThat(prompt.schemaName()).isEqualTo("receipt");
            assertThat(prompt.instructions()).contains("Never add a product that is not in the text");
            // Card, tax and authorisation numbers are not the model's business.
            assertThat(prompt.input()).doesNotContain("46103834", "0812345").contains("1 PECH POLLO");
        }

        @Test
        void anAnswerThatDoesNotFollowTheSchemaIsThrownAway() {
            List<String> wrong = List.of(
                    "not json",
                    "{\"products\": []}",
                    "{\"lines\": [{\"text\": \"PECH POLLO\", \"name\": \"Pollo\", \"amount\": 1, \"unit\": \"BOX\", \"price\": 1}]}",
                    "{\"lines\": [{\"text\": \"PECH POLLO\", \"name\": \"\", \"amount\": 1, \"unit\": \"UNIT\", \"price\": 1}]}",
                    "{\"lines\": [{\"text\": \"PECH POLLO\", \"name\": \"Pollo\", \"amount\": -1, \"unit\": \"UNIT\", \"price\": 1}]}",
                    "{\"lines\": [{\"text\": \"PECH POLLO\", \"name\": \"Pollo\", \"amount\": \"1\", \"unit\": \"UNIT\", \"price\": 1}]}");
            for (String each : wrong) {
                answer = each;
                assertThat(service(provider, 50).readReceipt(RECEIPT, UUID.randomUUID())).as(each).isEmpty();
            }
        }

        @Test
        void anAnswerThatInventsAProductIsThrownAwayWhole() {
            answer = """
                    {"lines": [
                      {"text": "PECH POLLO", "name": "Pechuga de pollo", "amount": 0, "unit": "UNIT", "price": 5.10},
                      {"text": "CAVIAR BELUGA", "name": "Caviar", "amount": 0, "unit": "UNIT", "price": 99}
                    ]}""";

            assertThat(service(provider, 5).readReceipt(RECEIPT, UUID.randomUUID())).isEmpty();
        }

        @Test
        void aSpentAllowanceMeansNoCall() {
            answer = "{\"lines\": []}";
            DefaultAiService service = service(provider, 2);
            UUID ana = UUID.randomUUID();

            assertThat(service.readReceipt(RECEIPT, ana)).hasValue(List.of());
            assertThat(service.readReceipt(RECEIPT, ana)).hasValue(List.of());
            assertThat(service.readReceipt(RECEIPT, ana)).isEmpty();
            assertThat(prompts).hasSize(2);
        }

        @Test
        void withoutAModelNothingIsAsked() {
            DefaultAiService service = service(new NoAiProvider(), 5);

            assertThat(service.enabled()).isFalse();
            assertThat(service.readReceipt(RECEIPT, UUID.randomUUID())).isEmpty();
        }

        @Test
        void aFailedCallGivesNothing() {
            assertThat(service(prompt -> Optional.empty(), 5).readReceipt(RECEIPT, UUID.randomUUID())).isEmpty();
        }
    }

    @Nested
    class WritingARecipe {

        private final RecipeRequest request = new RecipeRequest(
                "es",
                2,
                List.of(
                        new Ingredient("i1", "Calabacín", Quantity.of("2", Unit.UNIT), 1L, false),
                        new Ingredient("i2", "Pechuga de pollo", Quantity.of("400", Unit.GRAM), 3L, false),
                        new Ingredient("i3", "Arroz", Quantity.of("1", Unit.KILOGRAM), null, false),
                        new Ingredient("s1", "Sal", Quantity.of("1", Unit.GRAM), null, true)),
                List.of("pork"));

        private String recipe(String ingredients) {
            return """
                    {"title": "Arroz con pollo y calabacín", "summary": "Un arroz sencillo.", "minutes": 35,
                     "difficulty": "EASY", "ingredients": [%s],
                     "steps": ["Corta el pollo y el calabacín.", "Dóralos y añade el arroz.", "Cuece 18 minutos."]}"""
                    .formatted(ingredients);
        }

        @Test
        void aValidAnswerBecomesTheRecipe() {
            answer = recipe("""
                    {"key": "i1", "amount": 2, "unit": "UNIT", "optional": false},
                    {"key": "i2", "amount": 0.3, "unit": "KILOGRAM", "optional": false},
                    {"key": "i3", "amount": 160, "unit": "GRAM", "optional": false},
                    {"key": "s1", "amount": 5, "unit": "GRAM", "optional": true}""");

            Answer<WrittenRecipe> written = service(provider, 5).writeRecipe(request, UUID.randomUUID());

            assertThat(written.outcome()).isEqualTo(Outcome.OK);
            WrittenRecipe recipe = written.value();
            assertThat(recipe.title()).isEqualTo("Arroz con pollo y calabacín");
            assertThat(recipe.minutes()).isEqualTo(35);
            assertThat(recipe.difficulty()).isEqualTo(Difficulty.EASY);
            assertThat(recipe.steps()).hasSize(3);
            assertThat(recipe.ingredients()).containsExactly(
                    new UsedIngredient("i1", Quantity.of("2", Unit.UNIT), false),
                    new UsedIngredient("i2", Quantity.of("0.3", Unit.KILOGRAM), false),
                    new UsedIngredient("i3", Quantity.of("160", Unit.GRAM), false),
                    // A staple has no limit.
                    new UsedIngredient("s1", Quantity.of("5", Unit.GRAM), true));

            AiProvider.StructuredPrompt prompt = prompts.getFirst();
            assertThat(prompt.schemaName()).isEqualTo("recipe");
            JsonNode input = JSON.readTree(prompt.input());
            assertThat(input.path("language").asString()).isEqualTo("es");
            assertThat(input.path("servings").asInt()).isEqualTo(2);
            assertThat(input.path("avoid").path(0).asString()).isEqualTo("pork");
            assertThat(input.path("ingredients").path(0).path("name").asString()).isEqualTo("Calabacín");
            assertThat(input.path("ingredients").path(0).path("daysLeft").asInt()).isEqualTo(1);
            assertThat(input.path("ingredients").path(1).path("amount").isNumber()).isTrue();
            assertThat(input.path("ingredients").path(1).path("amount").asInt()).isEqualTo(400);
            assertThat(input.path("ingredients").path(3).path("staple").asBoolean()).isTrue();
        }

        @Test
        void aRecipeThatUsesWhatIsNotThereIsThrownAway() {
            Map<String, String> wrong = Map.of(
                    "an ingredient that is not in the list",
                    "{\"key\": \"i9\", \"amount\": 1, \"unit\": \"UNIT\", \"optional\": false}",
                    "more than there is",
                    "{\"key\": \"i2\", \"amount\": 500, \"unit\": \"GRAM\", \"optional\": false}",
                    "more than there is, in another unit",
                    "{\"key\": \"i3\", \"amount\": 1.5, \"unit\": \"KILOGRAM\", \"optional\": false}",
                    "a unit that measures something else",
                    "{\"key\": \"i2\", \"amount\": 1, \"unit\": \"LITER\", \"optional\": false}",
                    "the same ingredient twice",
                    "{\"key\": \"i1\", \"amount\": 1, \"unit\": \"UNIT\", \"optional\": false},"
                            + "{\"key\": \"i1\", \"amount\": 1, \"unit\": \"UNIT\", \"optional\": false}",
                    "nothing from the household but salt",
                    "{\"key\": \"s1\", \"amount\": 1, \"unit\": \"GRAM\", \"optional\": false}",
                    "only optional food",
                    "{\"key\": \"i1\", \"amount\": 1, \"unit\": \"UNIT\", \"optional\": true}");
            wrong.forEach((why, ingredients) -> {
                answer = recipe(ingredients);
                assertThat(service(provider, 50).writeRecipe(request, UUID.randomUUID()).outcome())
                        .as(why)
                        .isEqualTo(Outcome.FAILED);
            });
        }

        @Test
        void aRecipeThatDoesNotFollowTheSchemaIsThrownAway() {
            String ingredient = "{\"key\": \"i1\", \"amount\": 1, \"unit\": \"UNIT\", \"optional\": false}";
            List<String> wrong = List.of(
                    "{}",
                    recipe(ingredient).replace("\"minutes\": 35", "\"minutes\": 0"),
                    recipe(ingredient).replace("\"EASY\"", "\"TRIVIAL\""),
                    recipe(ingredient).replace("\"Un arroz sencillo.\"", "\"\""),
                    recipe(ingredient).replaceAll("\"steps\": \\[.*]", "\"steps\": []"),
                    recipe(""));
            for (String each : wrong) {
                answer = each;
                assertThat(service(provider, 50).writeRecipe(request, UUID.randomUUID()).outcome())
                        .as(each)
                        .isEqualTo(Outcome.FAILED);
            }
        }

        @Test
        void saysWhyThereIsNoRecipe() {
            assertThat(service(new NoAiProvider(), 5).writeRecipe(request, UUID.randomUUID()).outcome())
                    .isEqualTo(Outcome.NOT_CONFIGURED);
            assertThat(service(provider, 0).writeRecipe(request, UUID.randomUUID()).outcome())
                    .isEqualTo(Outcome.LIMIT_REACHED);
            assertThat(prompts).isEmpty();
            assertThat(service(prompt -> Optional.empty(), 5).writeRecipe(request, UUID.randomUUID()).outcome())
                    .isEqualTo(Outcome.FAILED);
        }
    }
}
