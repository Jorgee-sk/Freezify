package com.freezify.scanning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.freezify.ai.AiService;
import com.freezify.food.Unit;
import com.freezify.testsupport.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

class ReceiptScanApiTests extends ApiTestSupport {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Madrid"));
    private static final String DAY = "%02d/%02d/%d".formatted(TODAY.getDayOfMonth(), TODAY.getMonthValue(), TODAY.getYear());

    private static final String RECEIPT = """
            SUPERMERCADO EJEMPLO S.L.
            %s 18:23
            1 LECHE ENTERA 1L                0,89
            1 PECH POLLO                     5,10
            1 TOMATE PERA
            0,856 kg   2,10 €/kg            1,80
            1 BOLSA PLASTICO                 0,15
            TOTAL                            7,94
            """.formatted(DAY);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** No model is configured in tests; each test says what the model answers, when it matters. */
    @MockitoBean
    private AiService ai;

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
    void aReceiptIsReadIntoADraftForReview() throws Exception {
        read(jorge, RECEIPT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readBy").value("RULES"))
                .andExpect(jsonPath("$.purchaseDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.purchaseDateFromReceipt").value(true))
                .andExpect(jsonPath("$.lines[*].text", contains("LECHE ENTERA", "PECH POLLO", "TOMATE PERA", "BOLSA PLASTICO")))
                .andExpect(jsonPath("$.lines[*].name", contains("Leche", "Pechuga de pollo", "Tomate", "Bolsa plastico")))
                .andExpect(jsonPath("$.lines[*].match", contains("NAME", "ALIAS", "NAME", "NONE")))
                .andExpect(jsonPath("$.lines[*].include", contains(true, true, true, false)))
                .andExpect(jsonPath("$.lines[0].foodId").value(foodId("Leche")))
                .andExpect(jsonPath("$.lines[0].category").value("DAIRY"))
                .andExpect(jsonPath("$.lines[0].storageLocation").value("REFRIGERATOR"))
                .andExpect(jsonPath("$.lines[0].quantity.amount").value(1))
                .andExpect(jsonPath("$.lines[0].quantity.unit").value("LITER"))
                .andExpect(jsonPath("$.lines[0].quantityFromReceipt").value(true))
                .andExpect(jsonPath("$.lines[0].price").value(0.89))
                .andExpect(jsonPath("$.lines[2].quantity.amount").value(0.856))
                .andExpect(jsonPath("$.lines[2].quantity.unit").value("KILOGRAM"))
                .andExpect(jsonPath("$.lines[3].foodId").isEmpty())
                .andExpect(jsonPath("$.lines[3].category").value("OTHER"))
                .andExpect(jsonPath("$.lines[3].candidates", hasSize(0)));

        // Reading stores nothing.
        inventory(jorge).andExpect(jsonPath("$.items", hasSize(0)));
        // Catalog foods are named in the language asked for.
        mvc.perform(as(jorge, json(post(scans() + "/receipt").param("lang", "en"), body(RECEIPT))))
                .andExpect(jsonPath("$.lines[0].name").value("Milk"));
    }

    @Test
    void anImplausibleDateOnTheReceiptIsNotTakenAsThePurchaseDate() throws Exception {
        read(jorge, "01/01/2001\n1 LECHE 0,89")
                .andExpect(jsonPath("$.purchaseDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.purchaseDateFromReceipt").value(false));
        // Without a count, size or weight on the receipt, one unit is a guess and is said to be one.
        read(jorge, "LECHE 0,89")
                .andExpect(jsonPath("$.lines[0].quantity.amount").value(1))
                .andExpect(jsonPath("$.lines[0].quantity.unit").value("UNIT"))
                .andExpect(jsonPath("$.lines[0].quantityFromReceipt").value(false));
    }

    @Test
    void whatTheModelReadsIsUsedWhenItAnswers() throws Exception {
        when(ai.readReceipt(anyString(), any())).thenReturn(Optional.of(List.of(
                new AiService.ReceiptLine("PCHG PLL", "Pechuga de pollo", new BigDecimal("0.5"), Unit.KILOGRAM,
                        new BigDecimal("4.20")),
                new AiService.ReceiptLine("DETERG ULTRA", "Detergente", null, null, new BigDecimal("3.10")))));

        read(jorge, "1 PCHG PLL 4,20\n1 DETERG ULTRA 3,10")
                .andExpect(jsonPath("$.readBy").value("AI"))
                .andExpect(jsonPath("$.lines[*].text", contains("PCHG PLL", "DETERG ULTRA")))
                .andExpect(jsonPath("$.lines[*].name", contains("Pechuga de pollo", "Detergente")))
                .andExpect(jsonPath("$.lines[0].foodId").value(foodId("Pechuga de pollo")))
                .andExpect(jsonPath("$.lines[0].quantity.amount").value(0.5))
                .andExpect(jsonPath("$.lines[0].quantityFromReceipt").value(true))
                .andExpect(jsonPath("$.lines[1].foodId").isEmpty())
                .andExpect(jsonPath("$.lines[1].include").value(false))
                .andExpect(jsonPath("$.lines[1].quantityFromReceipt").value(false));
    }

    @Test
    void confirmingPutsTheReviewedProductsInTheInventory() throws Exception {
        LocalDate bought = TODAY.minusDays(1);
        LocalDate printedDate = TODAY.plusDays(6);
        confirm(jorge, """
                {"purchaseDate": "%s", "lines": [
                  {"text": "LECHE ENTERA", "foodId": "%s", "name": "Leche", "quantity": {"amount": 2, "unit": "LITER"},
                   "price": 1.78, "expirationDate": "%s"},
                  {"text": "TOMATE PERA", "foodId": "%s", "name": "Tomate",
                   "quantity": {"amount": 0.856, "unit": "KILOGRAM"}, "price": 1.80},
                  {"text": "PAPEL COCINA", "name": "Papel de cocina", "quantity": {"amount": 1, "unit": "UNIT"}}
                ]}""".formatted(bought, foodId("Leche"), printedDate, foodId("Tomate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocked").value(3));

        String items = inventory(jorge).andReturn().getResponse().getContentAsString();
        Map<String, Object> milk = item(items, "Leche");
        assertThat(milk)
                .containsEntry("foodId", foodId("Leche"))
                .containsEntry("category", "DAIRY")
                .containsEntry("storageLocation", "REFRIGERATOR")
                .containsEntry("purchaseDate", bought.toString())
                // The date read on the package is the person's, not an estimate.
                .containsEntry("expirationDate", printedDate.toString())
                .containsEntry("expirationSource", "USER")
                .containsEntry("estimatedPrice", 1.78);
        Map<String, Object> tomato = item(items, "Tomate");
        assertThat(tomato).containsEntry("expirationSource", "ESTIMATED").containsEntry("estimatedPrice", 1.8);
        assertThat(item(items, "Papel de cocina"))
                .containsEntry("foodId", null)
                .containsEntry("category", "OTHER")
                .containsEntry("storageLocation", "OTHER");
        assertThat(productEvents("receipt_scanned")).isEqualTo(1);
        assertThat(productEvents("food_added")).isEqualTo(3);
    }

    @Test
    void theHouseholdsChoiceIsRememberedForTheNextReceipt() throws Exception {
        // "QUESO MOZZ" would be cheese by its first word; this household says it is mozzarella.
        read(jorge, "1 QUESO MOZZ RALLADO 1,95").andExpect(jsonPath("$.lines[0].foodId").value(foodId("Queso")));
        confirm(jorge, """
                {"lines": [{"text": "QUESO MOZZ RALLADO", "foodId": "%s", "name": "Mozzarella",
                            "quantity": {"amount": 1, "unit": "UNIT"}}]}""".formatted(foodId("Mozzarella")))
                .andExpect(status().isOk());

        read(jorge, "2 QUESO MOZZ RALLADO 3,90")
                .andExpect(jsonPath("$.lines[0].foodId").value(foodId("Mozzarella")))
                .andExpect(jsonPath("$.lines[0].match").value("LEARNED"));

        // Another household learns nothing from this one.
        TestUser lucia = register("Lucía");
        String otherHousehold = createHousehold(lucia, "Otra casa");
        mvc.perform(as(lucia, json(post("/api/v1/households/" + otherHousehold + "/scans/receipt"),
                        body("1 QUESO MOZZ RALLADO 1,95"))))
                .andExpect(jsonPath("$.lines[0].foodId").value(foodId("Queso")))
                .andExpect(jsonPath("$.lines[0].match").value("NAME"));

        // Saying it is not a catalog food forgets the choice.
        confirm(jorge, """
                {"lines": [{"text": "QUESO MOZZ RALLADO", "name": "Queso rallado",
                            "quantity": {"amount": 1, "unit": "UNIT"}}]}""")
                .andExpect(status().isOk());
        read(jorge, "1 QUESO MOZZ RALLADO 1,95").andExpect(jsonPath("$.lines[0].match").value("NAME"));
    }

    @Test
    void nothingIsStockedWhenALineIsWrong() throws Exception {
        confirm(jorge, """
                {"lines": [
                  {"foodId": "%s", "name": "Leche", "quantity": {"amount": 1, "unit": "LITER"}},
                  {"foodId": "00000000-0000-0000-0000-000000000000", "name": "?", "quantity": {"amount": 1, "unit": "UNIT"}}
                ]}""".formatted(foodId("Leche")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FOOD_NOT_FOUND"));
        inventory(jorge).andExpect(jsonPath("$.items", hasSize(0)));

        confirm(jorge, """
                {"purchaseDate": "%s", "lines": [{"name": "Leche", "quantity": {"amount": 1, "unit": "LITER"}}]}"""
                        .formatted(TODAY.plusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PURCHASE_DATE_IN_FUTURE"));
        confirm(jorge, "{\"lines\": []}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        confirm(jorge, """
                {"lines": [{"name": "Leche", "quantity": {"amount": 0, "unit": "LITER"}}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        read(jorge, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        read(jorge, "x".repeat(20_001)).andExpect(status().isBadRequest());
        inventory(jorge).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void onlyMembersCanScanForAHousehold() throws Exception {
        TestUser stranger = register("Extraño");

        read(stranger, RECEIPT).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
        confirm(stranger, """
                {"lines": [{"name": "Leche", "quantity": {"amount": 1, "unit": "LITER"}}]}""")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
        inventory(jorge).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(json(post(scans() + "/receipt"), body(RECEIPT))).andExpect(status().isUnauthorized());
    }

    private String scans() {
        return "/api/v1/households/" + householdId + "/scans";
    }

    private ResultActions read(TestUser user, String text) throws Exception {
        return mvc.perform(as(user, json(post(scans() + "/receipt"), body(text))));
    }

    private ResultActions confirm(TestUser user, String request) throws Exception {
        return mvc.perform(as(user, json(post(scans() + "/receipt/confirm"), request)));
    }

    private ResultActions inventory(TestUser user) throws Exception {
        return mvc.perform(as(user, get("/api/v1/households/" + householdId + "/inventory")));
    }

    private static String body(String text) {
        return JSON.writeValueAsString(Map.of("text", text));
    }

    private static Map<String, Object> item(String inventory, String name) {
        List<Map<String, Object>> items = JsonPath.read(inventory, "$.items[?(@.name == '" + name + "')]");
        assertThat(items).as(name).hasSize(1);
        return items.getFirst();
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
