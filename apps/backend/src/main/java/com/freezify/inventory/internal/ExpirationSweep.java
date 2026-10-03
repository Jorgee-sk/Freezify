package com.freezify.inventory.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Marks as expired the food whose date has passed. Dates only change meaning when the day changes, so this
 * runs shortly after midnight, and once at startup in case the application was down at that time. Before that it
 * estimates the date of food that has none but could have one, so that it is marked like the rest.
 *
 * <p>Running it twice, or on two instances at once, is harmless: the second run finds nothing to do.
 */
@Component
class ExpirationSweep {

    private static final Logger log = LoggerFactory.getLogger(ExpirationSweep.class);

    private final InventoryService inventory;

    ExpirationSweep(InventoryService inventory) {
        this.inventory = inventory;
    }

    @Scheduled(cron = "${freezify.expiration.sweep-cron}", zone = "${freezify.time-zone}")
    @EventListener(ApplicationReadyEvent.class)
    void run() {
        try {
            int estimated = inventory.estimateMissingDates();
            if (estimated > 0) {
                log.info("Expiration sweep: {} item(s) without a date got an estimated one", estimated);
            }
            int expired = inventory.markExpired();
            log.info("Expiration sweep: {} item(s) marked as expired", expired);
        } catch (RuntimeException e) {
            // A failed sweep must be visible, and must not stop the scheduler or the startup.
            log.error("Expiration sweep failed", e);
        }
    }
}
