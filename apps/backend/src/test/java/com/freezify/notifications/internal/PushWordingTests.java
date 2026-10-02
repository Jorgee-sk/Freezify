package com.freezify.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PushWordingTests {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 2);

    @Test
    void namesASingleFoodAndSaysHowLongIsLeft() {
        assertThat(body("es", food(3, false))).isEqualTo("Leche caduca en 3 días");
        assertThat(body("es", food(1, false))).isEqualTo("Leche caduca en 1 día");
        assertThat(body("es", food(0, false))).isEqualTo("Leche caduca hoy");
        assertThat(body("es", food(-1, false))).isEqualTo("Leche caducó hace 1 día");
        assertThat(body("es", food(-4, false))).isEqualTo("Leche caducó hace 4 días");
        assertThat(body("en", food(3, false))).isEqualTo("Leche expires in 3 days");
        assertThat(body("en", food(0, false))).isEqualTo("Leche expires today");
        assertThat(body("en", food(-1, false))).isEqualTo("Leche expired 1 day ago");
    }

    @Test
    void neverPresentsAnEstimateAsAFact() {
        for (int days : new int[] {3, 1, 0, -1, -4}) {
            assertThat(body("es", food(days, true))).endsWith("(fecha estimada)");
            assertThat(body("en", food(days, true))).endsWith("(estimated date)");
        }
        assertThat(body("es", food(1, true))).isEqualTo("Leche caduca en aproximadamente 1 día (fecha estimada)");
        assertThat(body("es", food(0, true))).isEqualTo("Leche probablemente caduca hoy (fecha estimada)");
        assertThat(body("en", food(-2, true))).isEqualTo("Leche probably expired 2 days ago (estimated date)");
    }

    @Test
    void countsSeveralFoodsIncludingThoseNotListed() {
        NotificationView several = notification(7, "Casa", food(0, false), food(1, true));

        assertThat(PushWording.message(several, "es").body())
                .isEqualTo("Tienes 7 alimentos que deberías consumir pronto");
        assertThat(PushWording.message(several, "en").body()).isEqualTo("You have 7 foods you should eat soon");
    }

    @Test
    void speaksEnglishToLanguagesItDoesNotKnow() {
        assertThat(body("fr", food(0, false))).isEqualTo("Leche expires today");
    }

    @Test
    void isTitledWithTheHouseholdAndSaysWhereToGo() {
        NotificationView notification = notification(1, "Casa", food(1, false));

        assertThat(PushWording.message(notification, "es").title()).isEqualTo("Casa");
        assertThat(PushWording.message(notification, "es").data())
                .containsEntry("type", "EXPIRATION")
                .containsEntry("notificationId", notification.id().toString())
                .containsEntry("householdId", notification.householdId().toString());
        assertThat(PushWording.message(notification(1, "", food(1, false)), "es").title())
                .isEqualTo("Freezify");
    }

    private static String body(String language, NotificationView.Item item) {
        return PushWording.message(notification(1, "Casa", item), language).body();
    }

    private static NotificationView.Item food(int days, boolean estimated) {
        return new NotificationView.Item("Leche", DAY.plusDays(days), estimated, days);
    }

    private static NotificationView notification(int itemCount, String householdName, NotificationView.Item... items) {
        return new NotificationView(
                UUID.randomUUID(),
                NotificationType.EXPIRATION,
                UUID.randomUUID(),
                householdName,
                DAY,
                itemCount,
                List.of(items),
                Instant.parse("2026-10-02T07:00:00Z"),
                false);
    }
}
