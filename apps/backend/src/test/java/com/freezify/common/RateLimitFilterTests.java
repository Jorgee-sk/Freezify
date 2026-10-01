package com.freezify.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class RateLimitFilterTests {

    private final RateLimitFilter filter = new RateLimitFilter(
            new RateLimitProperties(3, Duration.ofMinutes(1), List.of("/api/v1/auth/")),
            new ProblemWriter(JsonMapper.builder().build()));

    @Test
    void blocksAClientThatExceedsTheLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(call("/api/v1/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = call("/api/v1/auth/login", "10.0.0.1");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentType()).startsWith("application/problem+json");
        assertThat(blocked.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");
    }

    @Test
    void limitsEachClientSeparately() throws Exception {
        for (int i = 0; i < 3; i++) {
            call("/api/v1/auth/login", "10.0.0.1");
        }

        assertThat(call("/api/v1/auth/login", "10.0.0.1").getStatus()).isEqualTo(429);
        assertThat(call("/api/v1/auth/login", "10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void sharesTheAllowanceAcrossLimitedPaths() throws Exception {
        call("/api/v1/auth/login", "10.0.0.1");
        call("/api/v1/auth/register", "10.0.0.1");
        call("/api/v1/auth/refresh", "10.0.0.1");

        assertThat(call("/api/v1/auth/login", "10.0.0.1").getStatus()).isEqualTo(429);
    }

    @Test
    void ignoresPathsThatAreNotConfigured() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("/api/v1/households", "10.0.0.1").getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse call(String path, String remoteAddress) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remoteAddress);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
