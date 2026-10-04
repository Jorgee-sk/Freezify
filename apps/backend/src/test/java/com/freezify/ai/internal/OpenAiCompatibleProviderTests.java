package com.freezify.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Against a local server that speaks the OpenAI chat completions API, as Gemini, Ollama and others do. */
class OpenAiCompatibleProviderTests {

    private static final AiProvider.StructuredPrompt PROMPT = new AiProvider.StructuredPrompt(
            "Read the receipt.",
            "1 LECHE 0,89",
            "receipt",
            Map.of("type", "object", "required", List.of("lines")));

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = "";
    private volatile long delayMillis = 0;
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private OpenAiCompatibleProvider provider(String path, String apiKey) {
        return new OpenAiCompatibleProvider(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path),
                apiKey,
                "gemini-test",
                Duration.ofMillis(500),
                jsonMapper);
    }

    @Test
    void asksForAnAnswerFollowingTheSchemaAndReturnsIt() {
        responseBody = """
                {"choices": [{"message": {"role": "assistant", "content": "{\\"lines\\": []}"}}]}""";

        assertThat(provider("/v1beta/openai", "secret-key").complete(PROMPT)).hasValue("{\"lines\": []}");

        assertThat(requestPath.get()).isEqualTo("/v1beta/openai/chat/completions");
        assertThat(authorization.get()).isEqualTo("Bearer secret-key");
        JsonNode body = jsonMapper.readTree(requestBody.get());
        assertThat(body.path("model").asString()).isEqualTo("gemini-test");
        assertThat(body.path("temperature").asInt()).isZero();
        assertThat(body.path("messages").path(0).path("role").asString()).isEqualTo("system");
        assertThat(body.path("messages").path(0).path("content").asString()).isEqualTo("Read the receipt.");
        assertThat(body.path("messages").path(1).path("content").asString()).isEqualTo("1 LECHE 0,89");
        JsonNode format = body.path("response_format");
        assertThat(format.path("type").asString()).isEqualTo("json_schema");
        assertThat(format.path("json_schema").path("name").asString()).isEqualTo("receipt");
        assertThat(format.path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(format.path("json_schema").path("schema").path("required").path(0).asString()).isEqualTo("lines");
    }

    @Test
    void aPictureGoesWithTheInputAsADataUrl() {
        responseBody = "{\"choices\": [{\"message\": {\"content\": \"{}\"}}]}";
        byte[] photo = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 42};

        provider("/v1", "key").complete(new AiProvider.StructuredPrompt(
                "Say which food it is.", "{}", "food_photo", Map.of(), new AiProvider.Picture(photo, "image/jpeg")));

        JsonNode content = jsonMapper.readTree(requestBody.get()).path("messages").path(1).path("content");
        assertThat(content.path(0).path("type").asString()).isEqualTo("text");
        assertThat(content.path(0).path("text").asString()).isEqualTo("{}");
        assertThat(content.path(1).path("type").asString()).isEqualTo("image_url");
        assertThat(content.path(1).path("image_url").path("url").asString())
                .isEqualTo("data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(photo));
    }

    @Test
    void aLocalModelNeedsNoKey() {
        responseBody = "{\"choices\": [{\"message\": {\"content\": \"{}\"}}]}";

        assertThat(provider("/v1/", "").complete(PROMPT)).hasValue("{}");

        assertThat(requestPath.get()).isEqualTo("/v1/chat/completions");
        assertThat(authorization.get()).isNull();
    }

    @Test
    void failuresGiveNothing() {
        status = 429;
        responseBody = "{\"error\": {\"message\": \"quota exceeded\"}}";
        assertThat(provider("/v1", "key").complete(PROMPT)).isEmpty();

        status = 200;
        responseBody = "{\"choices\": []}";
        assertThat(provider("/v1", "key").complete(PROMPT)).isEmpty();

        responseBody = "<html>not json</html>";
        assertThat(provider("/v1", "key").complete(PROMPT)).isEmpty();
    }

    @Test
    void aSlowModelIsNotWaitedForBeyondTheTimeout() {
        delayMillis = 2_000;
        responseBody = "{\"choices\": [{\"message\": {\"content\": \"{}\"}}]}";

        long start = System.nanoTime();
        assertThat(provider("/v1", "key").complete(PROMPT)).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1_500));
    }

    @Test
    void anUnreachableModelGivesNothing() {
        OpenAiCompatibleProvider unreachable = new OpenAiCompatibleProvider(
                URI.create("http://127.0.0.1:1/v1"), "key", "model", Duration.ofMillis(500), jsonMapper);

        assertThat(unreachable.complete(PROMPT)).isEmpty();
    }
}
