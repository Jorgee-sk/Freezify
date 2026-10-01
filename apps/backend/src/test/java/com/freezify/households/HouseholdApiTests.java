package com.freezify.households;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.freezify.testsupport.ApiTestSupport;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class HouseholdApiTests extends ApiTestSupport {

    @Test
    void creatorBecomesOwner() throws Exception {
        TestUser jorge = register("Jorge");

        mvc.perform(as(jorge, json(post("/api/v1/households"), """
                        {"name": "  Casa  "}
                        """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Casa"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.memberCount").value(1));
    }

    @Test
    void householdNameIsValidated() throws Exception {
        TestUser jorge = register("Jorge");

        mvc.perform(as(jorge, json(post("/api/v1/households"), """
                        {"name": "   "}
                        """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void userOnlySeesOwnHouseholds() throws Exception {
        TestUser jorge = register("Jorge");
        TestUser stranger = register("Stranger");
        createHousehold(jorge, "Casa");
        createHousehold(jorge, "Piso playa");
        createHousehold(stranger, "Otra casa");

        mvc.perform(as(jorge, get("/api/v1/households")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Casa"))
                .andExpect(jsonPath("$[1].name").value("Piso playa"));
    }

    @Test
    void nonMemberCannotReachAnotherHouseholdByGuessingItsId() throws Exception {
        TestUser jorge = register("Jorge");
        TestUser stranger = register("Stranger");
        String householdId = createHousehold(jorge, "Casa");
        String base = "/api/v1/households/" + householdId;

        expectHouseholdNotFound(mvc.perform(as(stranger, get(base))));
        expectHouseholdNotFound(mvc.perform(as(stranger, json(patch(base), """
                {"name": "Hacked"}
                """))));
        expectHouseholdNotFound(mvc.perform(as(stranger, delete(base))));
        expectHouseholdNotFound(mvc.perform(as(stranger, get(base + "/members"))));
        expectHouseholdNotFound(mvc.perform(as(stranger, post(base + "/invitations"))));
        expectHouseholdNotFound(mvc.perform(as(stranger, delete(base + "/members/" + jorge.id()))));

        // Same answer as for an id that does not exist at all.
        expectHouseholdNotFound(mvc.perform(as(stranger, get("/api/v1/households/" + UUID.randomUUID()))));

        mvc.perform(as(jorge, get(base)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Casa"))
                .andExpect(jsonPath("$.memberCount").value(1));
    }

    @Test
    void householdEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/households")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/households/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidHouseholdIdIsABadRequest() throws Exception {
        TestUser jorge = register("Jorge");

        mvc.perform(as(jorge, get("/api/v1/households/not-a-uuid"))).andExpect(status().isBadRequest());
    }

    @Test
    void invitedUserJoinsAndSharesTheHousehold() throws Exception {
        TestUser jorge = register("Jorge");
        TestUser partner = register("Lucía");
        String householdId = createHousehold(jorge, "Casa");

        String code = invite(jorge, householdId);

        // Typed by hand: lower case with a dash in the middle.
        String typed = code.substring(0, 4).toLowerCase() + "-" + code.substring(4).toLowerCase();
        mvc.perform(as(partner, json(post("/api/v1/households/join"), """
                        {"code": "%s"}
                        """.formatted(typed))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(householdId))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.memberCount").value(2));

        mvc.perform(as(partner, get("/api/v1/households")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(householdId));

        mvc.perform(as(partner, get("/api/v1/households/" + householdId + "/members")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].displayName").value("Jorge"))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[1].displayName").value("Lucía"))
                .andExpect(jsonPath("$[1].email").value(partner.email()))
                .andExpect(jsonPath("$[1].role").value("MEMBER"));
    }

    @Test
    void invitationCodeAvoidsAmbiguousCharacters() throws Exception {
        TestUser jorge = register("Jorge");
        String householdId = createHousehold(jorge, "Casa");

        mvc.perform(as(jorge, post("/api/v1/households/" + householdId + "/invitations")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", matchesPattern("[A-HJKMNP-Z2-9]{8}")))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void joiningTwiceIsAConflict() throws Exception {
        TestUser jorge = register("Jorge");
        TestUser partner = register("Lucía");
        String householdId = createHousehold(jorge, "Casa");
        String code = invite(jorge, householdId);

        join(partner, code).andExpect(status().isOk());
        join(partner, code).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
        join(jorge, code).andExpect(status().isConflict());
    }

    @Test
    void unknownOrExpiredInvitationIsRejected() throws Exception {
        TestUser jorge = register("Jorge");
        TestUser partner = register("Lucía");
        String householdId = createHousehold(jorge, "Casa");
        String code = invite(jorge, householdId);

        join(partner, "ZZZZZZZZ")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));

        clock.advance(Duration.ofDays(8));

        join(partner, code).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
    }

    @Test
    void onlyTheOwnerCanRenameOrDelete() throws Exception {
        TestUser jorge = register("Jorge");
        TestUser partner = register("Lucía");
        String householdId = createHousehold(jorge, "Casa");
        join(partner, invite(jorge, householdId)).andExpect(status().isOk());
        String base = "/api/v1/households/" + householdId;

        mvc.perform(as(partner, json(patch(base), """
                        {"name": "Mi casa"}
                        """)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_HOUSEHOLD_OWNER"));
        mvc.perform(as(partner, delete(base))).andExpect(status().isForbidden());

        mvc.perform(as(jorge, json(patch(base), """
                        {"name": "Casa nueva"}
                        """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Casa nueva"))
                .andExpect(jsonPath("$.memberCount").value(2));
    }

    @Test
    void anyMemberCanInvite() throws Exception {
        TestUser jorge = register("Jorge");
        TestUser partner = register("Lucía");
        TestUser flatmate = register("Marta");
        String householdId = createHousehold(jorge, "Casa");
        join(partner, invite(jorge, householdId)).andExpect(status().isOk());

        join(flatmate, invite(partner, householdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(3));
    }

    @Test
    void memberCanLeaveButOwnerCannot() throws Exception {
        TestUser jorge = register("Jorge");
        TestUser partner = register("Lucía");
        String householdId = createHousehold(jorge, "Casa");
        join(partner, invite(jorge, householdId)).andExpect(status().isOk());
        String members = "/api/v1/households/" + householdId + "/members/";

        mvc.perform(as(jorge, delete(members + jorge.id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNER_CANNOT_LEAVE"));

        mvc.perform(as(partner, delete(members + partner.id()))).andExpect(status().isNoContent());

        expectHouseholdNotFound(mvc.perform(as(partner, get("/api/v1/households/" + householdId))));
        mvc.perform(as(partner, get("/api/v1/households"))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void ownerCanRemoveMembersButMembersCannotRemoveOthers() throws Exception {
        TestUser jorge = register("Jorge");
        TestUser partner = register("Lucía");
        TestUser flatmate = register("Marta");
        String householdId = createHousehold(jorge, "Casa");
        String code = invite(jorge, householdId);
        join(partner, code).andExpect(status().isOk());
        join(flatmate, code).andExpect(status().isOk());
        String members = "/api/v1/households/" + householdId + "/members/";

        mvc.perform(as(partner, delete(members + flatmate.id())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_HOUSEHOLD_OWNER"));

        mvc.perform(as(jorge, delete(members + flatmate.id()))).andExpect(status().isNoContent());
        mvc.perform(as(jorge, delete(members + flatmate.id())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));

        mvc.perform(as(jorge, get("/api/v1/households/" + householdId)))
                .andExpect(jsonPath("$.memberCount").value(2));
    }

    @Test
    void deletingAHouseholdRemovesItForEveryMemberAndInvalidatesInvitations() throws Exception {
        TestUser jorge = register("Jorge");
        TestUser partner = register("Lucía");
        TestUser latecomer = register("Marta");
        String householdId = createHousehold(jorge, "Casa");
        String code = invite(jorge, householdId);
        join(partner, code).andExpect(status().isOk());

        mvc.perform(as(jorge, delete("/api/v1/households/" + householdId))).andExpect(status().isNoContent());

        expectHouseholdNotFound(mvc.perform(as(jorge, get("/api/v1/households/" + householdId))));
        mvc.perform(as(partner, get("/api/v1/households"))).andExpect(jsonPath("$", hasSize(0)));
        join(latecomer, code).andExpect(status().isNotFound());
    }

    private static void expectHouseholdNotFound(ResultActions result) throws Exception {
        result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
    }
}
