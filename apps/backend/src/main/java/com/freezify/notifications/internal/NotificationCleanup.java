package com.freezify.notifications.internal;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes notifications once they are old enough to have stopped mattering: what they said about food is long
 * out of date. At most one is created per user, household and day, so without this they would pile up forever.
 */
@Component
class NotificationCleanup {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanup.class);

    private final NotificationRepository notifications;
    private final Duration retention;
    private final Clock clock;

    NotificationCleanup(
            NotificationRepository notifications,
            @Value("${freezify.notifications.retention}") Duration retention,
            Clock clock) {
        this.notifications = notifications;
        this.retention = retention;
        this.clock = clock;
    }

    @Scheduled(cron = "${freezify.notifications.cleanup-cron}", zone = "${freezify.time-zone}")
    @Transactional
    public void deleteOld() {
        int deleted = notifications.deleteCreatedBefore(clock.instant().minus(retention));
        log.info("Deleted {} notification(s) older than {}", deleted, retention);
    }
}
