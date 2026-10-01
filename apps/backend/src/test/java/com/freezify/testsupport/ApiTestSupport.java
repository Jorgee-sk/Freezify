package com.freezify.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Full application against embedded PostgreSQL, driven through the HTTP layer. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({EmbeddedPostgresConfig.class, ApiTestSupport.ClockConfig.class})
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "correct-horse-battery";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    public record TestUser(String id, String email, String accessToken, String refreshToken) {}

    /** The database is shared by all tests, so every test works with its own users. */
    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected TestUser register(String displayName) throws Exception {
        String email = uniqueEmail();
        String body = mvc.perform(json(post("/api/v1/auth/register"), """
                        {"email": "%s", "password": "%s", "displayName": "%s"}
                        """.formatted(email, PASSWORD, displayName)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new TestUser(
                JsonPath.read(body, "$.user.id"),
                email,
                JsonPath.read(body, "$.accessToken"),
                JsonPath.read(body, "$.refreshToken"));
    }

    protected String createHousehold(TestUser owner, String name) throws Exception {
        String body = mvc.perform(as(owner, json(post("/api/v1/households"), """
                        {"name": "%s"}
                        """.formatted(name))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    protected String invite(TestUser member, String householdId) throws Exception {
        String body = mvc.perform(as(member, post("/api/v1/households/" + householdId + "/invitations")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.code");
    }

    protected ResultActions join(TestUser user, String code) throws Exception {
        return mvc.perform(as(user, json(post("/api/v1/households/join"), """
                {"code": "%s"}
                """.formatted(code))));
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected static MockHttpServletRequestBuilder as(TestUser user, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.accessToken());
    }
}
