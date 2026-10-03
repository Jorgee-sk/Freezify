package com.freezify.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.freezify.testsupport.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** A browser keeps its refresh token in an HttpOnly cookie that scripts cannot read. */
class BrowserSessionApiTests extends ApiTestSupport {

    private static final String HEADER = "X-Freezify-Session";
    private static final String COOKIE = "freezify_refresh";

    @Test
    void signingInFromABrowserPutsTheRefreshTokenInAnHttpOnlyCookie() throws Exception {
        TestUser jorge = register("Jorge");

        MockHttpServletResponse response = login(jorge)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                // Scripts never see it.
                .andExpect(jsonPath("$.refreshToken").isEmpty())
                .andReturn()
                .getResponse();

        String cookie = setCookie(response);
        assertThat(cookie)
                .startsWith(COOKIE + "=")
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth", "Max-Age=2592000");
        assertThat(value(cookie)).isNotBlank();
    }

    @Test
    void registeringFromABrowserDoesTheSame() throws Exception {
        MockHttpServletResponse response = mvc.perform(browser(json(post("/api/v1/auth/register"), """
                        {"email": "%s", "password": "%s", "displayName": "Ana"}
                        """.formatted(uniqueEmail(), PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refreshToken").isEmpty())
                .andReturn()
                .getResponse();

        assertThat(setCookie(response)).contains("HttpOnly");
    }

    @Test
    void theCookieRenewsTheSessionAndIsRotated() throws Exception {
        String first = value(setCookie(login(register("Jorge")).andReturn().getResponse()));

        MockHttpServletResponse renewed = refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isEmpty())
                .andReturn()
                .getResponse();
        String second = value(setCookie(renewed));
        assertThat(second).isNotBlank().isNotEqualTo(first);

        String accessToken = JsonPath.read(renewed.getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        // A cookie used twice means it was stolen: it fails, and the browser is told to forget it.
        MockHttpServletResponse reused =
                refresh(first).andExpect(status().isUnauthorized()).andReturn().getResponse();
        assertThat(setCookie(reused)).contains("Max-Age=0");
        // As with any refresh token, the reuse ends the whole session.
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void theCookieIsIgnoredWithoutTheHeaderThatOnlyTheAppSends() throws Exception {
        String cookie = value(setCookie(login(register("Jorge")).andReturn().getResponse()));

        // A form or a link from another site sends the cookie but cannot add the header.
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, cookie)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        // The cookie was not spent: the app can still use it.
        refresh(cookie).andExpect(status().isOk());
    }

    @Test
    void withoutACookieThereIsNothingToRenew() throws Exception {
        mvc.perform(browser(post("/api/v1/auth/refresh")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void signingOutRevokesTheCookieAndTellsTheBrowserToForgetIt() throws Exception {
        String cookie = value(setCookie(login(register("Jorge")).andReturn().getResponse()));

        MockHttpServletResponse response = mvc.perform(
                        browser(post("/api/v1/auth/logout")).cookie(new Cookie(COOKIE, cookie)))
                .andExpect(status().isNoContent())
                .andReturn()
                .getResponse();

        assertThat(setCookie(response)).startsWith(COOKIE + "=;").contains("Max-Age=0");
        refresh(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    void aRefreshTokenFromBeforeTheCookieMovesIntoIt() throws Exception {
        // The web kept the token in localStorage before: it hands it over once and gets the cookie back.
        TestUser jorge = register("Jorge");

        MockHttpServletResponse response = mvc.perform(browser(json(post("/api/v1/auth/refresh"), """
                        {"refreshToken": "%s"}
                        """.formatted(jorge.refreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isEmpty())
                .andReturn()
                .getResponse();

        refresh(value(setCookie(response))).andExpect(status().isOk());
    }

    @Test
    void theAppOnPhonesKeepsGettingTheTokenInTheBody() throws Exception {
        TestUser jorge = register("Jorge");

        MockHttpServletResponse response = mvc.perform(json(post("/api/v1/auth/login"), """
                        {"email": "%s", "password": "%s"}
                        """.formatted(jorge.email(), PASSWORD)))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn()
                .getResponse();

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void anAllowedOriginMaySendTheHeader() throws Exception {
        mvc.perform(options("/api/v1/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HEADER))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, org.hamcrest.Matchers.containsStringIgnoringCase(HEADER)));
        mvc.perform(options("/api/v1/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HEADER))
                .andExpect(status().isForbidden());
    }

    private ResultActions login(TestUser user) throws Exception {
        return mvc.perform(browser(json(post("/api/v1/auth/login"), """
                {"email": "%s", "password": "%s"}
                """.formatted(user.email(), PASSWORD))));
    }

    private ResultActions refresh(String cookie) throws Exception {
        return mvc.perform(browser(post("/api/v1/auth/refresh")).cookie(new Cookie(COOKIE, cookie)));
    }

    private static MockHttpServletRequestBuilder browser(MockHttpServletRequestBuilder request) {
        return request.header(HEADER, "cookie");
    }

    private static String setCookie(MockHttpServletResponse response) {
        List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).as("Set-Cookie").hasSize(1);
        return cookies.get(0);
    }

    private static String value(String setCookie) {
        return setCookie.substring((COOKIE + "=").length(), setCookie.indexOf(';'));
    }
}
