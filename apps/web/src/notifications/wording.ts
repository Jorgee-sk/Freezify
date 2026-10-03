import type { AppNotification, NotifiedItem } from '../api/notifications'
import i18n from '../i18n'

/**
 * "Leche: quedan 2 días para su fecha de caducidad". Sentences do not depend on the number of the name
 * ("Huevos"). An estimated date is always worded as an estimate: it is the app's guess, not what the package says.
 */
export function itemSentence(item: NotifiedItem): string {
  const days = item.daysUntilExpiration
  const when = days < 0 ? 'expired' : days === 0 ? 'today' : 'left'
  const key = `notifications.item.${when}${item.estimated ? 'Estimated' : ''}`
  return i18n.t(key, { name: item.name, count: Math.abs(days) })
}

/** A single food is named; several are counted. */
export function notificationTitle(notification: AppNotification): string {
  const [only] = notification.items
  return notification.itemCount === 1 && only
    ? itemSentence(only)
    : i18n.t('notifications.summary', { count: notification.itemCount })
}
