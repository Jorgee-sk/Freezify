import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/errors.dart';
import '../../l10n/app_localizations.dart';
import '../households/dialogs.dart';
import '../inventory/inventory_format.dart';
import 'notification_models.dart';
import 'notification_repository.dart';
import 'notification_wording.dart';

class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key});

  void _reload(WidgetRef ref) {
    ref.invalidate(notificationsProvider);
    ref.invalidate(unreadNotificationsProvider);
  }

  /// Opening a notification leads to the inventory it talks about, which is where something can be done.
  Future<void> _open(BuildContext context, WidgetRef ref, AppNotification notification) async {
    if (!notification.read) {
      try {
        await ref.read(notificationRepositoryProvider).markRead(notification.id);
      } catch (_) {
        // Not being able to mark it must not keep the user from their inventory; it stays unread.
      }
      _reload(ref);
    }
    if (context.mounted) context.go('/households/${notification.householdId}');
  }

  Future<void> _markAllRead(BuildContext context, WidgetRef ref) async {
    try {
      await ref.read(notificationRepositoryProvider).markAllRead();
      _reload(ref);
    } catch (error) {
      if (context.mounted) showError(context, error);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final notifications = ref.watch(notificationsProvider);
    final anyUnread = notifications.value?.any((notification) => !notification.read) ?? false;

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.notificationsTitle),
        actions: [
          if (anyUnread)
            IconButton(
              tooltip: l10n.markAllRead,
              onPressed: () => _markAllRead(context, ref),
              icon: const Icon(Icons.done_all),
            ),
          IconButton(
            tooltip: l10n.notificationPreferences,
            onPressed: () => context.go('/notifications/preferences'),
            icon: const Icon(Icons.tune),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () {
          ref.invalidate(unreadNotificationsProvider);
          return ref.refresh(notificationsProvider.future);
        },
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: notifications.when(
            loading: () => const [
              Center(
                child: Padding(padding: EdgeInsets.all(24), child: CircularProgressIndicator()),
              ),
            ],
            error: (error, _) => [
              Text(errorMessage(l10n, error), style: TextStyle(color: theme.colorScheme.error)),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton(onPressed: () => _reload(ref), child: Text(l10n.retry)),
              ),
            ],
            data: (items) => items.isEmpty
                ? [
                    Card(
                      child: Padding(padding: const EdgeInsets.all(16), child: Text(l10n.notificationsEmpty)),
                    ),
                  ]
                : [
                    for (final notification in items) ...[
                      _NotificationCard(notification: notification, onTap: () => _open(context, ref, notification)),
                      const SizedBox(height: 8),
                    ],
                  ],
          ),
        ),
      ),
    );
  }
}

class _NotificationCard extends StatelessWidget {
  const _NotificationCard({required this.notification, required this.onTap});

  final AppNotification notification;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant);
    final hidden = notification.itemCount - notification.items.length;

    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Text(
                      notificationTitle(l10n, notification),
                      style: theme.textTheme.titleSmall?.copyWith(
                        fontWeight: notification.read ? FontWeight.w500 : FontWeight.w700,
                      ),
                    ),
                  ),
                  if (!notification.read) ...[
                    const SizedBox(width: 8),
                    Badge(label: Text(l10n.notificationNew), backgroundColor: theme.colorScheme.primary),
                  ],
                ],
              ),
              if (notification.itemCount > 1) ...[
                const SizedBox(height: 8),
                for (final item in notification.items) Text('• ${itemSentence(l10n, item)}'),
                if (hidden > 0) Text(l10n.andMore(hidden), style: muted),
              ],
              const SizedBox(height: 8),
              Text('${notification.householdName} · ${formatDay(notification.day)}', style: muted),
            ],
          ),
        ),
      ),
    );
  }
}
