import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../core/errors.dart';
import '../../l10n/app_localizations.dart';
import '../auth/auth_controller.dart';
import 'dialogs.dart';
import 'households_repository.dart';

// Day/month/year in both languages, as the product targets Spain.
final _dateFormat = DateFormat('dd/MM/yyyy');

String _formatDate(DateTime date) => _dateFormat.format(date.toLocal());

class HouseholdDetailScreen extends ConsumerStatefulWidget {
  const HouseholdDetailScreen({super.key, required this.householdId});

  final String householdId;

  @override
  ConsumerState<HouseholdDetailScreen> createState() => _HouseholdDetailScreenState();
}

class _HouseholdDetailScreenState extends ConsumerState<HouseholdDetailScreen> {
  Invitation? _invitation;
  bool _busy = false;

  String get _id => widget.householdId;
  HouseholdsRepository get _repository => ref.read(householdsRepositoryProvider);

  /// Runs [action] with the buttons disabled and reports a failure to the user.
  Future<bool> _run(Future<void> Function() action) async {
    setState(() => _busy = true);
    try {
      await action();
      return true;
    } catch (error) {
      if (mounted) showError(context, error);
      return false;
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _refresh() {
    ref.invalidate(householdProvider(_id));
    ref.invalidate(membersProvider(_id));
    ref.invalidate(householdsProvider);
  }

  void _backToList() {
    ref.invalidate(householdsProvider);
    context.go('/');
  }

  Future<void> _rename(Household household) async {
    final l10n = AppLocalizations.of(context);
    final name = await promptText(
      context,
      title: l10n.rename,
      label: l10n.householdName,
      confirmLabel: l10n.save,
      initialValue: household.name,
    );
    if (name == null || !mounted) return;
    if (await _run(() => _repository.rename(_id, name))) _refresh();
  }

  Future<void> _invite() async {
    await _run(() async {
      final invitation = await _repository.invite(_id);
      if (mounted) setState(() => _invitation = invitation);
    });
  }

  Future<void> _removeMember(Member member) async {
    final l10n = AppLocalizations.of(context);
    final confirmed = await confirmDestructive(
      context,
      message: l10n.confirmRemove(member.displayName),
      confirmLabel: l10n.remove,
    );
    if (!confirmed || !mounted) return;
    if (await _run(() => _repository.removeMember(_id, member.userId))) _refresh();
  }

  Future<void> _leave(Household household, String userId) async {
    final l10n = AppLocalizations.of(context);
    final confirmed = await confirmDestructive(
      context,
      message: l10n.confirmLeave(household.name),
      confirmLabel: l10n.leaveHousehold,
    );
    if (!confirmed || !mounted) return;
    if (await _run(() => _repository.removeMember(_id, userId)) && mounted) _backToList();
  }

  Future<void> _delete(Household household) async {
    final l10n = AppLocalizations.of(context);
    final confirmed = await confirmDestructive(
      context,
      message: l10n.confirmDelete(household.name),
      confirmLabel: l10n.deleteHousehold,
    );
    if (!confirmed || !mounted) return;
    if (await _run(() => _repository.delete(_id)) && mounted) _backToList();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final user = ref.watch(authControllerProvider).value;
    final household = ref.watch(householdProvider(_id));
    final members = ref.watch(membersProvider(_id));
    final current = household.value;

    return Scaffold(
      appBar: AppBar(
        title: Text(current?.name ?? ''),
        actions: [
          if (current != null && current.isOwner)
            IconButton(
              tooltip: l10n.rename,
              icon: const Icon(Icons.edit_outlined),
              onPressed: _busy ? null : () => _rename(current),
            ),
        ],
      ),
      body: household.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Text(errorMessage(l10n, error), textAlign: TextAlign.center),
          ),
        ),
        data: (household) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Card(
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                      child: Text(l10n.membersTitle, style: theme.textTheme.titleMedium),
                    ),
                    ...members.when(
                      loading: () => const [Padding(padding: EdgeInsets.all(16), child: LinearProgressIndicator())],
                      error: (error, _) => [
                        Padding(padding: const EdgeInsets.all(16), child: Text(errorMessage(l10n, error))),
                      ],
                      data: (items) => [
                        for (final member in items)
                          ListTile(
                            leading: CircleAvatar(child: Text(member.displayName.characters.first.toUpperCase())),
                            title: Text(
                              member.userId == user?.id ? '${member.displayName} (${l10n.you})' : member.displayName,
                            ),
                            subtitle: Text(
                              '${member.role == HouseholdRole.owner ? l10n.roleOwner : l10n.roleMember}'
                              ' · ${l10n.joinedOn(_formatDate(member.joinedAt))}',
                            ),
                            trailing: household.isOwner && member.userId != user?.id
                                ? IconButton(
                                    tooltip: l10n.remove,
                                    icon: const Icon(Icons.person_remove_outlined),
                                    onPressed: _busy ? null : () => _removeMember(member),
                                  )
                                : null,
                          ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(l10n.inviteTitle, style: theme.textTheme.titleMedium),
                    const SizedBox(height: 4),
                    Text(l10n.inviteHelp, style: TextStyle(color: theme.colorScheme.onSurfaceVariant)),
                    if (_invitation case final invitation?) ...[
                      const SizedBox(height: 16),
                      Container(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: theme.colorScheme.primaryContainer,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Column(
                          children: [
                            SelectableText(
                              invitation.code,
                              style: theme.textTheme.headlineMedium?.copyWith(
                                fontWeight: FontWeight.w700,
                                letterSpacing: 4,
                                color: theme.colorScheme.onPrimaryContainer,
                              ),
                            ),
                            Text(l10n.inviteExpires(_formatDate(invitation.expiresAt))),
                          ],
                        ),
                      ),
                    ],
                    const SizedBox(height: 16),
                    OutlinedButton(onPressed: _busy ? null : _invite, child: Text(l10n.inviteGenerate)),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 32),
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton(
                style: TextButton.styleFrom(foregroundColor: theme.colorScheme.error),
                onPressed: _busy || user == null
                    ? null
                    : () => household.isOwner ? _delete(household) : _leave(household, user.id),
                child: Text(household.isOwner ? l10n.deleteHousehold : l10n.leaveHousehold),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
