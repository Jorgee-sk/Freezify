package com.freezify.recipes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.freezify.testsupport.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class RecipeApiTests extends ApiTestSupport {

    private static final String RECIPES = "/api/v1/recipes";
    private static final String PASTA = "Pasta con calabacín y tomate";

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser jorge;
    private String householdId;

    @BeforeEach
    void householdWithOwner() throws Exception {
        jorge = register("Jorge");
        householdId = createHousehold(jorge, "Casa");
    }

    @Test
    void theCatalogListsEveryRecipeByNameInTheLanguageAsked() throws Exception {
        mvc.perform(as(jorge, get(RECIPES).param("size", "100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(25))
                .andExpect(jsonPath("$.items", hasSize(25)))
                .andExpect(jsonPath("$.items[0].name").value("Arroz con pollo y verduras"))
                .andExpect(jsonPath("$.items[0].description").value("Arroz meloso de diario con lo que haya en la nevera."))
                .andExpect(jsonPath("$.items[0].servings").value(2))
                .andExpect(jsonPath("$.items[0].prepMinutes").value(15))
                .andExpect(jsonPath("$.items[0].cookMinutes").value(30))
                .andExpect(jsonPath("$.items[0].totalMinutes").value(45))
                .andExpect(jsonPath("$.items[0].difficulty").value("MEDIUM"))
                .andExpect(jsonPath("$.items[0].course").value("MAIN"));
        mvc.perform(as(jorge, get(RECIPES).param("lang", "en").param("size", "3")))
                .andExpect(jsonPath("$.totalPages").value(9))
                .andExpect(jsonPath(
                        "$.items[*].name",
                        contains("Avocado and egg toast", "Baked salmon with potatoes", "Banana smoothie")));
    }

    @Test
    void theCatalogCanBeFiltered() throws Exception {
        // Without accents, in any case.
        mvc.perform(as(jorge, get(RECIPES).param("q", "CALABACIN")))
                .andExpect(jsonPath("$.items[*].name", contains("Crema de calabacín", PASTA)));
        mvc.perform(as(jorge, get(RECIPES).param("maxMinutes", "10").param("size", "100")))
                .andExpect(jsonPath("$.items[*].totalMinutes", everyItem(lessThanOrEqualTo(10))))
                .andExpect(jsonPath("$.items[*].name", hasItem("Batido de plátano")))
                .andExpect(jsonPath("$.items[*].name", not(hasItem("Tortilla de patatas"))));
        mvc.perform(as(jorge, get(RECIPES).param("course", "DESSERT")))
                .andExpect(jsonPath("$.items[*].name", contains("Torrijas")));
        mvc.perform(as(jorge, get(RECIPES).param("difficulty", "MEDIUM").param("course", "MAIN")))
                .andExpect(jsonPath(
                        "$.items[*].name",
                        contains(
                                "Arroz con pollo y verduras",
                                "Arroz salteado con gambas",
                                "Merluza con guisantes",
                                "Tortilla de patatas")));
        mvc.perform(as(jorge, get(RECIPES).param("q", "no existe")))
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void aRecipeComesWithNormalizedIngredientsAndSteps() throws Exception {
        String id = recipeId(PASTA);

        mvc.perform(as(jorge, get(RECIPES + "/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipe.name").value(PASTA))
                .andExpect(jsonPath("$.ingredients", hasSize(7)))
                .andExpect(jsonPath("$.ingredients[0].name").value("Pasta"))
                .andExpect(jsonPath("$.ingredients[0].foodId").value(foodId("Pasta")))
                .andExpect(jsonPath("$.ingredients[0].amount").value(200))
                .andExpect(jsonPath("$.ingredients[0].unit").value("GRAM"))
                .andExpect(jsonPath("$.ingredients[0].staple").value(false))
                .andExpect(jsonPath("$.ingredients[6].name").value("Sal"))
                .andExpect(jsonPath("$.ingredients[6].staple").value(true))
                .andExpect(jsonPath("$.steps", hasSize(4)))
                .andExpect(jsonPath("$.steps[0]").value("Cuece la pasta en agua con sal."));
        mvc.perform(as(jorge, get(RECIPES + "/" + id).param("lang", "en")))
                .andExpect(jsonPath("$.recipe.name").value("Pasta with zucchini and tomato"))
                .andExpect(jsonPath("$.ingredients[1].name").value("Zucchini"))
                .andExpect(jsonPath("$.steps[0]").value("Boil the pasta in salted water."));
        assertThat(jdbc.queryForObject(
                        "select count(*) from product_events where name = 'recipe_viewed' and user_id = ?::uuid",
                        Long.class,
                        jorge.id()))
                .isEqualTo(2);
    }

    @Test
    void everySeededRecipeHasIngredientsAndStepsInBothLanguages() throws Exception {
        String list = mvc.perform(as(jorge, get(RECIPES).param("size", "100")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> ids = JsonPath.read(list, "$.items[*].id");

        for (String id : ids) {
            for (String language : List.of("es", "en")) {
                String detail = mvc.perform(as(jorge, get(RECIPES + "/" + id).param("lang", language)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
                List<String> names = JsonPath.read(detail, "$.ingredients[*].name");
                List<String> steps = JsonPath.read(detail, "$.steps");
                List<Boolean> staples = JsonPath.read(detail, "$.ingredients[*].staple");
                assertThat(names).as(detail).isNotEmpty().allSatisfy(name -> assertThat(name).isNotBlank());
                assertThat(steps).as(detail).hasSizeGreaterThanOrEqualTo(2).allSatisfy(step -> assertThat(step)
                        .isNotBlank());
                // Something has to be looked for in the inventory, or the recipe could never be recommended.
                assertThat(staples).as(detail).contains(false);
            }
        }
    }

    @Test
    void unknownRecipesAndBadRequestsAreRejected() throws Exception {
        mvc.perform(as(jorge, get(RECIPES + "/00000000-0000-0000-0000-000000000000")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_FOUND"));
        mvc.perform(as(jorge, get(RECIPES).param("lang", "fr")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get(RECIPES)).andExpect(status().isUnauthorized());
        mvc.perform(get(recommendations())).andExpect(status().isUnauthorized());
    }

    @Test
    void recommendsWhatUsesTheFoodAtHomeAndSaysWhy() throws Exception {
        addFromCatalog("Calabacín", 1, "UNIT", inDays(2));
        addFromCatalog("Pasta", 500, "GRAM", inDays(300));
        addFromCatalog("Tomate", 3, "UNIT", null);

        recommend()
                .andExpect(jsonPath("$[0].recipe.name").value(PASTA))
                .andExpect(jsonPath("$[0].recipe.totalMinutes").value(25))
                .andExpect(jsonPath("$[0].factors.ingredientMatch").value(0.75))
                .andExpect(jsonPath("$[0].factors.novelty").value(1.0))
                .andExpect(jsonPath("$[0].daysSinceCooked").doesNotExist())
                // Pasta: enough, with plenty of time.
                .andExpect(jsonPath("$[0].ingredients[0].name").value("Pasta"))
                .andExpect(jsonPath("$[0].ingredients[0].availability").value("ENOUGH"))
                .andExpect(jsonPath("$[0].ingredients[0].priority").value("OK"))
                // Zucchini: the reason for the recommendation.
                .andExpect(jsonPath("$[0].ingredients[1].name").value("Calabacín"))
                .andExpect(jsonPath("$[0].ingredients[1].availability").value("ENOUGH"))
                .andExpect(jsonPath("$[0].ingredients[1].expirationDate").value(inDays(2)))
                .andExpect(jsonPath("$[0].ingredients[1].daysUntilExpiration").value(2))
                .andExpect(jsonPath("$[0].ingredients[1].priority").value("URGENT"))
                .andExpect(jsonPath("$[0].ingredients[1].estimated").value(false))
                // Tomato: no date, so nothing is said about when it expires.
                .andExpect(jsonPath("$[0].ingredients[2].availability").value("ENOUGH"))
                .andExpect(jsonPath("$[0].ingredients[2].expirationDate").doesNotExist())
                // Mozzarella: the only thing to buy.
                .andExpect(jsonPath("$[0].ingredients[3].name").value("Mozzarella"))
                .andExpect(jsonPath("$[0].ingredients[3].availability").value("MISSING"))
                // Garlic, oil and salt are assumed.
                .andExpect(jsonPath("$[0].ingredients[4].availability").value("ASSUMED"))
                .andExpect(jsonPath("$[0].ingredients[4].staple").value(true));
    }

    @Test
    void onlyRecipesThatUseSomethingAtHomeAreRecommended() throws Exception {
        recommend().andExpect(jsonPath("$", hasSize(0)));

        addFromCatalog("Plátano", 2, "UNIT", null);

        recommend()
                .andExpect(jsonPath("$[*].recipe.name", contains("Batido de plátano", "Yogur con fruta y cereales")));
    }

    @Test
    void foodThatExpiresSoonerMakesItsRecipeComeFirst() throws Exception {
        // The mushroom recipe is better covered and quicker, but the broccoli cannot wait.
        addFromCatalog("Champiñones", 250, "GRAM", inDays(9));
        addFromCatalog("Brócoli", 1, "UNIT", inDays(1));

        recommend()
                .andExpect(jsonPath("$[0].recipe.name").value("Pollo a la plancha con brócoli"))
                .andExpect(jsonPath("$[0].factors.expiryUrgency").value(0.5));
    }

    @Test
    void foodPastItsDateIsNeverOfferedForCooking() throws Exception {
        addFromCatalog("Calabacín", 1, "UNIT", inDays(-1));

        recommend().andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void saysWhenThereIsLessThanNeededOrWhenQuantitiesCannotBeCompared() throws Exception {
        addFromCatalog("Pasta", 100, "GRAM", null);
        // The recipe asks for two tomatoes; half a kilo cannot be compared with that.
        addFromCatalog("Tomate", 0.5, "KILOGRAM", null);
        addFromCatalog("Mozzarella", 0.25, "KILOGRAM", null);

        recommendation(PASTA)
                .has(".ingredients[0].availability", "PARTIAL")
                .has(".ingredients[2].availability", "UNKNOWN_QUANTITY")
                .has(".ingredients[3].availability", "ENOUGH")
                .has(".factors.ingredientMatch", 0.625);
    }

    @Test
    void addsUpSeveralItemsOfTheSameFoodAndKeepsTheSoonestDate() throws Exception {
        addFromCatalog("Pasta", 120, "GRAM", inDays(30));
        addFromCatalog("Pasta", 100, "GRAM", inDays(4));

        recommendation(PASTA)
                .has(".ingredients[0].availability", "ENOUGH")
                .has(".ingredients[0].expirationDate", inDays(4))
                .has(".ingredients[0].priority", "SOON");
    }

    @Test
    void foodTypedByHandIsMatchedByItsName() throws Exception {
        mvc.perform(as(jorge, json(post(inventory()), """
                        {"name": "calabacin", "quantity": {"amount": 1, "unit": "UNIT"}, "storageLocation": "REFRIGERATOR"}
                        """)))
                .andExpect(status().isCreated());

        recommendation(PASTA).has(".ingredients[1].availability", "ENOUGH");
    }

    @Test
    void anEstimatedDateIsReportedAsAnEstimate() throws Exception {
        // Fresh salmon without a date gets one from the shelf-life rules.
        addWithoutDateInTheFridge("Salmón", 300, "GRAM");

        recommendation("Salmón al horno con patatas")
                .has(".ingredients[0].name", "Salmón")
                .has(".ingredients[0].estimated", true)
                .has(".ingredients[0].priority", "URGENT");
    }

    @Test
    void aRecipeCookedTodayDropsInTheRanking() throws Exception {
        addFromCatalog("Plátano", 2, "UNIT", null);
        String smoothie = recipeId("Batido de plátano");
        recommend().andExpect(jsonPath("$[0].recipe.name").value("Batido de plátano"));

        mvc.perform(as(jorge, post(cooked(smoothie)))).andExpect(status().isNoContent());
        // Saying it again changes nothing.
        mvc.perform(as(jorge, post(cooked(smoothie)))).andExpect(status().isNoContent());

        recommend()
                .andExpect(jsonPath("$[*].recipe.name", contains("Yogur con fruta y cereales", "Batido de plátano")))
                .andExpect(jsonPath("$[1].daysSinceCooked").value(0))
                .andExpect(jsonPath("$[1].factors.novelty").value(0.0))
                .andExpect(jsonPath("$[0].daysSinceCooked").doesNotExist());
        assertThat(jdbc.queryForObject(
                        "select count(*) from product_events where name = 'recipe_cooked' and household_id = ?::uuid",
                        Long.class,
                        householdId))
                .isEqualTo(1);

        // A week later it is half way to being new again.
        clock.advance(Duration.ofDays(7));
        recommendation("Batido de plátano").has(".daysSinceCooked", 7).has(".factors.novelty", 0.5);
    }

    @Test
    void theNumberOfRecommendationsCanBeLimited() throws Exception {
        addFromCatalog("Huevos", 12, "UNIT", null);
        addFromCatalog("Cebolla", 3, "UNIT", null);

        // Fifteen recipes use eggs or onion; ten are returned unless more are asked for.
        mvc.perform(as(jorge, get(recommendations()))).andExpect(jsonPath("$", hasSize(10)));
        recommend().andExpect(jsonPath("$", hasSize(15)));
        mvc.perform(as(jorge, get(recommendations()).param("limit", "2"))).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(as(jorge, get(recommendations()).param("limit", "51")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void recommendationsFollowTheLanguageAsked() throws Exception {
        addFromCatalog("Plátano", 2, "UNIT", null);

        mvc.perform(as(jorge, get(recommendations()).param("lang", "en")))
                .andExpect(jsonPath("$[0].recipe.name").value("Banana smoothie"))
                .andExpect(jsonPath("$[0].ingredients[0].name").value("Banana"));
    }

    @Test
    void nobodyGetsRecommendationsFromAHouseholdTheyDoNotBelongTo() throws Exception {
        TestUser stranger = register("Marta");
        addFromCatalog("Plátano", 2, "UNIT", null);
        String smoothie = recipeId("Batido de plátano");

        mvc.perform(as(stranger, get(recommendations())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
        mvc.perform(as(stranger, post(cooked(smoothie))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
        mvc.perform(as(jorge, post(cooked("00000000-0000-0000-0000-000000000000"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_FOUND"));

        // The stranger's attempt left no trace.
        recommendation("Batido de plátano").hasNo(".daysSinceCooked");
    }

    private ResultActions recommend() throws Exception {
        return mvc.perform(as(jorge, get(recommendations()).param("limit", "50"))).andExpect(status().isOk());
    }

    /** Checks one recommendation, wherever it is in the ranking; paths are relative to it. */
    private Recommendation recommendation(String recipeName) throws Exception {
        String all = recommend().andReturn().getResponse().getContentAsString();
        List<String> names = JsonPath.read(all, "$[*].recipe.name");
        assertThat(names).as("recommended recipes").containsOnlyOnce(recipeName);
        return new Recommendation(all, "$[" + names.indexOf(recipeName) + "]");
    }

    private record Recommendation(String json, String root) {

        Recommendation has(String path, Object expected) {
            Object actual = JsonPath.read(json, root + path);
            assertThat(actual).as(path).isEqualTo(expected);
            return this;
        }

        Recommendation hasNo(String path) {
            Object actual;
            try {
                actual = JsonPath.read(json, root + path);
            } catch (com.jayway.jsonpath.PathNotFoundException absent) {
                return this;
            }
            assertThat(actual).as(path).isNull();
            return this;
        }
    }

    private String recipeId(String name) throws Exception {
        String list = mvc.perform(as(jorge, get(RECIPES).param("size", "100")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> ids = JsonPath.read(list, "$.items[?(@.name == '" + name + "')].id");
        assertThat(ids).as("recipe " + name).hasSize(1);
        return ids.get(0);
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

    /**
     * @param expirationDate {@code null} for food without a date. It is then kept somewhere without shelf-life
     *     rules, so that the application does not estimate one
     */
    private void addFromCatalog(String food, double amount, String unit, String expirationDate) throws Exception {
        add(food, amount, unit, expirationDate == null ? "OTHER" : "REFRIGERATOR", expirationDate);
    }

    /** The application estimates the date from its shelf-life rules. */
    private void addWithoutDateInTheFridge(String food, double amount, String unit) throws Exception {
        add(food, amount, unit, "REFRIGERATOR", null);
    }

    private void add(String food, double amount, String unit, String storage, String expirationDate) throws Exception {
        mvc.perform(as(jorge, json(post(inventory()), """
                        {"foodId": "%s", "name": "%s", "quantity": {"amount": %s, "unit": "%s"},
                         "storageLocation": "%s", "expirationDate": %s}
                        """.formatted(
                                foodId(food),
                                food,
                                amount,
                                unit,
                                storage,
                                expirationDate == null ? "null" : "\"" + expirationDate + "\""))))
                .andExpect(status().isCreated());
    }

    private String inventory() {
        return "/api/v1/households/" + householdId + "/inventory";
    }

    private String recommendations() {
        return "/api/v1/households/" + householdId + "/recipes/recommendations";
    }

    private String cooked(String recipeId) {
        return "/api/v1/households/" + householdId + "/recipes/" + recipeId + "/cooked";
    }

    private static String inDays(int days) {
        return LocalDate.now(ZoneId.of("Europe/Madrid")).plusDays(days).toString();
    }
}
