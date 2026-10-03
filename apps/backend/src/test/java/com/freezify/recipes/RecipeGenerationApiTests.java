package com.freezify.recipes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.freezify.ai.AiService;
import com.freezify.ai.AiService.Answer;
import com.freezify.ai.AiService.Difficulty;
import com.freezify.ai.AiService.Ingredient;
import com.freezify.ai.AiService.Outcome;
import com.freezify.ai.AiService.RecipeRequest;
import com.freezify.ai.AiService.UsedIngredient;
import com.freezify.ai.AiService.WrittenRecipe;
import com.freezify.food.Quantity;
import com.freezify.food.Unit;
import com.freezify.testsupport.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

class RecipeGenerationApiTests extends ApiTestSupport {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Madrid"));

    /** The model is replaced: what it answers is checked by the AI module's own tests. */
    @MockitoBean
    private AiService ai;

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser jorge;
    private String householdId;

    @BeforeEach
    void householdWithFood() throws Exception {
        jorge = register("Jorge");
        householdId = createHousehold(jorge, "Casa");
        when(ai.enabled()).thenReturn(true);
    }

    @Test
    void writesARecipeWithWhatTheHouseholdHasAndEats() throws Exception {
        stock("Calabacín", "2", "UNIT", TODAY.plusDays(1));
        stock("Pechuga de pollo", "300", "GRAM", TODAY.plusDays(3));
        stock("Pechuga de pollo", "200", "GRAM", TODAY.plusDays(5));
        stock("Jamón serrano", "100", "GRAM", TODAY.plusDays(20));
        stock("Yogur", "2", "UNIT", TODAY.minusDays(1));
        stockFreeText("Salsa de la abuela", TODAY.plusDays(4));
        mvc.perform(as(jorge, json(put("/api/v1/households/" + householdId + "/diet"), """
                        {"type": "NONE", "avoided": ["PORK"]}
                        """)))
                .andExpect(status().isOk());
        ArgumentCaptor<RecipeRequest> asked = ArgumentCaptor.forClass(RecipeRequest.class);
        when(ai.writeRecipe(asked.capture(), any())).thenAnswer(call -> {
            RecipeRequest request = call.getArgument(0);
            return Answer.ok(new WrittenRecipe(
                    "Pollo salteado con calabacín",
                    "Un salteado rápido.",
                    20,
                    Difficulty.EASY,
                    List.of(
                            new UsedIngredient(key(request, "Pechuga de pollo"), Quantity.of("400", Unit.GRAM), false),
                            new UsedIngredient(key(request, "Calabacín"), Quantity.of("2", Unit.UNIT), false),
                            new UsedIngredient(key(request, "Sal"), Quantity.of("2", Unit.GRAM), true)),
                    List.of("Corta el pollo y el calabacín.", "Saltéalos con aceite de oliva y sal.")));
        });

        generate(jorge, "{\"servings\": 3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Pollo salteado con calabacín"))
                .andExpect(jsonPath("$.servings").value(3))
                .andExpect(jsonPath("$.minutes").value(20))
                .andExpect(jsonPath("$.difficulty").value("EASY"))
                // Staples last, the food the recipe needs first.
                .andExpect(jsonPath("$.ingredients[*].name", contains("Pechuga de pollo", "Calabacín", "Sal")))
                .andExpect(jsonPath("$.ingredients[0].amount").value(400))
                .andExpect(jsonPath("$.ingredients[0].daysLeft").value(3))
                .andExpect(jsonPath("$.ingredients[0].estimated").value(false))
                .andExpect(jsonPath("$.ingredients[1].priority").value("URGENT"))
                .andExpect(jsonPath("$.ingredients[2].staple").value(true))
                .andExpect(jsonPath("$.ingredients[2].daysLeft").isEmpty())
                .andExpect(jsonPath("$.steps", hasSize(2)));

