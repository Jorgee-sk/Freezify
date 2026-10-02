package com.freezify.notifications.internal;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends through the Firebase Cloud Messaging HTTP v1 API, authenticating as the project's service account.
 *
 * <p>The service account key signs a short-lived assertion, which Google exchanges for an access token; the
 * token is reused until shortly before it expires. Neither the key nor device tokens are ever logged.
 */
class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);

    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final Duration ASSERTION_LIFETIME = Duration.ofMinutes(10);
    /** A token this close to expiring is not used for a new request. */
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(1);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private record AccessToken(String value, Instant expiresAt) {}

    private final String projectId;
    private final String clientEmail;
    private final String privateKeyId;
    private final PrivateKey privateKey;
    private final URI tokenUri;
    private final URI sendUri;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private @Nullable AccessToken accessToken;

    private FcmPushSender(
            String projectId,
            String clientEmail,
            String privateKeyId,
            PrivateKey privateKey,
            URI tokenUri,
            URI fcmEndpoint,
            JsonMapper jsonMapper,
            Clock clock) {
        this.projectId = projectId;
        this.clientEmail = clientEmail;
        this.privateKeyId = privateKeyId;
        this.privateKey = privateKey;
        this.tokenUri = tokenUri;
        this.sendUri = fcmEndpoint.resolve("/v1/projects/" + projectId + "/messages:send");
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /**
     * @throws IllegalStateException when the file cannot be read or is not a service account key
     */
    static FcmPushSender fromCredentialsFile(Path file, URI fcmEndpoint, JsonMapper jsonMapper, Clock clock) {
        try {
            JsonNode key = jsonMapper.readTree(Files.readString(file));
            String pem = key.required("private_key").asString();
            byte[] der = Base64.getMimeDecoder()
                    .decode(pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", ""));
            return new FcmPushSender(
                    key.required("project_id").asString(),
                    key.required("client_email").asString(),
                    key.required("private_key_id").asString(),
                    KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der)),
                    URI.create(key.required("token_uri").asString()),
                    fcmEndpoint,
                    jsonMapper,
                    clock);
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            // The cause may quote the file; only say which file it was.
            throw new IllegalStateException("Cannot use the Firebase service account key at " + file + ": "
                    + e.getClass().getSimpleName());
        }
    }

    String projectId() {
        return projectId;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Result send(String deviceToken, PushMessage message) {
        return send(deviceToken, message, false);
    }

    /**
     * @param validateOnly asks Firebase to check the request without delivering anything
     */
    Result send(String deviceToken, PushMessage message, boolean validateOnly) {
        try {
            Map<String, Object> fcmMessage = new LinkedHashMap<>();
            fcmMessage.put("token", deviceToken);
            fcmMessage.put("notification", Map.of("title", message.title(), "body", message.body()));
            fcmMessage.put("data", message.data());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", fcmMessage);
            if (validateOnly) {
                body.put("validate_only", true);
            }

            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(sendUri)
                            .timeout(TIMEOUT)
                            .header("Authorization", "Bearer " + accessToken())
                            .header("Content-Type", "application/json; charset=UTF-8")
                            .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return Result.SENT;
            }
            String status = errorStatus(response.body());
            if (response.statusCode() == 404 || "UNREGISTERED".equals(status)) {
                return Result.UNREGISTERED;
            }
            if (response.statusCode() == 401) {
                // The token may have been revoked; the next send asks for a new one.
                forgetAccessToken();
            }
            log.warn("Firebase rejected a push message: HTTP {} {}", response.statusCode(), status);
            return Result.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        } catch (IOException | RuntimeException e) {
            log.warn("Could not send a push message: {}", e.toString());
            return Result.FAILED;
        }
    }

    private synchronized void forgetAccessToken() {
        accessToken = null;
    }

    private synchronized String accessToken() throws IOException, InterruptedException {
        Instant now = clock.instant();
        if (accessToken != null && now.isBefore(accessToken.expiresAt().minus(EXPIRY_MARGIN))) {
            return accessToken.value();
        }
        String form = "grant_type=" + encode("urn:ietf:params:oauth:grant-type:jwt-bearer") + "&assertion="
                + encode(assertion(now));
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(tokenUri)
                        .timeout(TIMEOUT)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new UncheckedIOException(
                    new IOException("Google did not issue an access token: HTTP " + response.statusCode()));
        }
        JsonNode json = jsonMapper.readTree(response.body());
        accessToken = new AccessToken(
                json.required("access_token").asString(),
                now.plusSeconds(json.required("expires_in").asLong()));
        return accessToken.value();
    }

    /** "I am this service account and I want to send messages", signed with its private key. */
    private String assertion(Instant now) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(privateKeyId).build(),
                    new JWTClaimsSet.Builder()
                            .issuer(clientEmail)
                            .audience(tokenUri.toString())
                            .claim("scope", SCOPE)
                            .issueTime(Date.from(now))
                            .expirationTime(Date.from(now.plus(ASSERTION_LIFETIME)))
                            .build());
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot sign with the Firebase service account key", e);
        }
    }

    /** The {@code error.status} of a Google API error, or its FCM error code when there is one. */
    private @Nullable String errorStatus(String responseBody) {
        try {
            JsonNode error = jsonMapper.readTree(responseBody).path("error");
            for (JsonNode detail : error.path("details")) {
                if (detail.has("errorCode")) {
                    return detail.path("errorCode").asString();
                }
            }
            return error.path("status").asString(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
