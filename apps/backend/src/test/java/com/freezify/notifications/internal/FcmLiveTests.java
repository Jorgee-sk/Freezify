package com.freezify.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.freezify.notifications.internal.PushSender.PushMessage;
import com.freezify.notifications.internal.PushSender.Result;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Talks to the real Firebase project. Skipped unless {@code FREEZIFY_FCM_CREDENTIALS_FILE} points at a service
 * account key, so it never runs in CI. Nothing is delivered: Firebase is only asked to check the message.
 */
@EnabledIfEnvironmentVariable(named = "FREEZIFY_FCM_CREDENTIALS_FILE", matches = ".+")
class FcmLiveTests {

    @Test
    void firebaseAcceptsTheServiceAccountAndOnlyObjectsToTheMadeUpDevice() {
        FcmPushSender sender = FcmPushSender.fromCredentialsFile(
                Path.of(System.getenv("FREEZIFY_FCM_CREDENTIALS_FILE")),
                URI.create("https://fcm.googleapis.com"),
                JsonMapper.builder().build(),
                Clock.systemUTC());
        Logger logger = (Logger) LoggerFactory.getLogger(FcmPushSender.class);
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        logger.addAppender(log);
        try {
            Result result = sender.send(
                    "not-a-real-device", new PushMessage("Freezify", "Prueba", Map.of("type", "EXPIRATION")), true);

            // Wrong credentials would be 401 or 403, and a disabled API 403: a 400 about the argument means
            // Google issued an access token and Firebase Cloud Messaging accepted it for this project.
            assertThat(result).isEqualTo(Result.FAILED);
            assertThat(log.list).extracting(ILoggingEvent::getFormattedMessage)
                    .containsExactly("Firebase rejected a push message: HTTP 400 INVALID_ARGUMENT");
        } finally {
            logger.detachAppender(log);
        }
    }
}
