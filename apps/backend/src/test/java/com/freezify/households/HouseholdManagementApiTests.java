package com.freezify.households;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.freezify.testsupport.ApiTestSupport;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class HouseholdManagementApiTests extends ApiTestSupport {

    private TestUser jorge;
    private TestUser lucia;
    private String householdId;

    @BeforeEach
    void householdWithTwoMembers() throws Exception {
        jorge = register("Jorge");
        lucia = register("Lucía");
        householdId = createHousehold(jorge, "Casa");
        join(lucia, invite(jorge, householdId)).andExpect(status().isOk());
    }

    @Test
    void membersSeeTheCodesThatStillLetSomeoneJoin() throws Exception {
        String first = invite(jorge, householdId);
        String second = invite(lucia, householdId);

        invitations(lucia)
                .andExpect(status().isOk())
                // The code used to let Lucía in is still valid: codes can be used by several people.
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[1].code").value(first))
                .andExpect(jsonPath("$[1].createdBy").value(jorge.id()))
                .andExpect(jsonPath("$[2].code").value(second))
                .andExpect(jsonPath("$[2].createdBy").value(lucia.id()));

        // Expired codes are not listed.
        clock.advance(Duration.ofDays(8));
        invitations(jorge).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void aRevokedCodeNoLongerLetsAnyoneJoin() throws Exception {
        String code = invite(jorge, householdId);
        TestUser late = register("Tarde");

        mvc.perform(as(jorge, delete(invitation(code)))).andExpect(status().isNoContent());

        join(late, code).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
        // Those who already joined stay.
        mvc.perform(as(lucia, get("/api/v1/households/" + householdId))).andExpect(status().isOk());
        mvc.perform(as(jorge, delete(invitation(code))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
    }

    @Test
    void whoeverGeneratedACodeAndTheOwnerCanRevokeIt() throws Exception {
        String jorgesCode = invite(jorge, householdId);
        String luciasCode = invite(lucia, householdId);

        mvc.perform(as(lucia, delete(invitation(jorgesCode))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_HOUSEHOLD_OWNER"));
        mvc.perform(as(lucia, delete(invitation(luciasCode.toLowerCase())))).andExpect(status().isNoContent());
        mvc.perform(as(jorge, delete(invitation(jorgesCode)))).andExpect(status().isNoContent());
    }

    @Test
    void codesOfAHouseholdAreOutOfReachForEveryoneElse() throws Exception {
        TestUser stranger = register("Desconocido");
        String code = invite(jorge, householdId);
        String strangersHousehold = createHousehold(stranger, "Otra casa");

        invitations(stranger).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
        mvc.perform(as(stranger, delete(invitation(code))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
        // Nor through a household of their own.
        mvc.perform(as(stranger, delete("/api/v1/households/" + strangersHousehold + "/invitations/" + code)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
        join(register("Otra persona"), code).andExpect(status().isOk());
    }

    @Test
    void theOwnerHandsTheHouseholdOverAndCanThenLeave() throws Exception {
        transfer(jorge, lucia.id()).andExpect(status().isNoContent());

        mvc.perform(as(jorge, get("/api/v1/households/" + householdId))).andExpect(jsonPath("$.role").value("MEMBER"));
        mvc.perform(as(lucia, get("/api/v1/households/" + householdId))).andExpect(jsonPath("$.role").value("OWNER"));
        mvc.perform(as(jorge, get("/api/v1/households/" + householdId + "/members")))
                .andExpect(jsonPath("$[*].role", contains("MEMBER", "OWNER")));
        // The new owner can do what only an owner can; the previous one no longer can.
        mvc.perform(as(lucia, json(patch("/api/v1/households/" + householdId), """
                        {"name": "Casa de Lucía"}
                        """)))
                .andExpect(status().isOk());
        transfer(jorge, jorge.id()).andExpect(status().isForbidden());

        mvc.perform(as(jorge, delete("/api/v1/households/" + householdId + "/members/" + jorge.id())))
                .andExpect(status().isNoContent());
        mvc.perform(as(lucia, get("/api/v1/households/" + householdId))).andExpect(jsonPath("$.memberCount").value(1));
    }

    @Test
    void onlyTheOwnerHandsTheHouseholdOverAndOnlyToAMember() throws Exception {
        TestUser stranger = register("Desconocido");

        transfer(lucia, lucia.id())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_HOUSEHOLD_OWNER"));
        transfer(jorge, stranger.id())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        transfer(stranger, stranger.id())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
        mvc.perform(as(jorge, json(post(owner()), "{}"))).andExpect(status().isBadRequest());
        // Handing it to oneself changes nothing.
        transfer(jorge, jorge.id()).andExpect(status().isNoContent());

        mvc.perform(as(jorge, get("/api/v1/households/" + householdId + "/members")))
                .andExpect(jsonPath("$[*].role", contains("OWNER", "MEMBER")));
        mvc.perform(as(jorge, get("/api/v1/households/" + householdId + "/members")))
                .andExpect(jsonPath("$[0].userId").value(jorge.id()));
        transfer(jorge, UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }

    private ResultActions invitations(TestUser user) throws Exception {
        return mvc.perform(as(user, get("/api/v1/households/" + householdId + "/invitations")));
    }

    private ResultActions transfer(TestUser user, String newOwnerId) throws Exception {
        return mvc.perform(as(user, json(post(owner()), """
                {"userId": "%s"}
                """.formatted(newOwnerId))));
    }

    private String invitation(String code) {
        return "/api/v1/households/" + householdId + "/invitations/" + code;
    }

    private String owner() {
        return "/api/v1/households/" + householdId + "/owner";
    }
}
