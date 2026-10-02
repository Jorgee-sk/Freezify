package com.freezify.notifications.internal;

import com.freezify.common.ApiException;
import com.freezify.common.PageResponse;
import com.freezify.households.HouseholdDirectory;
import com.freezify.households.HouseholdEvents.MemberRemoved;
import com.freezify.notifications.NotificationEvents.NotificationOpened;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What a user can do with their own notifications. Every query is scoped to the user who asks. */
@Service
public class NotificationService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final NotificationRepository notifications;
    private final NotificationPreferencesRepository preferences;
    private final ItemAlertRepository alerts;
    private final HouseholdDirectory households;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    NotificationService(
            NotificationRepository notifications,
            NotificationPreferencesRepository preferences,
            ItemAlertRepository alerts,
            HouseholdDirectory households,
            ApplicationEventPublisher events,
            Clock clock) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.alerts = alerts;
        this.households = households;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationView> list(UUID userId, int page, int size) {
        Page<NotificationEntity> found = notifications.findByUserId(userId, PageRequest.of(page, size, NEWEST_FIRST));
        Map<UUID, String> names = households.names(
                found.stream().map(NotificationEntity::householdId).distinct().toList());
        return PageResponse.of(
                found.map(notification -> notification.toView(names.getOrDefault(notification.householdId(), ""))));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    /** A notification of another user is reported exactly like one that does not exist. */
    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        NotificationEntity notification = notifications
                .findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("NOTIFICATION_NOT_FOUND", "Notification not found."));
        if (notification.markRead(clock.instant())) {
            events.publishEvent(new NotificationOpened(userId, notification.householdId(), notificationId));
        }
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notifications.markAllRead(userId, clock.instant());
    }

    @Transactional(readOnly = true)
    public Preferences preferences(UUID userId) {
        return preferences
                .findById(userId)
                .map(NotificationPreferencesEntity::toPreferences)
                .orElseGet(Preferences::defaults);
    }

    @Transactional
    public Preferences updatePreferences(UUID userId, Preferences updated) {
        NotificationPreferencesEntity entity =
                preferences.findById(userId).orElseGet(() -> new NotificationPreferencesEntity(userId));
        return preferences.save(entity.apply(updated, clock.instant())).toPreferences();
    }

    /**
     * Someone who is no longer a member must not keep reading what was in the household's fridge. Runs inside
     * the transaction that removes the member, so the two cannot come apart.
     */
    @EventListener
    void on(MemberRemoved event) {
        notifications.deleteAll(notifications.findByUserIdAndHouseholdId(event.userId(), event.householdId()));
        alerts.deleteAll(alerts.findByUserIdAndHouseholdId(event.userId(), event.householdId()));
    }
}
