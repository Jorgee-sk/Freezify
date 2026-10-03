package com.freezify.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AiConfigTests {

    private final AiConfig config = new AiConfig();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void withoutAProviderTheApplicationWorksWithoutAModel() {
        assertThat(config.aiProvider(properties("none", null, null), jsonMapper)).isInstanceOf(NoAiProvider.class);
    }

    @Test
    void anOpenAiCompatibleProviderTalksToTheConfiguredService() {
        AiProvider provider = config.aiProvider(
                properties("openai-compatible", URI.create("https://generativelanguage.googleapis.com/v1beta/openai"),
                        "gemini-model"),
                jsonMapper);

        assertThat(provider).isInstanceOf(OpenAiCompatibleProvider.class);
        assertThat(((OpenAiCompatibleProvider) provider).completionsUri())
                .isEqualTo(URI.create("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"));
    }

    @Test
    void anIncompleteConfigurationStopsTheStartup() {
        assertThatThrownBy(() -> config.aiProvider(properties("openai-compatible", null, "model"), jsonMapper))
                .hasMessageContaining("FREEZIFY_AI_BASE_URL");
        assertThatThrownBy(() -> config.aiProvider(
                        properties("openai-compatible", URI.create("http://localhost:11434/v1"), " "), jsonMapper))
                .hasMessageContaining("FREEZIFY_AI_MODEL");
        assertThatThrownBy(() -> config.aiProvider(properties("gpt", null, null), jsonMapper))
                .hasMessageContaining("Unknown freezify.ai.provider");
    }

    private static AiProperties properties(String provider, URI baseUrl, String model) {
        return new AiProperties(provider, baseUrl, "key", model, Duration.ofSeconds(5), 10);
    }
}
