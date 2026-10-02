package com.freezify.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.freezify.notifications.internal.PushSender.PushMessage;
import com.freezify.notifications.internal.PushSender.Result;
import com.freezify.testsupport.MutableClock;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The Firebase client against a local server that plays Google's token endpoint and the FCM API. */
class FcmPushSenderTests {

    private static final PushMessage MESSAGE =
            new PushMessage("Casa", "Leche caduca en 1 día", Map.of("householdId", "h1"));

    private record Received(String path, String authorization, String body) {}

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final MutableClock clock = new MutableClock();
    private final List<Received> received = new ArrayList<>();
    private HttpServer google;
    private KeyPair keyPair;
    private int sendStatus = 200;
    private String sendBody = "{\"name\": \"projects/demo/messages/1\"}";
    private int tokenStatus = 200;

    @TempDir
    Path directory;

    @BeforeEach
    void startGoogle() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        google = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        google.createContext("/token", exchange -> {
            record(exchange);
            respond(exchange, tokenStatus, "{\"access_token\": \"access-" + count("/token") + "\", \"expires_in\": 3600}");
        });
        google.createContext("/v1/projects/demo/messages:send", exchange -> {
            record(exchange);
            respond(exchange, sendStatus, sendBody);
        });
        google.start();
    }

    @AfterEach
    void stopGoogle() {
        google.stop(0);
    }

    @Test
    void sendsTheMessageAuthenticatedAsTheServiceAccount() throws Exception {
        Result result = sender().send("device-1", MESSAGE);

        assertThat(result).isEqualTo(Result.SENT);
        Received send = last("/v1/projects/demo/messages:send");
        assertThat(send.authorization()).isEqualTo("Bearer access-1");
        JsonNode message = jsonMapper.readTree(send.body()).path("message");
        assertThat(message.path("token").asString()).isEqualTo("device-1");
        assertThat(message.path("notification").path("title").asString()).isEqualTo("Casa");
        assertThat(message.path("notification").path("body").asString()).isEqualTo("Leche caduca en 1 día");
        assertThat(message.path("data").path("householdId").asString()).isEqualTo("h1");
        assertThat(jsonMapper.readTree(send.body()).has("validate_only")).isFalse();
    }

    @Test
    void asksForAnAccessTokenWithAnAssertionSignedByTheKey() throws Exception {
        sender().send("device-1", MESSAGE);

        Map<String, String> form = form(last("/token").body());
        assertThat(form.get("grant_type")).isEqualTo("urn:ietf:params:oauth:grant-type:jwt-bearer");
        SignedJWT assertion = SignedJWT.parse(form.get("assertion"));
        assertThat(assertion.verify(new RSASSAVerifier((RSAPublicKey) keyPair.getPublic())))
                .isTrue();
        assertThat(assertion.getHeader().getKeyID()).isEqualTo("key-1");
        assertThat(assertion.getJWTClaimsSet().getIssuer()).isEqualTo("push@demo.iam.gserviceaccount.com");
        assertThat(assertion.getJWTClaimsSet().getAudience()).containsExactly(tokenUri());
        assertThat(assertion.getJWTClaimsSet().getStringClaim("scope"))
                .isEqualTo("https://www.googleapis.com/auth/firebase.messaging");
        assertThat(assertion.getJWTClaimsSet().getExpirationTime())
                .isAfter(assertion.getJWTClaimsSet().getIssueTime());
    }

    @Test
    void reusesTheAccessTokenUntilItIsAboutToExpire() throws Exception {
        FcmPushSender sender = sender();
        sender.send("device-1", MESSAGE);
        sender.send("device-2", MESSAGE);
        assertThat(count("/token")).isEqualTo(1);

        clock.advance(Duration.ofMinutes(59).plusSeconds(30));
        sender.send("device-3", MESSAGE);

        assertThat(count("/token")).isEqualTo(2);
        assertThat(last("/v1/projects/demo/messages:send").authorization()).isEqualTo("Bearer access-2");
    }

    @Test
    void canAskFirebaseToCheckAMessageWithoutDeliveringIt() throws Exception {
        sender().send("device-1", MESSAGE, true);

        assertThat(jsonMapper
                        .readTree(last("/v1/projects/demo/messages:send").body())
                        .path("validate_only")
                        .asBoolean())
                .isTrue();
    }

    @Test
    void reportsAnInstallationThatIsGone() throws Exception {
        sendStatus = 404;
        sendBody = """
                {"error": {"code": 404, "status": "NOT_FOUND", "details": [
                  {"@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError", "errorCode": "UNREGISTERED"}]}}
                """;

        assertThat(sender().send("device-1", MESSAGE)).isEqualTo(Result.UNREGISTERED);
    }

    @Test
    void aRejectedMessageIsAFailureThatKeepsTheInstallation() throws Exception {
        sendStatus = 400;
        sendBody = """
                {"error": {"code": 400, "status": "INVALID_ARGUMENT", "details": [
                  {"@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError", "errorCode": "INVALID_ARGUMENT"}]}}
                """;

        assertThat(sender().send("device-1", MESSAGE)).isEqualTo(Result.FAILED);
    }

    @Test
    void asksForANewAccessTokenAfterOneIsRefused() throws Exception {
        FcmPushSender sender = sender();
        sendStatus = 401;
        sendBody = "{\"error\": {\"code\": 401, \"status\": \"UNAUTHENTICATED\"}}";
        assertThat(sender.send("device-1", MESSAGE)).isEqualTo(Result.FAILED);

        sendStatus = 200;
        assertThat(sender.send("device-1", MESSAGE)).isEqualTo(Result.SENT);

        assertThat(count("/token")).isEqualTo(2);
    }

    @Test
    void neverThrowsWhenGoogleCannotBeReachedOrRefusesTheKey() throws Exception {
        tokenStatus = 400;
        assertThat(sender().send("device-1", MESSAGE)).isEqualTo(Result.FAILED);
        assertThat(count("/v1/projects/demo/messages:send")).isZero();

        FcmPushSender sender = sender();
        google.stop(0);
        assertThat(sender.send("device-1", MESSAGE)).isEqualTo(Result.FAILED);
    }

    @Test
    void refusesAFileThatIsNotAServiceAccountKeyWithoutQuotingIt() throws Exception {
        Path notAKey = directory.resolve("not-a-key.json");
        Files.writeString(notAKey, "{\"private_key\": \"super-secret-material\"}");

        assertThatThrownBy(() -> FcmPushSender.fromCredentialsFile(notAKey, endpoint(), jsonMapper, clock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not-a-key.json")
                .hasMessageNotContaining("super-secret-material")
                .hasNoCause();
        assertThatThrownBy(() -> FcmPushSender.fromCredentialsFile(
                        directory.resolve("missing.json"), endpoint(), jsonMapper, clock))
                .isInstanceOf(IllegalStateException.class);
    }

    private FcmPushSender sender() throws IOException {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        Path file = directory.resolve("service-account.json");
        Files.writeString(
                file,
                jsonMapper.writeValueAsString(Map.of(
                        "type", "service_account",
                        "project_id", "demo",
                        "private_key_id", "key-1",
                        "private_key", pem,
                        "client_email", "push@demo.iam.gserviceaccount.com",
                        "token_uri", tokenUri())));
        return FcmPushSender.fromCredentialsFile(file, endpoint(), jsonMapper, clock);
    }

    private URI endpoint() {
        return URI.create("http://127.0.0.1:" + google.getAddress().getPort());
    }

    private String tokenUri() {
        return endpoint() + "/token";
    }

    private synchronized void record(HttpExchange exchange) throws IOException {
        received.add(new Received(
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private synchronized long count(String path) {
        return received.stream().filter(request -> request.path().equals(path)).count();
    }

    private synchronized Received last(String path) {
        return received.stream()
                .filter(request -> request.path().equals(path))
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static Map<String, String> form(String body) {
        Map<String, String> fields = new java.util.HashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            fields.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return fields;
    }
}
