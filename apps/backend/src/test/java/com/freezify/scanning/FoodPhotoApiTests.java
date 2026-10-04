package com.freezify.scanning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.freezify.ai.AiService;
import com.freezify.ai.AiService.Answer;
import com.freezify.ai.AiService.FoodGuess;
import com.freezify.ai.AiService.Outcome;
import com.freezify.testsupport.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

class FoodPhotoApiTests extends ApiTestSupport {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    /** The model is replaced: what it answers is checked by the AI module's own tests. */
    @MockitoBean
    private AiService ai;

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser jorge;
    private String householdId;

    @BeforeEach
    void householdWithOwner() throws Exception {
        jorge = register("Jorge");
        householdId = createHousehold(jorge, "Casa");
        when(ai.enabled()).thenReturn(true);
    }

    @Test
    void saysWhichFoodsThePhotoMayShowWithHowSureTheModelIs() throws Exception {
        UUID tomato = UUID.fromString(foodId("Tomate"));
        UUID pepper = UUID.fromString(foodId("Pimiento rojo"));
        when(ai.identifyFood(eq(JPEG), eq("image/jpeg"), anyList(), eq("es"), any())).thenReturn(Answer.ok(List.of(
                new FoodGuess(tomato, "Tomate", 0.81),
                new FoodGuess(pepper, "Pimiento rojo", 0.12),
                new FoodGuess(null, "Caqui", 0.07))));

        identify(jorge, photo(JPEG, "image/jpeg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Tomate", "Pimiento rojo", "Caqui")))
                .andExpect(jsonPath("$[0].foodId").value(tomato.toString()))
                .andExpect(jsonPath("$[0].confidence").value(0.81))
                .andExpect(jsonPath("$[0].category").value("VEGETABLES"))
                .andExpect(jsonPath("$[0].defaultUnit").value("UNIT"))
                .andExpect(jsonPath("$[0].defaultStorage").value("REFRIGERATOR"))
                .andExpect(jsonPath("$[2].foodId").isEmpty())
                .andExpect(jsonPath("$[2].category").value("OTHER"))
                .andExpect(jsonPath("$[2].defaultUnit").isEmpty());
        assertThat(productEvents("food_scanned")).isEqualTo(1);
    }

    @Test
    void theTypeOfThePhotoIsReadFromItsContent() throws Exception {
        when(ai.identifyFood(any(), anyString(), anyList(), anyString(), any())).thenReturn(Answer.ok(List.of()));

        // A PNG said to be a JPEG is sent as what it is.
        identify(jorge, photo(PNG, "image/jpeg")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        verify(ai).identifyFood(eq(PNG), eq("image/png"), anyList(), eq("es"), any());

        // Anything that is not a photo is sent nowhere.
        identify(jorge, photo("<svg onload=alert(1)>".getBytes(), "image/jpeg"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
        byte[] huge = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(JPEG, 0, huge, 0, JPEG.length);
        identify(jorge, photo(huge, "image/jpeg"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        verify(ai, never()).identifyFood(any(), eq("image/jpeg"), anyList(), anyString(), any());
    }

    @Test
    void saysWhyThePhotoWasNotRead() throws Exception {
        when(ai.identifyFood(any(), anyString(), anyList(), anyString(), any())).thenReturn(Answer.not(Outcome.FAILED));
        identify(jorge, photo(JPEG, "image/jpeg"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));

        when(ai.identifyFood(any(), anyString(), anyList(), anyString(), any()))
                .thenReturn(Answer.not(Outcome.LIMIT_REACHED));
        identify(jorge, photo(JPEG, "image/jpeg"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AI_LIMIT_REACHED"));

        when(ai.enabled()).thenReturn(false);
        identify(jorge, photo(JPEG, "image/jpeg"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_NOT_CONFIGURED"));
        assertThat(productEvents("food_scanned")).isZero();
    }

    @Test
    void onlyMembersCanHaveAPhotoRead() throws Exception {
        TestUser stranger = register("Extraño");

        identify(stranger, photo(JPEG, "image/jpeg"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOUSEHOLD_NOT_FOUND"));
        verify(ai, never()).identifyFood(any(), anyString(), anyList(), anyString(), any());
    }

    private static MockMultipartFile photo(byte[] bytes, String contentType) {
        return new MockMultipartFile("image", "photo.jpg", contentType, bytes);
    }

    private ResultActions identify(TestUser user, MockMultipartFile image) throws Exception {
        return mvc.perform(multipart("/api/v1/households/" + householdId + "/scans/food")
                .file(image)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.accessToken()));
    }

    private long productEvents(String name) {
        return jdbc.queryForObject(
                "select count(*) from product_events where name = ? and household_id = ?::uuid",
                Long.class,
                name,
                householdId);
    }

    private String foodId(String name) throws Exception {
        String foods = mvc.perform(as(jorge, get("/api/v1/foods").param("q", name)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> ids = JsonPath.read(foods, "$[?(@.name == '" + name + "')].id");
        assertThat(ids).as("food " + name).hasSize(1);
        return ids.get(0);
    }
}
