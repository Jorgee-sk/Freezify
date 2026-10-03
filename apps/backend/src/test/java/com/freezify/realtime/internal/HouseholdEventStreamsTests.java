package com.freezify.realtime.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HouseholdEventStreamsTests {

    private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");

    private final HouseholdEventStreams streams =
            new HouseholdEventStreams(Duration.ofMinutes(30), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aConnectionEndsWhenItsTokenExpires() {
        assertThat(streams.lifetime(NOW.plus(Duration.ofMinutes(4)))).isEqualTo(Duration.ofMinutes(4));
        assertThat(streams.open(UUID.randomUUID(), UUID.randomUUID(), NOW.plus(Duration.ofMinutes(4))).getTimeout())
                .isEqualTo(Duration.ofMinutes(4).toMillis());
    }

    @Test
    void aConnectionNeverOutlivesTheLongestAllowed() {
        assertThat(streams.lifetime(NOW.plus(Duration.ofHours(2)))).isEqualTo(Duration.ofMinutes(30));
        assertThat(streams.lifetime(null)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void aTokenAboutToExpireStillGetsAMoment() {
        assertThat(streams.lifetime(NOW.plusMillis(10))).isEqualTo(Duration.ofSeconds(1));
        assertThat(streams.lifetime(NOW.minusSeconds(5))).isEqualTo(Duration.ofSeconds(1));
    }
}
