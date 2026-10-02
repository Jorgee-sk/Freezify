import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/errors.dart';
import '../../l10n/app_localizations.dart';
import '../auth/auth_controller.dart';
import '../notifications/notification_bell.dart';
import '../notifications/notification_repository.dart';
import 'dialogs.dart';
import 'households_repository.dart';

class HouseholdsScreen extends ConsumerWidget {
  const HouseholdsScreen({super.key});

  Future<void> _create(BuildContext context, WidgetRef ref) async {
    final l10n = AppLocalizations.of(context);
    final name = await promptText(
      context,
      title: l10n.createHousehold,
      label: l10n.householdName,
      confirmLabel: l10n.create,
    );
    if (name == null || !context.mounted) return;
    await _open(context, ref, () => ref.read(householdsRepositoryProvider).create(name));
  }

  Future<void> _join(BuildContext context, WidgetRef ref) async {
    final l10n = AppLocalizations.of(context);
    final code = await promptText(
      context,
      title: l10n.joinHousehold,
      label: l10n.invitationCode,
      confirmLabel: l10n.join,
      maxLength: 32,
      capitalization: TextCapitalization.characters,
    );
    if (code == null || !context.mounted) return;
    await _open(context, ref, () => ref.read(householdsRepositoryProvider).join(code));
  }

  Future<void> _open(BuildContext context, WidgetRef ref, Future<Household> Function() action) async {
    try {
      final household = await action();
      ref.invalidate(householdsProvider);
      if (context.mounted) context.go('/households/${household.id}');
    } catch (error) {
      if (context.mounted) showError(context, error);
    }
  }

  Future<void> _changeLocale(BuildContext context, WidgetRef ref, String locale) async {
    try {
      await ref.read(authControllerProvider.notifier).changeLocale(locale);
    } catch (error) {
      if (context.mounted) showError(context, error);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final user = ref.watch(authControllerProvider).value;
    final households = ref.watch(householdsProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.appName),
        actions: [
          const NotificationBell(),
          PopupMenuButton<String>(
            onSelected: (value) => switch (value) {
              'logout' => ref.read(authControllerProvider.notifier).logout(),
              _ => _changeLocale(context, ref, value),
            },
            itemBuilder: (context) => [
              PopupMenuItem(enabled: false, child: Text(l10n.language)),
              for (final locale in AppLocalizations.supportedLocales)
                CheckedPopupMenuItem(
                  value: locale.languageCode,
                  checked: user?.locale == locale.languageCode,
                  child: Text(locale.languageCode.toUpperCase()),
                ),
              const PopupMenuDivider(),
              PopupMenuItem(value: 'logout', child: Text(l10n.logout)),
            ],
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () {
          ref.invalidate(unreadNotificationsProvider);
          return ref.refresh(householdsProvider.future);
        },
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            if (user != null) Text(l10n.greeting(user.displayName), style: theme.textTheme.headlineSmall),
            const SizedBox(height: 16),
            Text(l10n.householdsTitle, style: theme.textTheme.titleMedium),
            const SizedBox(height: 8),
            ...households.when(
              loading: () => const [Center(child: Padding(padding: EdgeInsets.all(24), child: CircularProgressIndicator()))],
              error: (error, _) => [
                Text(errorMessage(l10n, error), style: TextStyle(color: theme.colorScheme.error)),
                Align(
                  alignment: Alignment.centerLeft,
                  child: TextButton(onPressed: () => ref.invalidate(householdsProvider), child: Text(l10n.retry)),
                ),
              ],
              data: (items) => items.isEmpty
                  ? [
                      Card(
                        child: Padding(padding: const EdgeInsets.all(16), child: Text(l10n.householdsEmpty)),
                      ),
                    ]
                  : [
                      for (final household in items) ...[
                        _HouseholdCard(household: household),
                        const SizedBox(height: 8),
                      ],
                    ],
            ),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: () => _create(context, ref),
              icon: const Icon(Icons.add_home_outlined),
              label: Text(l10n.createHousehold),
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: () => _join(context, ref),
              icon: const Icon(Icons.group_add_outlined),
              label: Text(l10n.joinHousehold),
            ),
          ],
        ),
      ),
    );
  }
}

class _HouseholdCard extends StatelessWidget {
  const _HouseholdCard({required this.household});

  final Household household;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Card(
      clipBehavior: Clip.antiAlias,
      child: ListTile(
        leading: const Icon(Icons.home_outlined),
        title: Text(household.name, style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Text(l10n.memberCount(household.memberCount)),
        trailing: Chip(label: Text(household.isOwner ? l10n.roleOwner : l10n.roleMember)),
        onTap: () => context.go('/households/${household.id}'),
      ),
    );
  }
}
