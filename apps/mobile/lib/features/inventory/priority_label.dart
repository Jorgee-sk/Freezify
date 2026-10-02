import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import 'inventory_models.dart';

/// "Urgente · quedan 2 días". `null` for items without a date or with plenty of time left: a label on every
/// row would stop meaning anything.
String? priorityText(AppLocalizations l10n, InventoryItem item) {
  final priority = item.priority;
  final days = item.daysUntilExpiration;
  if (priority == null || days == null || priority == ExpirationPriority.ok) return null;

  final name = switch (priority) {
    ExpirationPriority.expired => l10n.priorityExpired,
    ExpirationPriority.today => l10n.priorityToday,
    ExpirationPriority.urgent => l10n.priorityUrgent,
    ExpirationPriority.soon => l10n.prioritySoon,
    ExpirationPriority.upcoming => l10n.priorityUpcoming,
    ExpirationPriority.ok => '',
  };
  // "Expires today" already says how long is left.
  if (days == 0) return name;
  return '$name · ${days < 0 ? l10n.daysAgo(-days) : l10n.daysLeft(days)}';
}

/// Red for what cannot wait, orange and amber as it gets less pressing.
Color priorityColor(ThemeData theme, ExpirationPriority priority) {
  final dark = theme.brightness == Brightness.dark;
  return switch (priority) {
    ExpirationPriority.expired || ExpirationPriority.today || ExpirationPriority.urgent => theme.colorScheme.error,
    ExpirationPriority.soon => dark ? Colors.orange.shade300 : Colors.orange.shade900,
    ExpirationPriority.upcoming => dark ? Colors.amber.shade300 : Colors.amber.shade900,
    ExpirationPriority.ok => theme.colorScheme.onSurfaceVariant,
  };
}

/// The priority of an item as a small coloured label; nothing when there is nothing to say.
class PriorityLabel extends StatelessWidget {
  const PriorityLabel({super.key, required this.item});

  final InventoryItem item;

  @override
  Widget build(BuildContext context) {
    final text = priorityText(AppLocalizations.of(context), item);
    if (text == null) return const SizedBox.shrink();
    final theme = Theme.of(context);
    return Text(
      text,
      style: theme.textTheme.labelMedium?.copyWith(
        color: priorityColor(theme, item.priority!),
        fontWeight: FontWeight.w600,
      ),
    );
  }
}
