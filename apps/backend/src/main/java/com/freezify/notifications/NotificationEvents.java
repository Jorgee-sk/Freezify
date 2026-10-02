package com.freezify.notifications;

import java.util.UUID;

/** Published after something happens to a notification, for modules that react to it (statistics). */
public final class NotificationEvents {

    private NotificationEvents() {}

    /** The user opened the notification for the first time. */
    public record NotificationOpened(UUID userId, UUID householdId, UUID notificationId) {}
}
