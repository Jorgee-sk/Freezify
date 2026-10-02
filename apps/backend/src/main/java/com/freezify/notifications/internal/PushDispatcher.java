package com.freezify.notifications.internal;

import com.freezify.households.HouseholdDirectory;
import com.freezify.notifications.internal.PushSender.PushMessage;
import com.freezify.users.UserAccount;
import com.freezify.users.Users;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/** A notification was stored for a user. */
record NotificationCreated(UUID userId, UUID notificationId) {}

/**
 * Sends each new notification to the phones of its user. It runs after the notification is committed, so a
 * push never announces something that is not there, and a failed push never undoes a notification: the user
 * still finds it inside the app.
 */
@Component
class PushDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PushDispatcher.class);

    private final PushSender sender;
    private final Devices devices;
    private final NotificationRepository notifications;
    private final HouseholdDirectory households;
    private final Users users;
    private final TransactionTemplate readOnly;

    PushDispatcher(
            PushSender sender,
            Devices devices,
            NotificationRepository notifications,
            HouseholdDirectory households,
            Users users,
            PlatformTransactionManager transactionManager) {
        this.sender = sender;
        this.devices = devices;
        this.notifications = notifications;
        this.households = households;
        this.users = users;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        // The transaction that stored the notification is over; this one only reads it back.
        this.readOnly.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener
    void on(NotificationCreated event) {
        if (!sender.enabled()) {
            return;
        }
        try {
            List<String> tokens = devices.tokensOf(event.userId());
            if (tokens.isEmpty()) {
                return;
            }
            PushMessage message = readOnly.execute(status -> message(event));
            if (message == null) {
                return;
            }
            for (String token : tokens) {
                if (sender.send(token, message) == PushSender.Result.UNREGISTERED) {
                    devices.forget(token);
                }
            }
        } catch (RuntimeException e) {
            log.error("Could not push notification {}", event.notificationId(), e);
        }
    }

    private PushMessage message(NotificationCreated event) {
        NotificationEntity notification =
                notifications.findByIdAndUserId(event.notificationId(), event.userId()).orElse(null);
        if (notification == null) {
            return null;
        }
        String householdName =
                households.names(List.of(notification.householdId())).getOrDefault(notification.householdId(), "");
        String language = users.findById(event.userId()).map(UserAccount::locale).orElse("en");
        return PushWording.message(notification.toView(householdName), language);
    }
}
