package com.freezify.ai.internal;

import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * @param provider          {@code none} or {@code openai-compatible}
 * @param baseUrl           where the OpenAI-compatible API lives, for example Gemini's or a local Ollama
 * @param apiKey            the provider's key; a local model needs none
 * @param model             the model to ask
 * @param timeout           how long a call may take before the application goes on without it
 * @param dailyCallsPerUser how many calls each person may cause per day, to keep costs and free quotas in hand
 */
@ConfigurationProperties("freezify.ai")
record AiProperties(
        String provider,
        @Nullable URI baseUrl,
        @Nullable String apiKey,
        @Nullable String model,
        Duration timeout,
        int dailyCallsPerUser) {}

@Configuration(proxyBeanMethods = false)
class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /**
     * A model is optional: without one, receipts are read by rules. A provider that is chosen but incompletely
     * configured stops the startup, so that a broken deployment is noticed.
     */
    @Bean
    AiProvider aiProvider(AiProperties properties, JsonMapper jsonMapper) {
        return switch (properties.provider()) {
            case "none" -> {
                log.info("No language model configured: receipts are read by rules only");
                yield new NoAiProvider();
            }
            case "openai-compatible" -> {
                if (properties.baseUrl() == null || properties.model() == null || properties.model().isBlank()) {
                    throw new IllegalStateException(
                            "freezify.ai.provider=openai-compatible needs FREEZIFY_AI_BASE_URL and FREEZIFY_AI_MODEL");
                }
                OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                        properties.baseUrl(), properties.apiKey(), properties.model(), properties.timeout(), jsonMapper);
                log.info("Language model {} at {}", properties.model(), provider.completionsUri().getHost());
                yield provider;
            }
            default -> throw new IllegalStateException(
                    "Unknown freezify.ai.provider '" + properties.provider() + "': use none or openai-compatible");
        };
    }
}
