package com.freezify.notifications.internal;

import com.freezify.common.Today;
import com.freezify.expiration.ExpirationPriority;
import com.freezify.households.HouseholdDirectory;
import com.freezify.inventory.ExpiringFood;
import com.freezify.inventory.ExpiringFood.ExpiringItem;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tells each member, once a day and not before the hour they chose, which food of their households is running
 * out of time.
 *
 * <p>The rules that keep this from becoming spam:
 * <ul>
 *   <li>one notification per household and day at most, with everything in it;
 *   <li>only when there is something new to say: a food the user has not been told about, or one that has become
 *       more pressing since (2 days left, then today, then expired). Food that just sits there expired is not
 *       brought up again on its own;
 *   <li>never more often than the user asked for, nor about categories they muted.
 * </ul>
 *
 * <p>Runs every hour, and once at startup in case the application was down at somebody's hour.
 */
@Component
class ExpirationAlerts {

    private static final Logger log = LoggerFactory.getLogger(ExpirationAlerts.class);

    /** How many items a notification lists; the rest are only counted. */
    static final int LISTED_ITEMS = 5;

    private static final Comparator<ExpiringItem> MOST_PRESSING_FIRST =
            Comparator.comparing(ExpiringItem::expirationDate).thenComparing(ExpiringItem::name);

    private final ExpiringFood expiringFood;
    private final HouseholdDirectory households;
    private final NotificationRepository notifications;
    private final NotificationPreferencesRepository preferences;
    private final ItemAlertRepository alerts;
    private final NotificationCheckRepository checks;
    private final TransactionTemplate transaction;
    private final Today today;
    private final Clock clock;

    ExpirationAlerts(
            ExpiringFood expiringFood,
            HouseholdDirectory households,
            NotificationRepository notifications,
            NotificationPreferencesRepository preferences,
            ItemAlertRepository alerts,
            NotificationCheckRepository checks,
            PlatformTransactionManager transactionManager,
            Today today,
            Clock clock) {
        this.expiringFood = expiringFood;
        this.households = households;
        this.notifications = notifications;
        this.preferences = preferences;
        this.alerts = alerts;
        this.checks = checks;
        this.transaction = new TransactionTemplate(transactionManager);
        this.today = today;
        this.clock = clock;
    }

    @Scheduled(cron = "${freezify.notifications.check-cron}", zone = "${freezify.time-zone}")
    @EventListener(ApplicationReadyEvent.class)
    void run() {
        try {
            int sent = sendDue();
            log.info("Expiration alerts: {} notification(s) created", sent);
        } catch (RuntimeException e) {
            // A failed run must be visible, and must not stop the scheduler or the startup.
            log.error("Expiration alerts failed", e);
        }
    }

    /**
     * @return how many notifications were created
     */
    int sendDue() {
        ZonedDateTime now = today.now();
        LocalDate day = now.toLocalDate();
        Map<UUID, List<ExpiringItem>> byHousehold = expiringFood.until(Preferences.Threshold.widestHorizon(day)).stream()
                .collect(Collectors.groupingBy(ExpiringItem::householdId));

        Map<UUID, List<UUID>> householdsByUser = new HashMap<>();
        for (UUID householdId : byHousehold.keySet()) {
            for (UUID userId : households.memberIds(householdId)) {
                householdsByUser.computeIfAbsent(userId, id -> new ArrayList<>()).add(householdId);
            }
        }

        int sent = 0;
        for (Map.Entry<UUID, List<UUID>> entry : householdsByUser.entrySet()) {
            UUID userId = entry.getKey();
            try {
                // One transaction per user: a failure for one of them must not cost the others their notification.
                Integer created = transaction.execute(
                        status -> check(userId, entry.getValue(), byHousehold, day, now.getHour()));
                sent += created == null ? 0 : created;
            } catch (RuntimeException e) {
                log.error("Expiration alerts failed for user {}", userId, e);
            }
        }
        return sent;
    }

    private int check(
            UUID userId, List<UUID> householdIds, Map<UUID, List<ExpiringItem>> byHousehold, LocalDate day, int hour) {
        Preferences wanted = preferences
                .findById(userId)
                .map(NotificationPreferencesEntity::toPreferences)
                .orElseGet(Preferences::defaults);
        if (!wanted.expirationAlerts() || hour < wanted.deliveryHour()) {
            return 0;
        }
        NotificationCheckEntity check = checks.findById(userId).orElseGet(() -> new NotificationCheckEntity(userId));
        if (check.doneOn(day)) {
            return 0;
        }
        checks.save(check.on(day));

        int created = 0;
        for (UUID householdId : householdIds) {
            if (alert(userId, householdId, byHousehold.get(householdId), wanted, day)) {
                created++;
            }
        }
        return created;
    }

    private boolean alert(UUID userId, UUID householdId, List<ExpiringItem> items, Preferences wanted, LocalDate day) {
        boolean toldTooRecently = notifications
                .findFirstByUserIdAndHouseholdIdAndTypeOrderByDayDesc(userId, householdId, NotificationType.EXPIRATION)
                .filter(last -> ChronoUnit.DAYS.between(last.day(), day) < wanted.frequency().days())
                .isPresent();
        if (toldTooRecently) {
            // What the user has been told stays as it is, so nothing is lost: it will be new when their turn comes.
            return false;
        }

        List<ExpiringItem> relevant = items.stream()
                .filter(item -> !wanted.mutedCategories().contains(item.category()))
                .filter(item -> wanted.threshold().includes(item.expirationDate(), day))
                .sorted(MOST_PRESSING_FIRST)
                .toList();

        Map<UUID, ItemAlertEntity> told = alerts.findByUserIdAndHouseholdId(userId, householdId).stream()
                .collect(Collectors.toMap(ItemAlertEntity::itemId, Function.identity()));
        boolean somethingNew = false;
        for (ExpiringItem item : relevant) {
            ExpirationPriority priority = ExpirationPriority.of(item.expirationDate(), day);
            ItemAlertEntity alert = told.remove(item.itemId());
            if (alert == null) {
                alerts.save(new ItemAlertEntity(userId, householdId, item.itemId(), priority));
                somethingNew = true;
            } else if (alert.moveTo(priority)) {
                somethingNew = true;
            }
        }
        // Food that is no longer worth a notification (eaten, date corrected) starts from scratch if it comes back.
        alerts.deleteAll(told.values());

        if (!somethingNew) {
            return false;
        }
        notifications.save(new NotificationEntity(
                userId,
                householdId,
                NotificationType.EXPIRATION,
                day,
                relevant.size(),
                relevant.stream()
                        .limit(LISTED_ITEMS)
                        .map(item -> new NotifiedItem(item.name(), item.expirationDate(), item.estimated()))
                        .toList(),
                clock.instant()));
        return true;
    }
}
