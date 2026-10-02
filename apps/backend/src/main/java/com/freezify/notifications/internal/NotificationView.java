package com.freezify.notifications.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A notification as clients receive it. It carries facts, not sentences: clients word it in the user's language.
 *
 * @param day       the calendar day the notification is about
 * @param itemCount how many items needed attention that day; {@code items} holds only the most pressing ones
 */
public record NotificationView(
        UUID id,
        NotificationType type,
        UUID householdId,
        String householdName,
        LocalDate day,
        int itemCount,
        List<Item> items,
        Instant createdAt,
        boolean read) {

    /**
     * @param estimated           whether the date is an estimate rather than the one the user gave
     * @param daysUntilExpiration counted from {@code day}; negative when the date had passed
     */
    public record Item(String name, LocalDate expirationDate, boolean estimated, long daysUntilExpiration) {}
}
