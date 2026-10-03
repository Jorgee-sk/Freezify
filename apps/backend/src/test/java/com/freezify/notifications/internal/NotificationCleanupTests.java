package com.freezify.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.freezify.testsupport.ApiTestSupport;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class NotificationCleanupTests extends ApiTestSupport {

    @Autowired
    private NotificationCleanup cleanup;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void notificationsOlderThanTheRetentionAreDeletedWithTheirItems() throws Exception {
        TestUser jorge = register("Jorge");
        String householdId = createHousehold(jorge, "Casa");
        UUID old = notification(jorge, householdId, Duration.ofDays(91));
        UUID recent = notification(jorge, householdId, Duration.ofDays(89));

        cleanup.deleteOld();

        assertThat(count("select count(*) from notifications where id = ?", old)).isZero();
        assertThat(count("select count(*) from notification_items where notification_id = ?", old)).isZero();
        assertThat(count("select count(*) from notifications where id = ?", recent)).isEqualTo(1);
        assertThat(count("select count(*) from notification_items where notification_id = ?", recent)).isEqualTo(1);
    }

    @Test
    void readOrNotMakesNoDifference() throws Exception {
        TestUser jorge = register("Jorge");
        String householdId = createHousehold(jorge, "Casa");
        UUID old = notification(jorge, householdId, Duration.ofDays(120));
        jdbc.update("update notifications set read_at = now() where id = ?", old);

        cleanup.deleteOld();

        assertThat(count("select count(*) from notifications where id = ?", old)).isZero();
    }

    private UUID notification(TestUser user, String householdId, Duration age) {
        UUID id = UUID.randomUUID();
        Instant created = clock.instant().minus(age);
        LocalDate day = LocalDate.ofInstant(created, clock.getZone());
        jdbc.update(
                "insert into notifications (id, user_id, household_id, type, day, item_count, created_at)"
                        + " values (?, ?::uuid, ?::uuid, 'EXPIRATION', ?, 1, ?)",
                id,
                user.id(),
                householdId,
                day,
                Timestamp.from(created));
        jdbc.update(
                "insert into notification_items (notification_id, position, name, expiration_date, estimated)"
                        + " values (?, 0, 'Leche', ?, false)",
                id,
                day);
        return id;
    }

    private long count(String sql, UUID id) {
        return jdbc.queryForObject(sql, Long.class, id);
    }
}
