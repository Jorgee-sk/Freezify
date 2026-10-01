package com.freezify.auth;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.freezify.testsupport.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AuthApiTests extends ApiTestSupport {

    @Test
    void registerReturnsSessionAndNeverThePasswordHash() throws Exception {
        String email = uniqueEmail();

        mvc.perform(json(post("/api/v1/auth/register"), """
                        {"email": "%s", "password": "%s", "displayName": "  Jorge  ", "locale": "en"}
                        """.formatted(email.toUpperCase(), PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.displayName").value("Jorge"))
                .andExpect(jsonPath("$.user.locale").value("en"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$..password").doesNotExist());
    }

    @Test
    void registerDefaultsToSpanish() throws Exception {
        TestUser user = register("Ana");

        mvc.perform(as(user, get("/api/v1/users/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.id()))
                .andExpect(jsonPath("$.locale").value("es"));
    }

    @Test
    void registerRejectsDuplicateEmailIgnoringCase() throws Exception {
        TestUser existing = register("Ana");

        mvc.perform(json(post("/api/v1/auth/register"), """
                        {"email": "%s", "password": "%s", "displayName": "Other"}
                        """.formatted(existing.email().toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void registerValidatesInput() throws Exception {
        mvc.perform(json(post("/api/v1/auth/register"), """
                        {"email": "not-an-email", "password": "short", "displayName": " ", "locale": "fr"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId", notNullValue()))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("displayName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("locale")));
    }

    @Test
    void registerRejectsPasswordLongerThan72Bytes() throws Exception {
        // 40 characters, 80 bytes in UTF-8: passes the length check but not the bcrypt limit.
        String password = "ñ".repeat(40);

        mvc.perform(json(post("/api/v1/auth/register"), """
                        {"email": "%s", "password": "%s", "displayName": "Ana"}
                        """.formatted(uniqueEmail(), password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mvc.perform(json(post("/api/v1/auth/login"), "{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void loginWithCorrectCredentials() throws Exception {
        TestUser user = register("Ana");

        mvc.perform(json(post("/api/v1/auth/login"), """
                        {"email": "%s", "password": "%s"}
                        """.formatted(user.email(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.user.id").value(user.id()));
    }

    @Test
    void loginFailuresDoNotRevealWhetherTheEmailExists() throws Exception {
        TestUser user = register("Ana");

        String wrongPassword = mvc.perform(json(post("/api/v1/auth/login"), """
                        {"email": "%s", "password": "wrong-password"}
                        """.formatted(user.email())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String unknownEmail = mvc.perform(json(post("/api/v1/auth/login"), """
                        {"email": "%s", "password": "wrong-password"}
                        """.formatted(uniqueEmail())))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(JsonPath.<String>read(unknownEmail, "$.detail"))
                .isEqualTo(JsonPath.<String>read(wrongPassword, "$.detail"));
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithTamperedSignatureIsRejected() throws Exception {
        TestUser user = register("Ana");
        String token = user.accessToken();
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredAccessTokenIsRejected() throws Exception {
        // Issued one hour in the past, so its 15 minute lifetime is long over.
        clock.advance(Duration.ofHours(-1));
        TestUser user = register("Ana");
        clock.reset();

        mvc.perform(as(user, get("/api/v1/users/me"))).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTheToken() throws Exception {
        TestUser user = register("Ana");

        String body = refresh(user.refreshToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken", not(user.refreshToken())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String newAccessToken = JsonPath.read(body, "$.accessToken");
        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.id()));
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeFamily() throws Exception {
        TestUser user = register("Ana");
        String rotated = JsonPath.read(
                refresh(user.refreshToken()).andReturn().getResponse().getContentAsString(), "$.refreshToken");

        // The original token was already used: this is what a thief (or the victim) would do.
        refresh(user.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // The legitimate successor must now be dead too.
        refresh(rotated).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        TestUser user = register("Ana");

        clock.advance(Duration.ofDays(31));

        refresh(user.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void unknownRefreshTokenIsRejected() throws Exception {
        refresh("does-not-exist").andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshTokenAndIsIdempotent() throws Exception {
        TestUser user = register("Ana");
        String logoutBody = """
                {"refreshToken": "%s"}
                """.formatted(user.refreshToken());

        mvc.perform(json(post("/api/v1/auth/logout"), logoutBody)).andExpect(status().isNoContent());
        mvc.perform(json(post("/api/v1/auth/logout"), logoutBody)).andExpect(status().isNoContent());

        refresh(user.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void profileCanBeUpdatedPartially() throws Exception {
        TestUser user = register("Ana");

        mvc.perform(as(user, json(patch("/api/v1/users/me"), """
                        {"locale": "en"}
                        """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("en"))
                .andExpect(jsonPath("$.displayName").value("Ana"));

        mvc.perform(as(user, json(patch("/api/v1/users/me"), """
                        {"displayName": "Ana María"}
                        """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("en"))
                .andExpect(jsonPath("$.displayName").value("Ana María"));

        mvc.perform(as(user, json(patch("/api/v1/users/me"), """
                        {"displayName": "   "}
                        """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void everyResponseCarriesACorrelationId() throws Exception {
        mvc.perform(get("/api/v1/users/me").header("X-Correlation-Id", "client-trace-42"))
                .andExpect(header().string("X-Correlation-Id", "client-trace-42"))
                .andExpect(jsonPath("$.correlationId").value("client-trace-42"));

        mvc.perform(get("/api/v1/users/me").header("X-Correlation-Id", "bad value\twith spaces"))
                .andExpect(header().string("X-Correlation-Id", not("bad value\twith spaces")));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(json(post("/api/v1/auth/refresh"), """
                {"refreshToken": "%s"}
                """.formatted(refreshToken)));
    }
}
