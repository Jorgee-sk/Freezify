package com.freezify.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.freezify.testsupport.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class InventoryApiTests extends ApiTestSupport {

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
    void addsAFoodWithTheMinimumInformation() throws Exception {
        add(jorge, """
                {"name": "  Tomates  ", "quantity": {"amount": 6, "unit": "UNIT"}, "storageLocation": "REFRIGERATOR",
                 "expirationDate": "2026-10-05"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Tomates"))
                .andExpect(jsonPath("$.householdId").value(householdId))
                .andExpect(jsonPath("$.quantity.amount").value(6.0))
                .andExpect(jsonPath("$.quantity.unit").value("UNIT"))
                .andExpect(jsonPath("$.storageLocation").value("REFRIGERATOR"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.category").value("OTHER"))
                .andExpect(jsonPath("$.expirationDate").value("2026-10-05"))
                .andExpect(jsonPath("$.expirationSource").value("USER"))
                .andExpect(jsonPath("$.purchaseDate")
                        .value(LocalDate.now(ZoneId.of("Europe/Madrid")).toString()));
    }

    @Test
    void anItemWithoutADateHasNoExpirationSource() throws Exception {
        add(jorge, """
                {"name": "Arroz", "quantity": {"amount": 1, "unit": "KILOGRAM"}, "storageLocation": "PANTRY"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expirationDate", nullValue()))
                .andExpect(jsonPath("$.expirationSource", nullValue()));
    }

    @Test
    void aCatalogFoodBringsItsCategory() throws Exception {
        String milkId = catalogFoodId("leche");

        add(jorge, """
                {"foodId": "%s", "name": "Leche semidesnatada", "quantity": {"amount": 750, "unit": "MILLILITER"},
                 "storageLocation": "REFRIGERATOR", "brand": "Hacendado", "estimatedPrice": 0.95,
                 "barcode": "8480000123456", "notes": " ", "purchaseDate": "2026-09-28", "openedDate": "2026-09-30"}
                """.formatted(milkId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.foodId").value(milkId))
                .andExpect(jsonPath("$.category").value("DAIRY"))
                .andExpect(jsonPath("$.status").value("OPENED"))
                .andExpect(jsonPath("$.brand").value("Hacendado"))
                .andExpect(jsonPath("$.estimatedPrice").value(0.95))
                .andExpect(jsonPath("$.purchaseDate").value("2026-09-28"))
                .andExpect(jsonPath("$.notes", nullValue()));
    }

    @Test
    void rejectsInvalidItems() throws Exception {
        add(jorge, """
                {"name": " ", "quantity": {"amount": 0, "unit": "GRAM"}, "estimatedPrice": -1}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("quantity.amount")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("storageLocation")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("estimatedPrice")));

        add(jorge, """
                {"name": "Arroz", "quantity": {"amount": 1, "unit": "CUPS"}, "storageLocation": "PANTRY"}
                """)
                .andExpect(status().isBadRequest());

        add(jorge, """
                {"foodId": "%s", "name": "Arroz", "quantity": {"amount": 1, "unit": "KILOGRAM"},
                 "storageLocation": "PANTRY"}
                """.formatted(UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FOOD_NOT_FOUND"));
    }

    @Test
    void listsWhatExpiresFirstAndItemsWithoutDateLast() throws Exception {
        addItem(jorge, "Yogur", "4", "UNIT", "REFRIGERATOR", "2026-10-09");
        addItem(jorge, "Arroz", "1", "KILOGRAM", "PANTRY", null);
        addItem(jorge, "Pollo", "500", "GRAM", "REFRIGERATOR", "2026-10-03");
        addItem(jorge, "Brócoli", "1", "UNIT", "REFRIGERATOR", "2026-10-03");

        list(jorge, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", contains("Brócoli", "Pollo", "Yogur", "Arroz")))
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.page").value(0));

        list(jorge, "?sort=NAME").andExpect(jsonPath("$.items[*].name", contains("Arroz", "Brócoli", "Pollo", "Yogur")));
        list(jorge, "?sort=RECENT")
                .andExpect(jsonPath("$.items[*].name", contains("Brócoli", "Pollo", "Arroz", "Yogur")));
    }

    @Test
    void filtersByLocationCategoryAndText() throws Exception {
        addItem(jorge, "Brócoli", "1", "UNIT", "REFRIGERATOR", null);
        addItem(jorge, "Guisantes", "400", "GRAM", "FREEZER", null);
        add(jorge, """
                {"foodId": "%s", "name": "Leche", "quantity": {"amount": 1, "unit": "LITER"},
                 "storageLocation": "REFRIGERATOR"}
                """.formatted(catalogFoodId("leche")))
                .andExpect(status().isCreated());

        list(jorge, "?location=FREEZER").andExpect(jsonPath("$.items[*].name", contains("Guisantes")));
        list(jorge, "?category=DAIRY").andExpect(jsonPath("$.items[*].name", contains("Leche")));
        list(jorge, "?q=BROCO").andExpect(jsonPath("$.items[*].name", contains("Brócoli")));
        list(jorge, "?location=REFRIGERATOR&q=e").andExpect(jsonPath("$.items[*].name", contains("Leche")));
        // LIKE wildcards typed by the user are plain characters.
        list(jorge, "?q=%25").andExpect(jsonPath("$.items", hasSize(0)));
        list(jorge, "?location=GARAGE").andExpect(status().isBadRequest());
    }

    @Test
    void paginates() throws Exception {
        for (String name : List.of("A", "B", "C", "D", "E")) {
            addItem(jorge, name, "1", "UNIT", "PANTRY", null);
        }

        list(jorge, "?size=2&page=1")
                .andExpect(jsonPath("$.items[*].name", contains("C", "D")))
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.size").value(2));
        list(jorge, "?size=500").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void membersShareTheInventoryAndOutsidersCannotTouchIt() throws Exception {
        TestUser partner = register("Lucía");
        TestUser stranger = register("Stranger");
        join(partner, invite(jorge, householdId)).andExpect(status().isOk());
        String itemId = addItem(jorge, "Leche", "2", "UNIT", "REFRIGERATOR", "2026-10-04");
        String base = "/api/v1/households/" + householdId + "/inventory";
        String itemJson = """
                {"name": "Hacked", "quantity": {"amount": 1, "unit": "UNIT"}, "storageLocation": "PANTRY"}
                """;

        list(partner, "").andExpect(jsonPath("$.items[*].name", contains("Leche")));
        mvc.perform(as(partner, get(base + "/" + itemId))).andExpect(status().isOk());

        expectHouseholdNotFound(mvc.perform(as(stranger, get(base))));
        expectHouseholdNotFound(mvc.perform(as(stranger, get(base + "/recent"))));
        expectHouseholdNotFound(mvc.perform(as(stranger, json(post(base), itemJson))));
        expectHouseholdNotFound(mvc.perform(as(stranger, get(base + "/" + itemId))));
        expectHouseholdNotFound(mvc.perform(as(stranger, json(put(base + "/" + itemId), itemJson))));
        expectHouseholdNotFound(mvc.perform(as(stranger, delete(base + "/" + itemId))));
        expectHouseholdNotFound(mvc.perform(as(stranger, post(base + "/" + itemId + "/open"))));
        expectHouseholdNotFound(mvc.perform(as(stranger, post(base + "/" + itemId + "/consume"))));
        expectHouseholdNotFound(mvc.perform(as(stranger, post(base + "/" + itemId + "/discard"))));
        mvc.perform(get(base)).andExpect(status().isUnauthorized());

        // Reaching the item through a household the stranger does belong to must not work either.
        String otherBase = "/api/v1/households/" + createHousehold(stranger, "Otra casa") + "/inventory/" + itemId;
        expectItemNotFound(mvc.perform(as(stranger, get(otherBase))));
        expectItemNotFound(mvc.perform(as(stranger, json(put(otherBase), itemJson))));
        expectItemNotFound(mvc.perform(as(stranger, delete(otherBase))));
        expectItemNotFound(mvc.perform(as(stranger, post(otherBase + "/consume"))));
        expectItemNotFound(mvc.perform(as(stranger, post(otherBase + "/discard"))));
        expectItemNotFound(mvc.perform(as(stranger, post(otherBase + "/open"))));

        mvc.perform(as(jorge, get(base + "/" + itemId)))
                .andExpect(jsonPath("$.name").value("Leche"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void updatesAnItem() throws Exception {
        String itemId = addItem(jorge, "Tomates", "6", "UNIT", "REFRIGERATOR", "2026-10-05");

        mvc.perform(as(jorge, json(put(item(itemId)), """
                        {"name": "Tomates pera", "category": "VEGETABLES", "quantity": {"amount": 1.5, "unit": "KILOGRAM"},
                         "storageLocation": "PANTRY", "purchaseDate": "2026-09-30"}
                        """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tomates pera"))
                .andExpect(jsonPath("$.category").value("VEGETABLES"))
                .andExpect(jsonPath("$.quantity.amount").value(1.5))
                .andExpect(jsonPath("$.quantity.unit").value("KILOGRAM"))
                .andExpect(jsonPath("$.storageLocation").value("PANTRY"))
                // The user's date was removed, so the date is now an estimate: 5 days from the purchase.
                .andExpect(jsonPath("$.userExpirationDate", nullValue()))
                .andExpect(jsonPath("$.expirationDate").value("2026-10-05"))
                .andExpect(jsonPath("$.expirationSource").value("ESTIMATED"));

        list(jorge, "?q=pera").andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void deletesAnItemWithoutCountingItAsWaste() throws Exception {
        String itemId = addItem(jorge, "Tomates", "6", "UNIT", "REFRIGERATOR", null);

        mvc.perform(as(jorge, delete(item(itemId)))).andExpect(status().isNoContent());

        expectItemNotFound(mvc.perform(as(jorge, get(item(itemId)))));
        list(jorge, "?state=ALL").andExpect(jsonPath("$.items", hasSize(0)));
        assertThat(outcomes()).isEmpty();
    }

    @Test
    void openingAnItemRecordsTheDateOnce() throws Exception {
        String itemId = addItem(jorge, "Leche", "1", "LITER", "REFRIGERATOR", null);
        String today = LocalDate.now(ZoneId.of("Europe/Madrid")).toString();

        mvc.perform(as(jorge, post(item(itemId) + "/open")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPENED"))
                .andExpect(jsonPath("$.openedDate").value(today));

        clock.advance(java.time.Duration.ofDays(3));
        mvc.perform(as(jorge, post(item(itemId) + "/open"))).andExpect(jsonPath("$.openedDate").value(today));
    }

    @Test
    void consumingPartOfAnItemReducesItAndRecordsTheConsumption() throws Exception {
        String itemId = addPriced("Pollo", "1", "KILOGRAM", "8.00");

        mvc.perform(as(jorge, json(post(item(itemId) + "/consume"), """
                        {"quantity": {"amount": 250, "unit": "GRAM"}}
                        """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity.amount").value(0.75))
                .andExpect(jsonPath("$.quantity.unit").value("KILOGRAM"))
                .andExpect(jsonPath("$.estimatedPrice").value(6.0))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        assertThat(outcomes()).singleElement().satisfies(outcome -> {
            assertThat(outcome).containsEntry("type", "CONSUMED").containsEntry("name", "Pollo");
            assertThat(outcome.get("reason")).isNull();
            assertThat(outcome.get("unit")).isEqualTo("KILOGRAM");
            assertThat(outcome.get("quantity").toString()).isEqualTo("0.250");
            assertThat(outcome.get("estimated_value").toString()).isEqualTo("2.00");
            assertThat(outcome.get("recorded_by").toString()).isEqualTo(jorge.id());
        });
    }

    @Test
    void consumingEverythingFinishesTheItem() throws Exception {
        String itemId = addPriced("Yogur", "4", "UNIT", "2.00");
        mvc.perform(as(jorge, json(post(item(itemId) + "/consume"), """
                {"quantity": {"amount": 3, "unit": "UNIT"}}
                """)));

        // No body: whatever is left.
        mvc.perform(as(jorge, post(item(itemId) + "/consume")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONSUMED"))
                .andExpect(jsonPath("$.quantity.amount").value(0.0));

        // The value of the parts adds up to the price of the whole.
        assertThat(outcomes())
                .extracting(outcome -> outcome.get("estimated_value").toString())
                .containsExactly("1.50", "0.50");
        list(jorge, "").andExpect(jsonPath("$.items", hasSize(0)));
        list(jorge, "?state=FINISHED").andExpect(jsonPath("$.items[*].name", contains("Yogur")));
        list(jorge, "?state=ALL").andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void discardingRecordsWasteWithItsReason() throws Exception {
        String itemId = addPriced("Lechuga", "2", "UNIT", "1.80");

        mvc.perform(as(jorge, json(post(item(itemId) + "/discard"), """
                        {"quantity": {"amount": 1, "unit": "UNIT"}, "reason": "SPOILED"}
                        """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.quantity.amount").value(1.0));
        mvc.perform(as(jorge, post(item(itemId) + "/discard")))
                .andExpect(jsonPath("$.status").value("DISCARDED"));

        assertThat(outcomes())
                .extracting(outcome -> outcome.get("type") + "/" + outcome.get("reason") + "/"
                        + outcome.get("estimated_value"))
                .containsExactly("DISCARDED/SPOILED/0.90", "DISCARDED/OTHER/0.90");
    }

    @Test
    void cannotTakeOutMoreThanThereIsOrInAnotherDimension() throws Exception {
        String itemId = addItem(jorge, "Pollo", "500", "GRAM", "REFRIGERATOR", null);

        mvc.perform(as(jorge, json(post(item(itemId) + "/consume"), """
                        {"quantity": {"amount": 1, "unit": "KILOGRAM"}}
                        """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUANTITY_EXCEEDS_AVAILABLE"));
        mvc.perform(as(jorge, json(post(item(itemId) + "/discard"), """
                        {"quantity": {"amount": 1, "unit": "LITER"}}
                        """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INCOMPATIBLE_UNIT"));
        mvc.perform(as(jorge, json(post(item(itemId) + "/consume"), """
                        {"quantity": {"amount": 0, "unit": "GRAM"}}
                        """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(as(jorge, get(item(itemId)))).andExpect(jsonPath("$.quantity.amount").value(500.0));
        assertThat(outcomes()).isEmpty();
    }

    @Test
    void aFinishedItemCannotBeChanged() throws Exception {
        String itemId = addItem(jorge, "Yogur", "1", "UNIT", "REFRIGERATOR", null);
        mvc.perform(as(jorge, post(item(itemId) + "/consume"))).andExpect(status().isOk());

        for (String action : List.of("/consume", "/discard", "/open")) {
            mvc.perform(as(jorge, post(item(itemId) + action)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ITEM_NOT_ACTIVE"));
        }
        mvc.perform(as(jorge, json(put(item(itemId)), """
                        {"name": "Yogur", "quantity": {"amount": 4, "unit": "UNIT"}, "storageLocation": "REFRIGERATOR"}
                        """)))
                .andExpect(status().isConflict());
    }

    @Test
    void offersRecentlyAddedFoodsWithoutRepeatingThem() throws Exception {
        addItem(jorge, "Leche", "1", "LITER", "REFRIGERATOR", null);
        addItem(jorge, "Pan", "1", "UNIT", "PANTRY", null);
        addItem(jorge, "leche", "2", "LITER", "REFRIGERATOR", null);

        mvc.perform(as(jorge, get("/api/v1/households/" + householdId + "/inventory/recent")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("leche", "Pan")))
                .andExpect(jsonPath("$[0].quantity.amount").value(2.0))
                .andExpect(jsonPath("$[0].storageLocation").value("REFRIGERATOR"));
    }

    @Test
    void deletingTheHouseholdDeletesItsInventory() throws Exception {
        String itemId = addItem(jorge, "Leche", "1", "LITER", "REFRIGERATOR", null);
        mvc.perform(as(jorge, post(item(itemId) + "/consume"))).andExpect(status().isOk());

        mvc.perform(as(jorge, delete("/api/v1/households/" + householdId))).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                        "select count(*) from food_items where household_id = ?::uuid", Integer.class, householdId))
                .isZero();
        assertThat(outcomes()).isEmpty();
    }

    @Test
    void recordsProductEvents() throws Exception {
        String itemId = addItem(jorge, "Leche", "2", "LITER", "REFRIGERATOR", null);
        mvc.perform(as(jorge, json(post(item(itemId) + "/consume"), """
                {"quantity": {"amount": 1, "unit": "LITER"}}
                """)));
        mvc.perform(as(jorge, post(item(itemId) + "/discard")));
        // A rejected request must not leave an event behind.
        mvc.perform(as(jorge, post(item(itemId) + "/consume"))).andExpect(status().isConflict());

        assertThat(jdbc.queryForList(
                        "select name from product_events where household_id = ?::uuid order by id",
                        String.class,
                        householdId))
                .containsExactly("food_added", "food_consumed", "food_discarded");
        assertThat(jdbc.queryForList(
                        "select name from product_events where user_id = ?::uuid and household_id is null",
                        String.class,
                        jorge.id()))
                .containsExactly("user_registered");
    }

    @Test
    void itemsSayHowSoonTheyExpire() throws Exception {
        addItem(jorge, "Caducado", "1", "UNIT", "REFRIGERATOR", inDays(-3));
        addItem(jorge, "Hoy", "1", "UNIT", "REFRIGERATOR", inDays(0));
        addItem(jorge, "Urgente", "1", "UNIT", "REFRIGERATOR", inDays(2));
        addItem(jorge, "Pronto", "1", "UNIT", "REFRIGERATOR", inDays(5));
        addItem(jorge, "Proximo", "1", "UNIT", "REFRIGERATOR", inDays(10));
        addItem(jorge, "Lejano", "1", "UNIT", "REFRIGERATOR", inDays(11));
        addItem(jorge, "Sin fecha", "1", "UNIT", "PANTRY", null);

        list(jorge, "")
                .andExpect(jsonPath("$.items[*].name")
                        .value(contains("Caducado", "Hoy", "Urgente", "Pronto", "Proximo", "Lejano", "Sin fecha")))
                .andExpect(jsonPath("$.items[*].priority")
                        .value(contains("EXPIRED", "TODAY", "URGENT", "SOON", "UPCOMING", "OK", null)))
                .andExpect(jsonPath("$.items[*].daysUntilExpiration").value(contains(-3, 0, 2, 5, 10, 11, null)));
    }

    @Test
    void priorityFollowsTheCalendarAsDaysPass() throws Exception {
        String itemId = addItem(jorge, "Yogur", "1", "UNIT", "REFRIGERATOR", inDays(4));
        mvc.perform(as(jorge, get(item(itemId))))
                .andExpect(jsonPath("$.priority").value("SOON"))
                .andExpect(jsonPath("$.daysUntilExpiration").value(4));

        clock.advance(java.time.Duration.ofDays(3));
        mvc.perform(as(jorge, get(item(itemId))))
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.daysUntilExpiration").value(1));

        clock.advance(java.time.Duration.ofDays(2));
        mvc.perform(as(jorge, get(item(itemId))))
                .andExpect(jsonPath("$.priority").value("EXPIRED"))
                .andExpect(jsonPath("$.daysUntilExpiration").value(-1));
    }

    @Test
    void consumeFirstListsWhatNeedsAttentionMostPressingFirst() throws Exception {
        addItem(jorge, "Lechuga", "1", "UNIT", "REFRIGERATOR", inDays(5));
        addItem(jorge, "Pollo", "500", "GRAM", "REFRIGERATOR", inDays(1));
        addItem(jorge, "Brócoli", "1", "UNIT", "REFRIGERATOR", inDays(1));
        addItem(jorge, "Leche", "1", "LITER", "REFRIGERATOR", inDays(-2));
        addItem(jorge, "Queso", "200", "GRAM", "REFRIGERATOR", inDays(6));
        addItem(jorge, "Arroz", "1", "KILOGRAM", "PANTRY", null);
        // Already eaten: no longer anyone's problem.
        String eaten = addItem(jorge, "Yogur", "1", "UNIT", "REFRIGERATOR", inDays(0));
        mvc.perform(as(jorge, post(item(eaten) + "/consume"))).andExpect(status().isOk());

        mvc.perform(as(jorge, get(consumeFirst())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name").value(contains("Leche", "Brócoli", "Pollo", "Lechuga")))
                .andExpect(jsonPath("$.items[*].priority").value(contains("EXPIRED", "URGENT", "URGENT", "SOON")))
                .andExpect(jsonPath("$.counts.EXPIRED").value(1))
                .andExpect(jsonPath("$.counts.TODAY").value(0))
                .andExpect(jsonPath("$.counts.URGENT").value(2))
                .andExpect(jsonPath("$.counts.SOON").value(1))
                .andExpect(jsonPath("$.counts.UPCOMING").value(1))
                .andExpect(jsonPath("$.counts.OK").value(0))
                .andExpect(jsonPath("$.counts.NO_DATE").value(1));
    }

    @Test
    void consumeFirstOfAnEmptyInventoryReportsEveryLevelAsZero() throws Exception {
        mvc.perform(as(jorge, get(consumeFirst())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.counts.length()").value(7))
                .andExpect(jsonPath("$.counts.URGENT").value(0));
    }

    @Test
    void consumeFirstIsOnlyForMembers() throws Exception {
        TestUser stranger = register("Stranger");
        addItem(jorge, "Pollo", "500", "GRAM", "REFRIGERATOR", inDays(1));

        expectHouseholdNotFound(mvc.perform(as(stranger, get(consumeFirst()))));
        mvc.perform(get(consumeFirst())).andExpect(status().isUnauthorized());
    }

    @Test
    void freshFoodWithoutADateGetsAnEstimateLabelledAsSuch() throws Exception {
        add(jorge, """
                {"foodId": "%s", "name": "Pechuga de pollo", "quantity": {"amount": 500, "unit": "GRAM"},
                 "storageLocation": "REFRIGERATOR", "purchaseDate": "2026-10-01"}
                """.formatted(catalogFoodId("pechuga")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expirationDate").value("2026-10-03"))
                .andExpect(jsonPath("$.expirationSource").value("ESTIMATED"))
                .andExpect(jsonPath("$.userExpirationDate", nullValue()));
    }

    @Test
    void theEstimateDependsOnWhereTheFoodIsKept() throws Exception {
        String chicken = catalogFoodId("pechuga");
        String body = """
                {"foodId": "%s", "name": "Pechuga de pollo", "quantity": {"amount": 500, "unit": "GRAM"},
                 "storageLocation": "%s", "purchaseDate": "2026-10-01"}
                """;
        String itemId = JsonPath.read(
                add(jorge, body.formatted(chicken, "REFRIGERATOR"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");

        // Moved to the freezer: three months instead of two days.
        mvc.perform(as(jorge, json(put(item(itemId)), body.formatted(chicken, "FREEZER"))))
                .andExpect(jsonPath("$.expirationDate").value("2026-12-30"))
                .andExpect(jsonPath("$.expirationSource").value("ESTIMATED"));

        // Nobody knows how long chicken keeps in a pantry: no rule, so no date rather than an invented one.
        mvc.perform(as(jorge, json(put(item(itemId)), body.formatted(chicken, "PANTRY"))))
                .andExpect(jsonPath("$.expirationDate", nullValue()))
                .andExpect(jsonPath("$.expirationSource", nullValue()));
    }

    @Test
    void aFoodsOwnRuleWinsOverTheRuleOfItsCategory() throws Exception {
        // Vegetables keep 7 days in the fridge, but carrots keep 21.
        add(jorge, """
                {"foodId": "%s", "name": "Zanahoria", "quantity": {"amount": 6, "unit": "UNIT"},
                 "storageLocation": "REFRIGERATOR", "purchaseDate": "2026-10-01"}
                """.formatted(catalogFoodId("zanahoria")))
                .andExpect(jsonPath("$.expirationDate").value("2026-10-22"));

        // Not from the catalog, but filed as a vegetable by the user: the category rule applies.
        add(jorge, """
                {"name": "Pak choi", "category": "VEGETABLES", "quantity": {"amount": 1, "unit": "UNIT"},
                 "storageLocation": "REFRIGERATOR", "purchaseDate": "2026-10-01"}
                """)
                .andExpect(jsonPath("$.expirationDate").value("2026-10-08"))
                .andExpect(jsonPath("$.expirationSource").value("ESTIMATED"));
    }

    @Test
    void theDateTheUserGivesIsNeverReplacedByAnEstimate() throws Exception {
        add(jorge, """
                {"foodId": "%s", "name": "Pechuga de pollo", "quantity": {"amount": 500, "unit": "GRAM"},
                 "storageLocation": "REFRIGERATOR", "purchaseDate": "2026-10-01", "expirationDate": "2026-10-09"}
                """.formatted(catalogFoodId("pechuga")))
                .andExpect(jsonPath("$.expirationDate").value("2026-10-09"))
                .andExpect(jsonPath("$.expirationSource").value("USER"))
                .andExpect(jsonPath("$.userExpirationDate").value("2026-10-09"));
    }

    @Test
    void packagedFoodHasNoDateUntilTheUserGivesOneOrOpensIt() throws Exception {
        String body = add(jorge, """
                        {"foodId": "%s", "name": "Leche", "quantity": {"amount": 1, "unit": "LITER"},
                         "storageLocation": "REFRIGERATOR"}
                        """.formatted(catalogFoodId("leche")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expirationDate", nullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String itemId = JsonPath.read(body, "$.id");

        mvc.perform(as(jorge, post(item(itemId) + "/open")))
                .andExpect(jsonPath("$.expirationDate").value(inDays(3)))
                .andExpect(jsonPath("$.expirationSource").value("ESTIMATED"))
                .andExpect(jsonPath("$.priority").value("SOON"));
    }

    @Test
    void openingShortensTheDateOnThePackageWithoutForgettingIt() throws Exception {
        String farAway = inDays(60);
        String body = add(jorge, """
                        {"foodId": "%s", "name": "Leche", "quantity": {"amount": 1, "unit": "LITER"},
                         "storageLocation": "REFRIGERATOR", "expirationDate": "%s"}
                        """.formatted(catalogFoodId("leche"), farAway))
                .andExpect(jsonPath("$.expirationSource").value("USER"))
                .andExpect(jsonPath("$.priority").value("OK"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String itemId = JsonPath.read(body, "$.id");

        mvc.perform(as(jorge, post(item(itemId) + "/open")))
                .andExpect(jsonPath("$.expirationDate").value(inDays(3)))
                .andExpect(jsonPath("$.expirationSource").value("ESTIMATED"))
                .andExpect(jsonPath("$.userExpirationDate").value(farAway))
                .andExpect(jsonPath("$.priority").value("SOON"));

        // It now belongs in "consume first", which is the whole point.
        mvc.perform(as(jorge, get(consumeFirst())))
                .andExpect(jsonPath("$.items[*].name").value(contains("Leche")))
                .andExpect(jsonPath("$.counts.SOON").value(1));
    }

    @Test
    void openingDoesNotExtendADateThatIsCloser() throws Exception {
        String tomorrow = inDays(1);
        String body = add(jorge, """
                        {"foodId": "%s", "name": "Leche", "quantity": {"amount": 1, "unit": "LITER"},
                         "storageLocation": "REFRIGERATOR", "expirationDate": "%s"}
                        """.formatted(catalogFoodId("leche"), tomorrow))
                .andReturn()
                .getResponse()
                .getContentAsString();

        mvc.perform(as(jorge, post(item(JsonPath.read(body, "$.id")) + "/open")))
                .andExpect(jsonPath("$.expirationDate").value(tomorrow))
                .andExpect(jsonPath("$.expirationSource").value("USER"));
    }

    @Test
    void everyShelfLifeRuleOfTheMigrationWasLoaded() {
        // The food rules are inserted by joining on the catalog slug; a typo there would silently drop a rule.
        assertThat(jdbc.queryForObject("select count(*) from shelf_life_rules where food_id is not null", Integer.class))
                .isEqualTo(37);
        assertThat(jdbc.queryForObject("select count(*) from shelf_life_rules where category is not null", Integer.class))
                .isEqualTo(20);
    }

    private String consumeFirst() {
        return "/api/v1/households/" + householdId + "/inventory/consume-first";
    }

    /** A date relative to today as the application sees it. */
    private static String inDays(int days) {
        return LocalDate.now(ZoneId.of("Europe/Madrid")).plusDays(days).toString();
    }

    private ResultActions add(TestUser user, String body) throws Exception {
        return mvc.perform(as(user, json(post("/api/v1/households/" + householdId + "/inventory"), body)));
    }

    private String addItem(TestUser user, String name, String amount, String unit, String location, String expiration)
            throws Exception {
        String body = add(user, """
                        {"name": "%s", "quantity": {"amount": %s, "unit": "%s"}, "storageLocation": "%s",
                         "expirationDate": %s}
                        """.formatted(name, amount, unit, location, expiration == null ? "null" : "\"" + expiration + "\""))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String addPriced(String name, String amount, String unit, String price) throws Exception {
        String body = add(jorge, """
                        {"name": "%s", "quantity": {"amount": %s, "unit": "%s"}, "storageLocation": "REFRIGERATOR",
                         "estimatedPrice": %s}
                        """.formatted(name, amount, unit, price))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions list(TestUser user, String query) throws Exception {
        return mvc.perform(as(user, get("/api/v1/households/" + householdId + "/inventory" + query)));
    }

    private String item(String itemId) {
        return "/api/v1/households/" + householdId + "/inventory/" + itemId;
    }

    private String catalogFoodId(String text) throws Exception {
        String body = mvc.perform(as(jorge, get("/api/v1/foods").param("q", text)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$[0].id");
    }

    private List<Map<String, Object>> outcomes() {
        return jdbc.queryForList(
                "select * from food_outcomes where household_id = ?::uuid order by id", householdId);
    }

    private static void expectHouseholdNotFound(ResultActions result) throws Exception {
        result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
    }

    private static void expectItemNotFound(ResultActions result) throws Exception {
        result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));
    }
}
