package com.freezify.recipes;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.freezify.testsupport.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class DietApiTests extends ApiTestSupport {

    private static final String RECIPES = "/api/v1/recipes";
    private static final String CHICKEN = "Pollo a la plancha con brócoli";
    private static final String PASTA = "Pasta con calabacín y tomate";

    private TestUser jorge;
    private String householdId;

    @BeforeEach
    void householdWithOwner() throws Exception {
        jorge = register("Jorge");
        householdId = createHousehold(jorge, "Casa");
    }

    @Test
    void aHouseholdStartsWithoutRestrictions() throws Exception {
        mvc.perform(as(jorge, get(diet())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("NONE"))
                .andExpect(jsonPath("$.avoided", hasSize(0)));
        catalogOf(jorge).andExpect(jsonPath("$.totalItems").value(25));
    }

    @Test
    void recipesSayWhatTheyContain() throws Exception {
        String list = mvc.perform(as(jorge, get(RECIPES).param("size", "100")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> pastaId = JsonPath.read(list, "$.items[?(@.name == '" + PASTA + "')].id");

        mvc.perform(as(jorge, get(RECIPES + "/" + pastaId.get(0))))
                .andExpect(jsonPath("$.recipe.contains", contains("DAIRY", "EGG", "GLUTEN")));
        mvc.perform(as(jorge, get(RECIPES).param("q", "lentejas con verduras")))
                .andExpect(jsonPath("$.items[0].contains", hasSize(0)));
        mvc.perform(as(jorge, get(RECIPES).param("q", "lentejas con chorizo")))
                .andExpect(jsonPath("$.items[0].contains", contains("MEAT", "PORK", "DAIRY", "GLUTEN", "SOY")));
    }

    @Test
    void aVegetarianHouseholdIsNeverOfferedMeatOrFish() throws Exception {
        addFromCatalog("Pechuga de pollo", 300, "GRAM");
        addFromCatalog("Brócoli", 1, "UNIT");
        addFromCatalog("Huevos", 6, "UNIT");
        recommend().andExpect(jsonPath("$[*].recipe.name", hasItem(CHICKEN)));

        setDiet(jorge, "VEGETARIAN", "").andExpect(status().isOk());

        // The chicken is still in the fridge; the recipe is simply not offered.
        recommend()
                .andExpect(jsonPath("$[*].recipe.name", not(hasItem(CHICKEN))))
                .andExpect(jsonPath("$[*].recipe.name", hasItem("Tortilla de patatas")))
                .andExpect(jsonPath("$[*].recipe.contains[*]", not(hasItem("MEAT"))))
                .andExpect(jsonPath("$[*].recipe.contains[*]", not(hasItem("FISH"))))
                .andExpect(jsonPath("$[*].recipe.contains[*]", not(hasItem("SHELLFISH"))));
        catalogOf(jorge)
                .andExpect(jsonPath("$.totalItems").value(12))
                .andExpect(jsonPath("$.items[*].name", not(hasItem(CHICKEN))))
                .andExpect(jsonPath("$.items[*].name", not(hasItem("Ensalada de garbanzos"))))
                .andExpect(jsonPath("$.items[*].contains[*]", not(hasItem("MEAT"))))
                .andExpect(jsonPath("$.items[*].contains[*]", not(hasItem("FISH"))));
    }

    @Test
    void aVeganHouseholdIsOnlyOfferedWhatHasNothingFromAnimals() throws Exception {
        setDiet(jorge, "VEGAN", "").andExpect(status().isOk());

        catalogOf(jorge)
                .andExpect(jsonPath("$.items[*].name", contains("Espinacas con garbanzos", "Lentejas con verduras")));
    }

    @Test
    void whatTheHouseholdAvoidsIsLeftOutWhateverItsDiet() throws Exception {
        addFromCatalog("Pasta", 500, "GRAM");
        addFromCatalog("Calabacín", 1, "UNIT");
        recommend().andExpect(jsonPath("$[*].recipe.name", hasItem(PASTA)));

        setDiet(jorge, "NONE", "\"GLUTEN\"").andExpect(status().isOk());

        recommend()
                .andExpect(jsonPath("$[*].recipe.name", not(hasItem(PASTA))))
                .andExpect(jsonPath("$[*].recipe.contains[*]", not(hasItem("GLUTEN"))));
        catalogOf(jorge)
                .andExpect(jsonPath("$.totalItems").value(15))
                .andExpect(jsonPath("$.items[*].contains[*]", not(hasItem("GLUTEN"))))
                // Hidden gluten counts too: the flour of the hake sauce, the soy sauce of the fried rice.
                .andExpect(jsonPath("$.items[*].name", not(hasItem("Merluza con guisantes"))))
                .andExpect(jsonPath("$.items[*].name", not(hasItem("Arroz salteado con gambas"))));
    }

    @Test
    void aDietAndWhatIsAvoidedAddUp() throws Exception {
        setDiet(jorge, "VEGETARIAN", "\"EGG\", \"DAIRY\"").andExpect(status().isOk());

        // Vegetarian without eggs or dairy leaves the same as vegan.
        catalogOf(jorge)
                .andExpect(jsonPath("$.items[*].name", contains("Espinacas con garbanzos", "Lentejas con verduras")));
    }

    @Test
    void filtersCombineWithTheRestrictions() throws Exception {
        setDiet(jorge, "VEGETARIAN", "").andExpect(status().isOk());

        mvc.perform(as(jorge, get(RECIPES).param("household", householdId).param("course", "BREAKFAST")))
                .andExpect(jsonPath(
                        "$.items[*].name",
                        contains("Batido de plátano", "Tostadas de aguacate y huevo", "Yogur con fruta y cereales")));
        // Without the household, the catalog is the same for everyone.
        mvc.perform(as(jorge, get(RECIPES).param("size", "100"))).andExpect(jsonPath("$.totalItems").value(25));
    }

    @Test
    void restrictionsBelongToTheHouseholdAndAnyMemberCanChangeThem() throws Exception {
        TestUser ana = register("Ana");
        join(ana, invite(jorge, householdId)).andExpect(status().isOk());

        setDiet(ana, "VEGETARIAN", "\"SOY\", \"GLUTEN\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("VEGETARIAN"))
                // Always in the same order, whatever order they were sent in.
                .andExpect(jsonPath("$.avoided", contains("GLUTEN", "SOY")));

        mvc.perform(as(jorge, get(diet())))
                .andExpect(jsonPath("$.type").value("VEGETARIAN"))
                .andExpect(jsonPath("$.avoided", contains("GLUTEN", "SOY")));

        // Replacing them replaces everything.
        setDiet(jorge, "NONE", "").andExpect(jsonPath("$.avoided", hasSize(0)));
        mvc.perform(as(ana, get(diet()))).andExpect(jsonPath("$.type").value("NONE"));
    }

    @Test
    void nobodySeesOrChangesTheRestrictionsOfAHouseholdTheyDoNotBelongTo() throws Exception {
        TestUser stranger = register("Marta");
        setDiet(jorge, "VEGAN", "").andExpect(status().isOk());

        mvc.perform(as(stranger, get(diet())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
        setDiet(stranger, "NONE", "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
        mvc.perform(as(stranger, get(RECIPES).param("household", householdId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
        mvc.perform(get(diet())).andExpect(status().isUnauthorized());

        mvc.perform(as(jorge, get(diet()))).andExpect(jsonPath("$.type").value("VEGAN"));
    }

    @Test
    void invalidRestrictionsAreRejected() throws Exception {
        setDiet(jorge, "CARNIVORE", "").andExpect(status().isBadRequest());
        setDiet(jorge, "NONE", "\"PLASTIC\"").andExpect(status().isBadRequest());
        mvc.perform(as(jorge, json(put(diet()), """
                        {"type": "VEGAN"}
                        """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private ResultActions setDiet(TestUser user, String type, String avoided) throws Exception {
        return mvc.perform(as(user, json(put(diet()), """
                {"type": "%s", "avoided": [%s]}
                """.formatted(type, avoided))));
    }

    /** The catalog as the household sees it, in full. */
    private ResultActions catalogOf(TestUser user) throws Exception {
        return mvc.perform(as(user, get(RECIPES).param("household", householdId).param("size", "100")))
                .andExpect(status().isOk());
    }

    private ResultActions recommend() throws Exception {
        return mvc.perform(as(
                        jorge,
                        get("/api/v1/households/" + householdId + "/recipes/recommendations")
                                .param("limit", "50")))
                .andExpect(status().isOk());
    }

    /** Kept somewhere without shelf-life rules, so that no date is estimated. */
    private void addFromCatalog(String food, double amount, String unit) throws Exception {
        String foods = mvc.perform(as(jorge, get("/api/v1/foods").param("q", food)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> ids = JsonPath.read(foods, "$[?(@.name == '" + food + "')].id");
        mvc.perform(as(jorge, json(post("/api/v1/households/" + householdId + "/inventory"), """
                        {"foodId": "%s", "name": "%s", "quantity": {"amount": %s, "unit": "%s"},
                         "storageLocation": "OTHER"}
                        """.formatted(ids.get(0), food, amount, unit))))
                .andExpect(status().isCreated());
    }

    private String diet() {
        return "/api/v1/households/" + householdId + "/diet";
    }
}
