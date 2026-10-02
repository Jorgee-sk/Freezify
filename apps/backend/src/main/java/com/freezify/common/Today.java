package com.freezify.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The current calendar day as users see it. Purchase and expiration dates are days, not instants, so "today"
 * needs a time zone; a single one is configured until households carry their own.
 */
@Component
public class Today {

    private final Clock clock;
    private final ZoneId zone;

    public Today(Clock clock, @Value("${freezify.time-zone}") ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    public LocalDate date() {
        return now().toLocalDate();
    }

    /** The current moment on the clock users look at. */
    public ZonedDateTime now() {
        return clock.instant().atZone(zone);
    }
}
