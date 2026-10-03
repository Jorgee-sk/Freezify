package com.freezify.notifications.internal;

import com.freezify.notifications.internal.PushSender.PushMessage;
import java.util.Map;

/**
 * Words a notification for the lock screen, where the app is not running to do it. Inside the app the clients
 * word notifications themselves; the sentences here say the same in Spanish and English.
 *
 * <p>An estimated date is always worded as an estimate: it is the application's guess, not what the package says.
 */
final class PushWording {

    private PushWording() {}

    /**
     * @param language "es" for Spanish; anything else gets English
     */
    static PushMessage message(NotificationView notification, String language) {
        boolean spanish = "es".equals(language);
        NotificationView.Item first = notification.items().isEmpty() ? null : notification.items().get(0);
        String body = notification.itemCount() == 1 && first != null
                ? item(first, spanish)
                : summary(notification.itemCount(), spanish);
        return new PushMessage(
                notification.householdName().isBlank() ? "Freezify" : notification.householdName(),
                body,
                Map.of(
                        "type", notification.type().name(),
                        "notificationId", notification.id().toString(),
                        "householdId", notification.householdId().toString()));
    }

    private static String summary(int count, boolean spanish) {
        if (spanish) {
            return count == 1
                    ? "Tienes 1 alimento que deberías consumir pronto"
                    : "Tienes " + count + " alimentos que deberías consumir pronto";
        }
        return count == 1 ? "You have 1 food you should eat soon" : "You have " + count + " foods you should eat soon";
    }

    /**
     * Food names can be plural ("Huevos"), so no sentence makes a verb agree with the name: the name is followed
     * by a colon and the sentence is about its date.
     */
    private static String item(NotificationView.Item item, boolean spanish) {
        long days = item.daysUntilExpiration();
        boolean estimated = item.estimated();
        String sentence;
        if (spanish) {
            if (days < 0) {
                sentence = (estimated ? "su fecha de caducidad probablemente pasó hace " : "su fecha de caducidad pasó hace ")
                        + days(-days, true);
            } else if (days == 0) {
                sentence = estimated ? "su fecha de caducidad es probablemente hoy" : "su fecha de caducidad es hoy";
            } else {
                sentence = (days == 1 ? "queda " : "quedan ")
                        + (estimated ? "aproximadamente " : "")
                        + days(days, true)
                        + " para su fecha de caducidad";
            }
            return item.name() + ": " + sentence + (estimated ? " (fecha estimada)" : "");
        }
        if (days < 0) {
            sentence = (estimated ? "the expiry date was probably " : "the expiry date was ") + days(-days, false) + " ago";
        } else if (days == 0) {
            sentence = estimated ? "the expiry date is probably today" : "the expiry date is today";
        } else {
            sentence = (estimated ? "about " : "") + days(days, false) + " left before the expiry date";
        }
        return item.name() + ": " + sentence + (estimated ? " (estimated date)" : "");
    }

    private static String days(long count, boolean spanish) {
        if (spanish) {
            return count == 1 ? "1 día" : count + " días";
        }
        return count == 1 ? "1 day" : count + " days";
    }
}
