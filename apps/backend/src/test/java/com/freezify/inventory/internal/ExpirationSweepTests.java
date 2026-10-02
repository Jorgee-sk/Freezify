package com.freezify.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.freezify.testsupport.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

class ExpirationSweepTests extends ApiTestSupport {

    @Autowired
    private ExpirationSweep sweep;

    private TestUser jorge;
    private String householdId;

    @BeforeEach
    void householdWithOwner() throws Exception {
        jorge = register("Jorge");
        householdId = createHousehold(jorge, "Casa");
    }

    @Test
    void marksAsExpiredWhatPassesItsDateWhenTheDayChanges() throws Exception {
        String milk = addItem("Leche", inDays(1), null);
        String openedHam = addItem("Jamón", inDays(1), inDays(0));
        String cheese = addItem("Queso", inDays(5), null);
        String rice = addItem("Arroz", null, null);
        String eaten = addItem("Yogur", inDays(1), null);
        mvc.perform(as(jorge, post(item(eaten) + "/consume"))).andExpect(status().isOk());

        // Still good on the day of its date.
        clock.advance(Duration.ofDays(1));
        sweep.run();
        assertThat(statusOf(milk)).isEqualTo("AVAILABLE");

        clock.advance(Duration.ofDays(1));
        sweep.run();

        assertThat(statusOf(milk)).isEqualTo("EXPIRED");
        assertThat(statusOf(openedHam)).isEqualTo("EXPIRED");
        assertThat(statusOf(cheese)).isEqualTo("AVAILABLE");
        assertThat(statusOf(rice)).isEqualTo("AVAILABLE");
        // What already left the house keeps its own outcome.
        assertThat(statusOf(eaten)).isEqualTo("CONSUMED");
    }

    @Test
    void expiredFoodIsStillInTheHouseUntilSomeoneDealsWithIt() throws Exception {
        String milk = addItem("Leche", inDays(1), null);
        clock.advance(Duration.ofDays(2));
        sweep.run();

        mvc.perform(as(jorge, get(inventory())))
                .andExpect(jsonPath("$.items[0].name").value("Leche"))
                .andExpect(jsonPath("$.items[0].status").value("EXPIRED"))
                .andExpect(jsonPath("$.items[0].priority").value("EXPIRED"));
        mvc.perform(as(jorge, get(inventory() + "/consume-first")))
                .andExpect(jsonPath("$.counts.EXPIRED").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Leche"));

        mvc.perform(as(jorge, json(post(item(milk) + "/discard"), """
                        {"reason": "EXPIRED"}
                        """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCARDED"));
    }

    @Test
    void runningItAgainChangesNothing() throws Exception {
        addItem("Leche", inDays(1), null);
        clock.advance(Duration.ofDays(2));
        MvcResult stream = mvc.perform(as(jorge, get("/api/v1/households/" + householdId + "/events")))
                .andExpect(request().asyncStarted())
                .andReturn();

        sweep.run();
        sweep.run();

        // Members with the inventory open are told once, by the run that changed something.
        assertThat(stream.getResponse().getContentAsString().split("event:inventory-changed", -1).length - 1)
                .isEqualTo(1);
    }

    @Test
    void anItemAddedAlreadyPastItsDateIsExpiredFromTheStart() throws Exception {
        String old = addItem("Yogur", inDays(-2), null);

        assertThat(statusOf(old)).isEqualTo("EXPIRED");
    }

    @Test
    void correctingTheDateBringsAnExpiredItemBack() throws Exception {
        String milk = addItem("Leche", inDays(-1), null);
        String openedHam = addItem("Jamón", inDays(-1), inDays(-3));
        assertThat(statusOf(milk)).isEqualTo("EXPIRED");
        assertThat(statusOf(openedHam)).isEqualTo("EXPIRED");

        // The date had been typed wrong.
        mvc.perform(as(jorge, json(put(item(milk)), body("Leche", inDays(4), null))))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        mvc.perform(as(jorge, json(put(item(openedHam)), body("Jamón", inDays(4), inDays(-3)))))
                .andExpect(jsonPath("$.status").value("OPENED"));
    }

    private String addItem(String name, String expirationDate, String openedDate) throws Exception {
        String response = mvc.perform(as(jorge, json(post(inventory()), body(name, expirationDate, openedDate))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private static String body(String name, String expirationDate, String openedDate) {
        return """
                {"name": "%s", "quantity": {"amount": 1, "unit": "UNIT"}, "storageLocation": "REFRIGERATOR",
                 "expirationDate": %s, "openedDate": %s}
                """.formatted(name, quoted(expirationDate), quoted(openedDate));
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private String statusOf(String itemId) throws Exception {
        return JsonPath.read(
                mvc.perform(as(jorge, get(item(itemId)))).andReturn().getResponse().getContentAsString(), "$.status");
    }

    private String inventory() {
        return "/api/v1/households/" + householdId + "/inventory";
    }

    private String item(String itemId) {
        return inventory() + "/" + itemId;
    }

    /** A date relative to today as the application sees it when the test starts. */
    private static String inDays(int days) {
        return LocalDate.now(ZoneId.of("Europe/Madrid")).plusDays(days).toString();
    }
}