        RecipeRequest request = asked.getValue();
        assertThat(request.language()).isEqualTo("es");
        assertThat(request.servings()).isEqualTo(3);
        assertThat(request.avoided()).containsExactly("pork");
        Map<String, Ingredient> offered =
                request.ingredients().stream().collect(Collectors.toMap(Ingredient::name, ingredient -> ingredient));
        // Not the ham (pork), not the yogurt (past its date), not the sauce (its contents are unknown).
        assertThat(offered).containsOnlyKeys("Calabacín", "Pechuga de pollo", "Sal", "Aceite de oliva", "Agua");
        // Both packs of chicken, as one ingredient that expires with the first one.
        assertThat(offered.get("Pechuga de pollo").quantity()).isEqualTo(Quantity.of("500", Unit.GRAM));
        assertThat(offered.get("Pechuga de pollo").daysLeft()).isEqualTo(3);
        // The most pressing first.
        assertThat(request.ingredients().getFirst().name()).isEqualTo("Calabacín");
        assertThat(offered.get("Sal").staple()).isTrue();
        assertThat(productEvents("recipe_generated")).isEqualTo(1);
    }

    @Test
    void foodOfUnknownContentsIsOfferedWhenTheHouseholdAvoidsNothing() throws Exception {
        stockFreeText("Salsa de la abuela", TODAY.plusDays(4));
        ArgumentCaptor<RecipeRequest> asked = ArgumentCaptor.forClass(RecipeRequest.class);
        when(ai.writeRecipe(asked.capture(), any())).thenReturn(Answer.not(Outcome.FAILED));

        generate(jorge, "").andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));

        assertThat(asked.getValue().ingredients()).extracting(Ingredient::name).contains("Salsa de la abuela");
        assertThat(asked.getValue().servings()).isEqualTo(2);
    }

    @Test
    void aRecipeThatNamesFoodItDoesNotUseIsNotShown() throws Exception {
        stock("Calabacín", "2", "UNIT", TODAY.plusDays(1));
        stock("Huevos", "6", "UNIT", TODAY.plusDays(10));
        when(ai.writeRecipe(any(), any())).thenAnswer(call -> {
            RecipeRequest request = call.getArgument(0);
            return Answer.ok(new WrittenRecipe(
                    "Calabacín a la crema",
                    "Con nata para cocinar.",
                    15,
                    Difficulty.EASY,
                    List.of(new UsedIngredient(key(request, "Calabacín"), Quantity.of("2", Unit.UNIT), false)),
                    // Eggs are at home but not in the recipe; cream is not even at home.
                    List.of("Saltea el calabacín.", "Añade la nata para cocinar y un huevo batido.")));
        });

        generate(jorge, "").andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
        assertThat(productEvents("recipe_generated")).isZero();
    }

    @Test
    void saysWhyThereIsNoRecipe() throws Exception {
        generate(jorge, "").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOTHING_TO_COOK_WITH"));

        stock("Calabacín", "2", "UNIT", TODAY.plusDays(1));
        when(ai.writeRecipe(any(), any())).thenReturn(Answer.not(Outcome.LIMIT_REACHED));
        generate(jorge, "").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("AI_LIMIT_REACHED"));

        when(ai.enabled()).thenReturn(false);
        mvc.perform(as(jorge, get("/api/v1/ai"))).andExpect(jsonPath("$.enabled").value(false));
        generate(jorge, "")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_NOT_CONFIGURED"));
    }

    @Test
    void onlyMembersAskAndServingsAreSensible() throws Exception {
        stock("Calabacín", "2", "UNIT", TODAY.plusDays(1));
        TestUser stranger = register("Extraño");

        generate(stranger, "").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
        generate(jorge, "{\"servings\": 0}").andExpect(status().isBadRequest());
        generate(jorge, "{\"servings\": 9}").andExpect(status().isBadRequest());
        verify(ai, never()).writeRecipe(any(), any());
        mvc.perform(as(jorge, get("/api/v1/ai"))).andExpect(jsonPath("$.enabled").value(true));
    }

    private static String key(RecipeRequest request, String name) {
        return request.ingredients().stream()
                .filter(ingredient -> ingredient.name().equals(name))
                .findFirst()
                .orElseThrow()
                .key();
    }

    private ResultActions generate(TestUser user, String body) throws Exception {
        var request = post("/api/v1/households/" + householdId + "/recipes/generated");
        return mvc.perform(as(user, body.isEmpty() ? request : json(request, body)));
    }

    private void stock(String food, String amount, String unit, LocalDate expiration) throws Exception {
        mvc.perform(as(jorge, json(post("/api/v1/households/" + householdId + "/inventory"), """
                        {"foodId": "%s", "name": "%s", "quantity": {"amount": %s, "unit": "%s"},
                         "storageLocation": "REFRIGERATOR", "expirationDate": "%s"}
                        """.formatted(foodId(food), food, amount, unit, expiration))))
                .andExpect(status().isCreated());
        if (expiration.isBefore(TODAY)) {
            // As the nightly sweep would.
            jdbc.update("update food_items set status = 'EXPIRED' where household_id = ?::uuid and name = ?", householdId, food);
        }
    }

    private void stockFreeText(String name, LocalDate expiration) throws Exception {
        mvc.perform(as(jorge, json(post("/api/v1/households/" + householdId + "/inventory"), """
                        {"name": "%s", "quantity": {"amount": 1, "unit": "UNIT"},
                         "storageLocation": "REFRIGERATOR", "expirationDate": "%s"}
                        """.formatted(name, expiration))))
                .andExpect(status().isCreated());
    }

    private long productEvents(String name) {
        return jdbc.queryForObject(
                "select count(*) from product_events where name = ? and household_id = ?::uuid",
                Long.class,
                name,
                householdId);
    }

    private String foodId(String name) throws Exception {
        String foods = mvc.perform(as(jorge, get("/api/v1/foods").param("q", name)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> ids = JsonPath.read(foods, "$[?(@.name == '" + name + "')].id");
        assertThat(ids).as("food " + name).hasSize(1);
        return ids.get(0);
    }
}
