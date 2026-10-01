package com.freezify.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.freezify.testsupport.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class RealtimeApiTests extends ApiTestSupport {

    private static final String ITEM = """
            {"name": "Leche", "quantity": {"amount": 2, "unit": "LITER"}, "storageLocation": "REFRIGERATOR"}
            """;

    private TestUser jorge;
    private TestUser partner;
    private String householdId;

    @BeforeEach
    void householdWithTwoMembers() throws Exception {
        jorge = register("Jorge");
        partner = register("Lucía");
        householdId = createHousehold(jorge, "Casa");
        join(partner, invite(jorge, householdId)).andExpect(status().isOk());
    }

    @Test
    void opensAnEventStreamForMembers() throws Exception {
        MvcResult stream = mvc.perform(as(jorge, get(events(householdId))))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/event-stream")))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andReturn();

        assertThat(received(stream)).contains("event:connected");
        assertThat(count(stream, "inventory-changed")).isZero();
    }

    @Test
    void everyChangeToTheInventoryReachesTheOtherMembers() throws Exception {
        MvcResult stream = open(partner, householdId);
        String inventory = "/api/v1/households/" + householdId + "/inventory";

        String itemId = JsonPath.read(
                mvc.perform(as(jorge, json(post(inventory), ITEM)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
        assertThat(count(stream, "inventory-changed")).isEqualTo(1);

        mvc.perform(as(jorge, json(put(inventory + "/" + itemId), ITEM))).andExpect(status().isOk());
        assertThat(count(stream, "inventory-changed")).isEqualTo(2);

        mvc.perform(as(jorge, post(inventory + "/" + itemId + "/open"))).andExpect(status().isOk());
        assertThat(count(stream, "inventory-changed")).isEqualTo(3);

        mvc.perform(as(jorge, json(post(inventory + "/" + itemId + "/consume"), """
                        {"quantity": {"amount": 1, "unit": "LITER"}}
                        """)))
                .andExpect(status().isOk());
        assertThat(count(stream, "inventory-changed")).isEqualTo(4);

        mvc.perform(as(jorge, post(inventory + "/" + itemId + "/discard"))).andExpect(status().isOk());
        assertThat(count(stream, "inventory-changed")).isEqualTo(5);

        mvc.perform(as(jorge, delete(inventory + "/" + itemId))).andExpect(status().isNoContent());
        assertThat(count(stream, "inventory-changed")).isEqualTo(6);

        // Events only say that something changed; the data is fetched through the authorized API.
        assertThat(received(stream)).doesNotContain("Leche").doesNotContain(itemId);
    }

    @Test
    void aRejectedChangeSendsNothing() throws Exception {
        MvcResult stream = open(partner, householdId);

        mvc.perform(as(jorge, json(post("/api/v1/households/" + householdId + "/inventory"), "{}")))
                .andExpect(status().isBadRequest());

        assertThat(count(stream, "inventory-changed")).isZero();
    }

    @Test
    void theOneWhoMadeTheChangeIsNotifiedTooForTheirOtherDevices() throws Exception {
        MvcResult phone = open(jorge, householdId);
        MvcResult laptop = open(jorge, householdId);

        addItem(jorge, householdId);

        assertThat(count(phone, "inventory-changed")).isEqualTo(1);
        assertThat(count(laptop, "inventory-changed")).isEqualTo(1);
    }

    @Test
    void eventsStayInsideTheirHousehold() throws Exception {
        String otherHousehold = createHousehold(jorge, "Piso playa");
        MvcResult stream = open(partner, householdId);

        addItem(jorge, otherHousehold);

        assertThat(count(stream, "inventory-changed")).isZero();
    }

    @Test
    void outsidersCannotListen() throws Exception {
        TestUser stranger = register("Stranger");

        mvc.perform(as(stranger, get(events(householdId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
        mvc.perform(as(stranger, get(events(UUID.randomUUID().toString())))).andExpect(status().isNotFound());
        mvc.perform(get(events(householdId))).andExpect(status().isUnauthorized());
    }

    @Test
    void aRemovedMemberStopsReceivingEvents() throws Exception {
        MvcResult stream = open(partner, householdId);
        addItem(jorge, householdId);
        assertThat(count(stream, "inventory-changed")).isEqualTo(1);

        mvc.perform(as(jorge, delete("/api/v1/households/" + householdId + "/members/" + partner.id())))
                .andExpect(status().isNoContent());
        addItem(jorge, householdId);

        assertThat(count(stream, "inventory-changed")).isEqualTo(1);
    }

    @Test
    void aMemberWhoLeavesStopsReceivingEventsButTheOthersDoNot() throws Exception {
        MvcResult leaving = open(partner, householdId);
        MvcResult staying = open(jorge, householdId);

        mvc.perform(as(partner, delete("/api/v1/households/" + householdId + "/members/" + partner.id())))
                .andExpect(status().isNoContent());
        addItem(jorge, householdId);

        assertThat(count(leaving, "inventory-changed")).isZero();
        assertThat(count(staying, "inventory-changed")).isEqualTo(1);
    }

    @Test
    void onlyAFewStreamsPerMemberAreKeptOpen() throws Exception {
        MvcResult first = open(jorge, householdId);
        MvcResult[] others = new MvcResult[5];
        for (int i = 0; i < others.length; i++) {
            others[i] = open(jorge, householdId);
        }

        addItem(partner, householdId);

        // The oldest connection was dropped to make room; the five newest are served.
        assertThat(count(first, "inventory-changed")).isZero();
        for (MvcResult stream : others) {
            assertThat(count(stream, "inventory-changed")).isEqualTo(1);
        }
    }

    private MvcResult open(TestUser user, String household) throws Exception {
        return mvc.perform(as(user, get(events(household))))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private void addItem(TestUser user, String household) throws Exception {
        mvc.perform(as(user, json(post("/api/v1/households/" + household + "/inventory"), ITEM)))
                .andExpect(status().isCreated());
    }

    private static String events(String household) {
        return "/api/v1/households/" + household + "/events";
    }

    private static String received(MvcResult stream) throws Exception {
        return stream.getResponse().getContentAsString();
    }

    private static int count(MvcResult stream, String eventName) throws Exception {
        return received(stream).split("event:" + eventName, -1).length - 1;
    }
}
