package com.freezify.notifications.internal;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * @param fcmCredentialsFile path of the Firebase service account key; when blank nothing is sent
 * @param fcmEndpoint        where Firebase Cloud Messaging listens; only tests change it
 */
@ConfigurationProperties("freezify.push")
record PushProperties(String fcmCredentialsFile, URI fcmEndpoint) {}

@Configuration(proxyBeanMethods = false)
class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    /**
     * Push is optional: the application works without it, and notifications are then only seen inside the app.
     * A key that is configured but cannot be read stops the startup, so that a broken deployment is noticed.
     */
    @Bean
    PushSender pushSender(PushProperties properties, JsonMapper jsonMapper, Clock clock) {
        if (properties.fcmCredentialsFile() == null || properties.fcmCredentialsFile().isBlank()) {
            log.info("Push notifications are disabled: no Firebase credentials configured");
            return new DisabledPushSender();
        }
        FcmPushSender sender = FcmPushSender.fromCredentialsFile(
                Path.of(properties.fcmCredentialsFile()), properties.fcmEndpoint(), jsonMapper, clock);
        log.info("Push notifications are sent through Firebase project {}", sender.projectId());
        return sender;
    }
}
