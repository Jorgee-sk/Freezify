package com.freezify.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.freezify.testsupport.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class ShoppingListApiTests extends ApiTestSupport {

    private static final String CHICKEN = "Pollo a la plancha con brócoli";
    private static final String CHICKEN_RICE = "Arroz con pollo y verduras";

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Madrid"));
    private static final LocalDate NEXT_MONDAY = TODAY.with(TemporalAdjusters.next(DayOfWeek.MONDAY));

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
    void aHouseholdStartsWithAnEmptyList() throws Exception {
        list(jorge).andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void anythingCanBePutOnTheList() throws Exception {
        add(jorge, """
                        {"name": "Pilas para el mando"}
                        """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Pilas para el mando"))
                .andExpect(jsonPath("$.foodId").isEmpty())
                .andExpect(jsonPath("$.category").value("OTHER"))
                .andExpect(jsonPath("$.quantity").isEmpty())
                .andExpect(jsonPath("$.origin").value("MANUAL"))
                .andExpect(jsonPath("$.checked").value(false));
        add(jorge, """
                        {"foodId": "%s", "quantity": {"amount": 1, "unit": "LITER"}}
                        """.formatted(foodId("Leche")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Leche"))
                .andExpect(jsonPath("$.category").value("DAIRY"))
                .andExpect(jsonPath("$.quantity.amount").value(1))
                .andExpect(jsonPath("$.quantity.unit").value("LITER"));

        list(jorge).andExpect(jsonPath("$.items", hasSize(2)));
        // Catalog foods are named in the language of whoever reads the list.
        mvc.perform(as(jorge, get(shoppingList()).param("lang", "en")))
                .andExpect(jsonPath("$.items[*].name", hasItem("Milk")))
                .andExpect(jsonPath("$.items[*].name", hasItem("Pilas para el mando")));
    }

    @Test
    void theListComesAisleByAisle() throws Exception {
        add(jorge, """
                {"name": "Papel de cocina"}
                """);
        add(jorge, foodLine("Pechuga de pollo", null));
        add(jorge, foodLine("Tomate", null));
        add(jorge, foodLine("Leche", null));
        add(jorge, foodLine("Calabacín", null));

        list(jorge)
                .andExpect(jsonPath(
                        "$.items[*].name", contains("Calabacín", "Tomate", "Leche", "Pechuga de pollo", "Papel de cocina")))
                .andExpect(jsonPath(
                        "$.items[*].category", contains("VEGETABLES", "VEGETABLES", "DAIRY", "MEAT", "OTHER")));
    }

    @Test
    void addingAFoodAlreadyOnTheListAddsToItsAmount() throws Exception {
        String first = id(add(jorge, foodLine("Pechuga de pollo", "{\"amount\": 200, \"unit\": \"GRAM\"}")));

        String second = id(add(jorge, foodLine("Pechuga de pollo", "{\"amount\": 0.3, \"unit\": \"KILOGRAM\"}")));

        assertThat(second).isEqualTo(first);
        list(jorge)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity.amount").value(500))
                .andExpect(jsonPath("$.items[0].quantity.unit").value("GRAM"));
        // Counted in pieces it cannot be added to grams: it gets its own line.
        add(jorge, foodLine("Pechuga de pollo", "{\"amount\": 2, \"unit\": \"UNIT\"}"));
        list(jorge).andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void aLineIsTickedOffWhenBoughtAndCanBeUnticked() throws Exception {
        String milk = id(add(jorge, foodLine("Leche", null)));

        check(jorge, milk, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checked").value(true))
                .andExpect(jsonPath("$.checkedAt").isNotEmpty());
        check(jorge, milk, false)
                .andExpect(jsonPath("$.checked").value(false))
                .andExpect(jsonPath("$.checkedAt").isEmpty());
    }

    @Test
    void aLineCanBeChangedAndRemoved() throws Exception {
        String line = id(add(jorge, """
                {"name": "Pan"}
                """));

        mvc.perform(as(jorge, json(put(item(line)), """
                        {"name": "Pan integral", "category": "BAKERY", "quantity": {"amount": 2, "unit": "UNIT"}}
                        """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pan integral"))
                .andExpect(jsonPath("$.category").value("BAKERY"))
                .andExpect(jsonPath("$.quantity.amount").value(2));

        mvc.perform(as(jorge, delete(item(line)))).andExpect(status().isNoContent());
        list(jorge).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(as(jorge, delete(item(line))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOPPING_ITEM_NOT_FOUND"));
    }

    @Test
    void whatWasBoughtCanBeTakenOffAtOnce() throws Exception {
        String milk = id(add(jorge, foodLine("Leche", null)));
        String bread = id(add(jorge, foodLine("Pan", null)));
        add(jorge, foodLine("Tomate", null));
        check(jorge, milk, true);
        check(jorge, bread, true);

        mvc.perform(as(jorge, delete(shoppingList() + "/items/checked")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removed").value(2));

        list(jorge).andExpect(jsonPath("$.items[*].name", contains("Tomate")));
    }

    @Test
    void theListIsFilledWithWhatThePlannedMealsLack() throws Exception {
        // The example of the specification, with the recipes of the catalog: two chicken dishes and some chicken
        // at home.
        addToInventory("Pechuga de pollo", 200, "GRAM");
        addToInventory("Brócoli", 1, "UNIT");
        plan(NEXT_MONDAY, "LUNCH", CHICKEN);
        plan(NEXT_MONDAY, "DINNER", CHICKEN_RICE);

        fill(jorge, NEXT_MONDAY).andExpect(status().isOk()).andExpect(jsonPath("$.lines").value(7));

        String list = list(jorge).andReturn().getResponse().getContentAsString();
        // 300 g + 300 g of chicken, less the 200 g at home.
        assertThat(line(list, "Pechuga de pollo"))
                .containsEntry("origin", "PLAN")
                .containsEntry("neededOn", NEXT_MONDAY.toString());
        assertQuantity(line(list, "Pechuga de pollo"), 400, "GRAM");
        assertQuantity(line(list, "Limón"), 1, "UNIT");
        assertQuantity(line(list, "Arroz"), 200, "GRAM");
        // What is at home is not asked for, and staples (oil, salt) never are.
        List<String> names = JsonPath.read(list, "$.items[*].name");
        assertThat(names)
                .containsExactlyInAnyOrder(
                        "Pechuga de pollo",
                        "Limón",
                        "Arroz",
                        "Pimiento rojo",
                        "Cebolla",
                        "Guisantes congelados",
                        "Tomate triturado")
                .doesNotContain("Brócoli", "Aceite de oliva", "Sal");
        assertThat(productEvents("shopping_list_created")).isEqualTo(1);
    }

    @Test
    void fillingAgainFollowsThePlanWithoutRepeatingLines() throws Exception {
        plan(NEXT_MONDAY, "LUNCH", CHICKEN);
        fill(jorge, NEXT_MONDAY).andExpect(jsonPath("$.lines").value(3));
        fill(jorge, NEXT_MONDAY).andExpect(jsonPath("$.lines").value(3));
        list(jorge).andExpect(jsonPath("$.items", hasSize(3)));

        // The meal changes: its lines change with it.
        plan(NEXT_MONDAY, "LUNCH", "Lentejas con verduras");
        fill(jorge, NEXT_MONDAY);
        list(jorge)
                .andExpect(jsonPath("$.items[*].name", not(hasItem("Pechuga de pollo"))))
                .andExpect(jsonPath("$.items[*].name", hasItem("Lentejas")));
        // Filling the list again does not count as creating it.
        assertThat(productEvents("shopping_list_created")).isEqualTo(1);
    }

    @Test
    void whatIsAlreadyOnTheListOrBoughtIsNotAskedForTwice() throws Exception {
        plan(NEXT_MONDAY, "LUNCH", CHICKEN);
        // Someone already wrote down the lemon, and 100 g of chicken.
        add(jorge, foodLine("Limón", "{\"amount\": 1, \"unit\": \"UNIT\"}"));
        add(jorge, foodLine("Pechuga de pollo", "{\"amount\": 100, \"unit\": \"GRAM\"}"));

        fill(jorge, NEXT_MONDAY).andExpect(jsonPath("$.lines").value(2));

        String list = list(jorge).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> chicken = JsonPath.read(list, "$.items[?(@.name == 'Pechuga de pollo' && @.origin == 'PLAN')]");
        assertThat(chicken).hasSize(1);
        assertQuantity(chicken.get(0), 200, "GRAM");
        List<Object> lemons = JsonPath.read(list, "$.items[?(@.name == 'Limón')]");
        assertThat(lemons).hasSize(1);

        // Once bought, a line of the plan stays bought: filling again does not put it back.
        String broccoli = JsonPath.<List<String>>read(list, "$.items[?(@.name == 'Brócoli')].id").get(0);
        check(jorge, broccoli, true);
        fill(jorge, NEXT_MONDAY);
        List<Object> broccolis = JsonPath.read(
                list(jorge).andReturn().getResponse().getContentAsString(), "$.items[?(@.name == 'Brócoli')]");
        assertThat(broccolis).hasSize(1);
    }

    @Test
    void aLineOfThePlanThatSomeoneChangesIsTheirs() throws Exception {
        plan(NEXT_MONDAY, "LUNCH", CHICKEN);
        fill(jorge, NEXT_MONDAY);
        String list = list(jorge).andReturn().getResponse().getContentAsString();
        String chicken = JsonPath.<List<String>>read(list, "$.items[?(@.name == 'Pechuga de pollo')].id").get(0);

        mvc.perform(as(jorge, json(put(item(chicken)), foodLine("Pechuga de pollo", "{\"amount\": 1, \"unit\": \"KILOGRAM\"}"))))
                .andExpect(jsonPath("$.origin").value("MANUAL"))
                .andExpect(jsonPath("$.neededOn").isEmpty());

        // A kilo already covers the 300 g the plan needs: no second chicken line.
        fill(jorge, NEXT_MONDAY);
        List<Object> lines = JsonPath.read(
                list(jorge).andReturn().getResponse().getContentAsString(), "$.items[?(@.name == 'Pechuga de pollo')]");
        assertThat(lines).hasSize(1);
    }

    @Test
    void eachWeekKeepsItsOwnLines() throws Exception {
        plan(NEXT_MONDAY, "LUNCH", CHICKEN);
        plan(NEXT_MONDAY.plusDays(7), "LUNCH", "Lentejas con verduras");
        fill(jorge, NEXT_MONDAY);
        fill(jorge, NEXT_MONDAY.plusDays(7));
        fill(jorge, NEXT_MONDAY);

        list(jorge)
                .andExpect(jsonPath("$.items[*].name", hasItem("Pechuga de pollo")))
                .andExpect(jsonPath("$.items[*].name", hasItem("Lentejas")));
    }

    @Test
    void aWeekThatIsOverCannotFillTheList() throws Exception {
        fill(jorge, TODAY.minusDays(7))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WEEK_IN_THE_PAST"));
        fill(jorge, NEXT_MONDAY).andExpect(jsonPath("$.lines").value(0));
    }

    @Test
    void whatIsAskedForMustMakeSense() throws Exception {
        add(jorge, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        add(jorge, """
                        {"name": "   "}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        add(jorge, """
                        {"foodId": "00000000-0000-0000-0000-000000000000"}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FOOD_NOT_FOUND"));
        add(jorge, """
                        {"name": "Pan", "quantity": {"amount": 0, "unit": "UNIT"}}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(as(jorge, json(put(shoppingList() + "/items/" + java.util.UUID.randomUUID() + "/checked"), """
                        {"checked": true}
                        """)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOPPING_ITEM_NOT_FOUND"));
        list(jorge).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void anyMemberUsesTheListAndSeesWhatOthersTickOff() throws Exception {
        TestUser partner = register("Lucía");
        join(partner, invite(jorge, householdId)).andExpect(status().isOk());
        MvcResult stream = mvc.perform(as(partner, get("/api/v1/households/" + householdId + "/events")))
                .andExpect(request().asyncStarted())
                .andReturn();

        String milk = id(add(jorge, foodLine("Leche", null)));
        assertThat(changes(stream)).isEqualTo(1);
        check(partner, milk, true).andExpect(status().isOk());
        assertThat(changes(stream)).isEqualTo(2);
        list(jorge).andExpect(jsonPath("$.items[0].checked").value(true));
    }

    @Test
    void theListOfAHouseholdIsOutOfReachForEveryoneElse() throws Exception {
        TestUser stranger = register("Desconocido");
        String milk = id(add(jorge, foodLine("Leche", null)));
        String otherHousehold = createHousehold(stranger, "Otra casa");

        list(stranger).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
        add(stranger, foodLine("Leche", null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
        check(stranger, milk, true).andExpect(status().isNotFound());
        mvc.perform(as(stranger, delete(item(milk)))).andExpect(status().isNotFound());
        fill(stranger, NEXT_MONDAY).andExpect(status().isNotFound());
        // Nor through a household of their own: the line belongs to another one.
        mvc.perform(as(stranger, json(
                        put("/api/v1/households/" + otherHousehold + "/shopping-list/items/" + milk + "/checked"),
                        "{\"checked\": true}")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOPPING_ITEM_NOT_FOUND"));

        list(jorge).andExpect(jsonPath("$.items[0].checked").value(false));
    }

    private ResultActions list(TestUser user) throws Exception {
        return mvc.perform(as(user, get(shoppingList())));
    }

    private ResultActions add(TestUser user, String body) throws Exception {
        return mvc.perform(as(user, json(post(shoppingList() + "/items"), body)));
    }

    private ResultActions check(TestUser user, String itemId, boolean checked) throws Exception {
        return mvc.perform(as(user, json(put(item(itemId) + "/checked"), """
                {"checked": %s}
                """.formatted(checked))));
    }

    private ResultActions fill(TestUser user, LocalDate week) throws Exception {
        return mvc.perform(as(user, json(post(shoppingList() + "/from-plan"), """
                {"week": "%s"}
                """.formatted(week))));
    }

    private String foodLine(String food, String quantity) throws Exception {
        return """
                {"foodId": "%s", "quantity": %s}
                """.formatted(foodId(food), quantity == null ? "null" : quantity);
    }

    private void plan(LocalDate date, String slot, String recipe) throws Exception {
        mvc.perform(as(jorge, json(put("/api/v1/households/" + householdId + "/meal-plan/" + date + "/" + slot), """
                        {"recipeId": "%s"}
                        """.formatted(recipeId(recipe)))))
                .andExpect(status().isNoContent());
    }

    /** Kept somewhere without shelf-life rules, so that no date is estimated. */
    private void addToInventory(String food, double amount, String unit) throws Exception {
        mvc.perform(as(jorge, json(post("/api/v1/households/" + householdId + "/inventory"), """
                        {"foodId": "%s", "name": "%s", "quantity": {"amount": %s, "unit": "%s"},
                         "storageLocation": "OTHER"}
                        """.formatted(foodId(food), food, amount, unit))))
                .andExpect(status().isCreated());
    }

    private static Map<String, Object> line(String list, String name) {
        List<Map<String, Object>> found = JsonPath.read(list, "$.items[?(@.name == '" + name + "')]");
        assertThat(found).as("line " + name).hasSize(1);
        return found.get(0);
    }

    @SuppressWarnings("unchecked")
    private static void assertQuantity(Map<String, Object> line, double amount, String unit) {
        Map<String, Object> quantity = (Map<String, Object>) line.get("quantity");
        assertThat(((Number) quantity.get("amount")).doubleValue()).as("amount").isEqualTo(amount);
        assertThat(quantity.get("unit")).isEqualTo(unit);
    }

    private static String id(ResultActions created) throws Exception {
        return JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static int changes(MvcResult stream) throws Exception {
        return stream.getResponse().getContentAsString().split("event:shopping-list-changed", -1).length - 1;
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

    private String recipeId(String name) throws Exception {
        String list = mvc.perform(as(jorge, get("/api/v1/recipes").param("size", "100")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> ids = JsonPath.read(list, "$.items[?(@.name == '" + name + "')].id");
        assertThat(ids).as("recipe " + name).hasSize(1);
        return ids.get(0);
    }

    private String shoppingList() {
        return "/api/v1/households/" + householdId + "/shopping-list";
    }

    private String item(String itemId) {
        return shoppingList() + "/items/" + itemId;
    }
}
