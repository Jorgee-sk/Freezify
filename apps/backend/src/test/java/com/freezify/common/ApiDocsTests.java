package com.freezify.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.freezify.testsupport.ApiTestSupport;
import org.junit.jupiter.api.Test;

class ApiDocsTests extends ApiTestSupport {

    /** A deployed instance does not describe its API to anyone who asks, unless FREEZIFY_API_DOCS says so. */
    @Test
    void theApiIsNotDescribedUnlessAskedFor() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }
}
