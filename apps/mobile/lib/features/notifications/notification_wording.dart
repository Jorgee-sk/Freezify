import '../../l10n/app_localizations.dart';
import 'notification_models.dart';

/// "Leche: quedan 2 días para su fecha de caducidad". Sentences do not depend on the number of the name
/// ("Huevos"). An estimated date is always worded as an estimate: it is the app's guess, not what the package
/// says.
String itemSentence(AppLocalizations l10n, NotifiedItem item) {
  final days = item.daysUntilExpiration;
  if (days < 0) {
    return item.estimated ? l10n.notifiedExpiredEstimated(item.name, -days) : l10n.notifiedExpired(item.name, -days);
  }
  if (days == 0) {
    return item.estimated ? l10n.notifiedTodayEstimated(item.name) : l10n.notifiedToday(item.name);
  }
  return item.estimated ? l10n.notifiedLeftEstimated(item.name, days) : l10n.notifiedLeft(item.name, days);
}

/// A single food is named; several are counted.
String notificationTitle(AppLocalizations l10n, AppNotification notification) =>
    notification.itemCount == 1 && notification.items.isNotEmpty
    ? itemSentence(l10n, notification.items.first)
    : l10n.notificationSummary(notification.itemCount);
