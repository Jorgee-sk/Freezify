package com.freezify.food;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.freezify.testsupport.ApiTestSupport;
import org.junit.jupiter.api.Test;

class FoodApiTests extends ApiTestSupport {

    @Test
    void namesStartingWithTheTextComeBeforeNamesContainingIt() throws Exception {
        TestUser user = register("Ana");

        mvc.perform(as(user, get("/api/v1/foods").param("q", "tom")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Tomate", "Tomate frito", "Tomate triturado")))
                .andExpect(jsonPath("$[0].id", notNullValue()))
                .andExpect(jsonPath("$[0].category").value("VEGETABLES"))
                .andExpect(jsonPath("$[0].defaultUnit").value("UNIT"))
                .andExpect(jsonPath("$[0].defaultStorage").value("REFRIGERATOR"));

        mvc.perform(as(user, get("/api/v1/foods").param("q", "pollo")))
                .andExpect(jsonPath("$[*].name", contains("Muslo de pollo", "Pechuga de pollo")));
    }

    @Test
    void searchIgnoresAccentsAndCase() throws Exception {
        TestUser user = register("Ana");

        mvc.perform(as(user, get("/api/v1/foods").param("q", "BROCOLI")))
                .andExpect(jsonPath("$[*].name", contains("Brócoli")));
        mvc.perform(as(user, get("/api/v1/foods").param("q", "  limón ")))
                .andExpect(jsonPath("$[*].name", contains("Limón")));
    }

    @Test
    void searchesInTheRequestedLanguage() throws Exception {
        TestUser user = register("Ana");

        mvc.perform(as(user, get("/api/v1/foods").param("q", "egg").param("lang", "en")))
                .andExpect(jsonPath("$[*].name", contains("Eggplant", "Eggs")));
        mvc.perform(as(user, get("/api/v1/foods").param("q", "egg"))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void respectsTheLimitAndReturnsNothingForBlankText() throws Exception {
        TestUser user = register("Ana");

        mvc.perform(as(user, get("/api/v1/foods").param("q", "a").param("limit", "3")))
                .andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(as(user, get("/api/v1/foods").param("q", "  "))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void rejectsInvalidParameters() throws Exception {
        TestUser user = register("Ana");

        mvc.perform(as(user, get("/api/v1/foods").param("q", "a").param("limit", "500")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(as(user, get("/api/v1/foods").param("q", "a").param("lang", "fr")))
                .andExpect(status().isBadRequest());
        mvc.perform(as(user, get("/api/v1/foods"))).andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/foods").param("q", "tom")).andExpect(status().isUnauthorized());
    }
}
