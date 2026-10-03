package com.freezify.ai.internal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Talks to any service with the OpenAI chat completions API and structured output: Google Gemini (which has a
 * free tier), Ollama running a model locally, OpenAI and others. Which one is a matter of configuration.
 *
 * <p>Neither the key, the prompt nor the answer is logged: receipts are personal data.
 */
class OpenAiCompatibleProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);

    private final URI completionsUri;
    private final @Nullable String apiKey;
    private final String model;
    private final Duration timeout;
    private final JsonMapper jsonMapper;
    private final HttpClient http;

    OpenAiCompatibleProvider(URI baseUrl, @Nullable String apiKey, String model, Duration timeout, JsonMapper jsonMapper) {
        String base = baseUrl.toString();
        this.completionsUri = URI.create(base.endsWith("/") ? base : base + "/").resolve("chat/completions");
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey;
        this.model = model;
        this.timeout = timeout;
        this.jsonMapper = jsonMapper;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    URI completionsUri() {
        return completionsUri;
    }

    @Override
    public Optional<String> complete(StructuredPrompt prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0);
        body.put("messages", List.of(
                Map.of("role", "system", "content", prompt.instructions()),
                Map.of("role", "user", "content", prompt.input())));
        body.put("response_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of("name", prompt.schemaName(), "strict", true, "schema", prompt.schema())));

        HttpRequest.Builder request = HttpRequest.newBuilder(completionsUri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8));
        if (apiKey != null) {
            request.header("Authorization", "Bearer " + apiKey);
        }
        try {
            HttpResponse<String> response =
                    http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                log.warn("The language model answered HTTP {}", response.statusCode());
                return Optional.empty();
            }
            JsonNode content = jsonMapper.readTree(response.body()).path("choices").path(0).path("message").path("content");
            if (!content.isString() || content.asString().isBlank()) {
                log.warn("The language model answered without content");
                return Optional.empty();
            }
            return Optional.of(content.asString());
        } catch (IOException | RuntimeException e) {
            log.warn("The language model could not be reached: {}", e.getClass().getSimpleName());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
