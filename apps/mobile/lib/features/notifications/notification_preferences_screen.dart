import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/errors.dart';
import '../../l10n/app_localizations.dart';
import '../households/dialogs.dart';
import '../inventory/inventory_format.dart';
import '../inventory/inventory_models.dart';
import 'notification_models.dart';
import 'notification_repository.dart';

class NotificationPreferencesScreen extends ConsumerWidget {
  const NotificationPreferencesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final preferences = ref.watch(notificationPreferencesProvider);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.notificationPreferences)),
      body: preferences.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(errorMessage(l10n, error), style: TextStyle(color: theme.colorScheme.error)),
              TextButton(onPressed: () => ref.invalidate(notificationPreferencesProvider), child: Text(l10n.retry)),
            ],
          ),
        ),
        // The form starts from what the server has; it is only built once that is known.
        data: (initial) => _PreferencesForm(initial: initial),
      ),
    );
  }
}

class _PreferencesForm extends ConsumerStatefulWidget {
  const _PreferencesForm({required this.initial});

  final NotificationPreferences initial;

  @override
  ConsumerState<_PreferencesForm> createState() => _PreferencesFormState();
}

class _PreferencesFormState extends ConsumerState<_PreferencesForm> {
  late NotificationPreferences _draft = widget.initial;
  bool _saving = false;

  void _change(NotificationPreferences changed) => setState(() => _draft = changed);

  void _toggleCategory(FoodCategory category, bool wanted) {
    final muted = {..._draft.mutedCategories};
    wanted ? muted.remove(category) : muted.add(category);
    _change(_draft.copyWith(mutedCategories: muted));
  }

  Future<void> _save() async {
    final l10n = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);
    setState(() => _saving = true);
    try {
      await ref.read(notificationRepositoryProvider).updatePreferences(_draft);
      ref.invalidate(notificationPreferencesProvider);
      messenger.showSnackBar(SnackBar(content: Text(l10n.preferencesSaved)));
    } catch (error) {
      if (mounted) showError(context, error);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant);
    final enabled = _draft.expirationAlerts;

    // Not a lazy list: the form is short, and all of it has to exist to be saved.
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(l10n.prefEnabled),
            subtitle: Text(l10n.prefAntiSpam),
            value: enabled,
            onChanged: (value) => _change(_draft.copyWith(expirationAlerts: value)),
          ),
          const SizedBox(height: 16),
          DropdownButtonFormField<int>(
            key: const ValueKey('hour'),
            initialValue: _draft.deliveryHour,
            decoration: InputDecoration(labelText: l10n.prefHour, helperText: l10n.prefHourHelp),
            items: [
              for (var hour = 0; hour < 24; hour++)
                DropdownMenuItem(value: hour, child: Text('${hour.toString().padLeft(2, '0')}:00')),
            ],
            onChanged: enabled ? (value) => _change(_draft.copyWith(deliveryHour: value)) : null,
          ),
          const SizedBox(height: 16),
          DropdownButtonFormField<NotificationFrequency>(
            key: const ValueKey('frequency'),
            initialValue: _draft.frequency,
            decoration: InputDecoration(labelText: l10n.prefFrequency),
            items: [
              for (final option in NotificationFrequency.values)
                DropdownMenuItem(value: option, child: Text(_frequencyName(l10n, option))),
            ],
            onChanged: enabled ? (value) => _change(_draft.copyWith(frequency: value)) : null,
          ),
          const SizedBox(height: 16),
          DropdownButtonFormField<NotificationThreshold>(
            key: const ValueKey('threshold'),
            initialValue: _draft.threshold,
            decoration: InputDecoration(labelText: l10n.prefThreshold),
            items: [
              for (final option in NotificationThreshold.values)
                DropdownMenuItem(value: option, child: Text(_thresholdName(l10n, option))),
            ],
            onChanged: enabled ? (value) => _change(_draft.copyWith(threshold: value)) : null,
          ),
          const SizedBox(height: 24),
          Text(l10n.prefCategories, style: muted),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: [
              for (final category in FoodCategory.values)
                FilterChip(
                  label: Text(categoryName(l10n, category)),
                  selected: !_draft.mutedCategories.contains(category),
                  onSelected: enabled ? (wanted) => _toggleCategory(category, wanted) : null,
                ),
            ],
          ),
          const SizedBox(height: 24),
          FilledButton(onPressed: _saving ? null : _save, child: Text(l10n.save)),
        ],
      ),
    );
  }
}

String _frequencyName(AppLocalizations l10n, NotificationFrequency frequency) => switch (frequency) {
  NotificationFrequency.daily => l10n.prefFrequencyDaily,
  NotificationFrequency.everyThreeDays => l10n.prefFrequencyEveryThreeDays,
  NotificationFrequency.weekly => l10n.prefFrequencyWeekly,
};

String _thresholdName(AppLocalizations l10n, NotificationThreshold threshold) => switch (threshold) {
  NotificationThreshold.today => l10n.prefThresholdToday,
  NotificationThreshold.urgent => l10n.prefThresholdUrgent,
  NotificationThreshold.soon => l10n.prefThresholdSoon,
};
