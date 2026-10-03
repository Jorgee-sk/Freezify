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
        assertThat(body("es", food(3, false))).isEqualTo("Leche: quedan 3 días para su fecha de caducidad");
        assertThat(body("es", food(1, false))).isEqualTo("Leche: queda 1 día para su fecha de caducidad");
        assertThat(body("es", food(0, false))).isEqualTo("Leche: su fecha de caducidad es hoy");
        assertThat(body("es", food(-1, false))).isEqualTo("Leche: su fecha de caducidad pasó hace 1 día");
        assertThat(body("es", food(-4, false))).isEqualTo("Leche: su fecha de caducidad pasó hace 4 días");
        assertThat(body("en", food(3, false))).isEqualTo("Leche: 3 days left before the expiry date");
        assertThat(body("en", food(0, false))).isEqualTo("Leche: the expiry date is today");
        assertThat(body("en", food(-1, false))).isEqualTo("Leche: the expiry date was 1 day ago");
    }

    @Test
    void readsRightWhateverTheNumberOfTheName() {
        NotificationView.Item eggs = new NotificationView.Item("Huevos", DAY, false, 0);

        // Not "Huevos caduca hoy": no verb agrees with the name.
        assertThat(body("es", eggs)).isEqualTo("Huevos: su fecha de caducidad es hoy");
        assertThat(body("en", eggs)).isEqualTo("Huevos: the expiry date is today");
    }

    @Test
    void neverPresentsAnEstimateAsAFact() {
        for (int days : new int[] {3, 1, 0, -1, -4}) {
            assertThat(body("es", food(days, true))).endsWith("(fecha estimada)");
            assertThat(body("en", food(days, true))).endsWith("(estimated date)");
        }
        assertThat(body("es", food(1, true))).isEqualTo("Leche: queda aproximadamente 1 día para su fecha de caducidad (fecha estimada)");
        assertThat(body("es", food(0, true))).isEqualTo("Leche: su fecha de caducidad es probablemente hoy (fecha estimada)");
        assertThat(body("en", food(-2, true))).isEqualTo("Leche: the expiry date was probably 2 days ago (estimated date)");
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
        assertThat(body("fr", food(0, false))).isEqualTo("Leche: the expiry date is today");
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
