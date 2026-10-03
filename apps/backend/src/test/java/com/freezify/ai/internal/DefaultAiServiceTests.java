package com.freezify.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.freezify.ai.AiService.ReceiptLine;
import com.freezify.common.Today;
import com.freezify.food.Unit;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DefaultAiServiceTests {

    private static final String RECEIPT = """
            MERCADONA S.A. A-46103834
            TARJ: ************1234 AUT: 0812345
            1 PECH POLLO       5,10
            1 LECHE ENTERA 1L  0,89
            """;

    private final List<AiProvider.StructuredPrompt> prompts = new ArrayList<>();
    private String answer = "";

    private final AiProvider provider = prompt -> {
        prompts.add(prompt);
        return Optional.of(answer);
    };

    private DefaultAiService service(AiProvider provider, int dailyCalls) {
        return new DefaultAiService(
                provider,
                new AiProperties("openai-compatible", null, null, "model", Duration.ofSeconds(1), dailyCalls),
                JsonMapper.builder().build(),
                new Today(Clock.systemUTC(), ZoneId.of("Europe/Madrid")));
    }

    @Test
    void aValidAnswerBecomesTheLinesOfTheReceipt() {
        answer = """
                {"lines": [
                  {"text": "PECH POLLO", "name": "Pechuga de pollo", "amount": 0, "unit": "UNIT", "price": 5.10},
                  {"text": "LECHE  ENTERA 1L", "name": "Leche entera", "amount": 1, "unit": "LITER", "price": 0.89}
                ]}""";

        Optional<List<ReceiptLine>> lines = service(provider, 5).readReceipt(RECEIPT, UUID.randomUUID());

        assertThat(lines).hasValue(List.of(
                // "0" means the receipt did not say: nothing is made up.
                new ReceiptLine("PECH POLLO", "Pechuga de pollo", null, null, new BigDecimal("5.1")),
                new ReceiptLine("LECHE  ENTERA 1L", "Leche entera", new BigDecimal("1"), Unit.LITER, new BigDecimal("0.89"))));
        AiProvider.StructuredPrompt prompt = prompts.getFirst();
        assertThat(prompt.schemaName()).isEqualTo("receipt");
        assertThat(prompt.instructions()).contains("Never add a product that is not in the text");
        // Card, tax and authorisation numbers are not the model's business.
        assertThat(prompt.input()).doesNotContain("46103834", "0812345").contains("1 PECH POLLO");
    }

    @Test
    void anAnswerThatDoesNotFollowTheSchemaIsThrownAway() {
        List<String> wrong = List.of(
                "not json",
                "{\"products\": []}",
                "{\"lines\": [{\"text\": \"PECH POLLO\", \"name\": \"Pollo\", \"amount\": 1, \"unit\": \"BOX\", \"price\": 1}]}",
                "{\"lines\": [{\"text\": \"PECH POLLO\", \"name\": \"\", \"amount\": 1, \"unit\": \"UNIT\", \"price\": 1}]}",
                "{\"lines\": [{\"text\": \"PECH POLLO\", \"name\": \"Pollo\", \"amount\": -1, \"unit\": \"UNIT\", \"price\": 1}]}",
                "{\"lines\": [{\"text\": \"PECH POLLO\", \"name\": \"Pollo\", \"amount\": \"1\", \"unit\": \"UNIT\", \"price\": 1}]}");
        for (String each : wrong) {
            answer = each;
            assertThat(service(provider, 50).readReceipt(RECEIPT, UUID.randomUUID())).as(each).isEmpty();
        }
    }

    @Test
    void anAnswerThatInventsAProductIsThrownAwayWhole() {
        answer = """
                {"lines": [
                  {"text": "PECH POLLO", "name": "Pechuga de pollo", "amount": 0, "unit": "UNIT", "price": 5.10},
                  {"text": "CAVIAR BELUGA", "name": "Caviar", "amount": 0, "unit": "UNIT", "price": 99}
                ]}""";

        assertThat(service(provider, 5).readReceipt(RECEIPT, UUID.randomUUID())).isEmpty();
    }

    @Test
    void eachPersonHasADailyAllowance() {
        answer = "{\"lines\": []}";
        DefaultAiService service = service(provider, 2);
        UUID ana = UUID.randomUUID();

        assertThat(service.readReceipt(RECEIPT, ana)).hasValue(List.of());
        assertThat(service.readReceipt(RECEIPT, ana)).hasValue(List.of());
        assertThat(service.readReceipt(RECEIPT, ana)).isEmpty();
        assertThat(prompts).hasSize(2);
        // Someone else still has theirs.
        assertThat(service.readReceipt(RECEIPT, UUID.randomUUID())).hasValue(List.of());
    }

    @Test
    void withoutAModelNothingIsAsked() {
        DefaultAiService service = service(new NoAiProvider(), 5);

        assertThat(service.enabled()).isFalse();
        assertThat(service.readReceipt(RECEIPT, UUID.randomUUID())).isEmpty();
    }

    @Test
    void aFailedCallGivesNothing() {
        assertThat(service(prompt -> Optional.empty(), 5).readReceipt(RECEIPT, UUID.randomUUID())).isEmpty();
    }
}
