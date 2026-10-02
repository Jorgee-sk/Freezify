import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/errors.dart';
import '../../l10n/app_localizations.dart';
import '../households/dialogs.dart';
import 'diet_models.dart';
import 'recipe_repository.dart';
import 'recipe_wording.dart';

/// What is not cooked in the household. It belongs to the household: every member sees and can change it.
class DietScreen extends ConsumerWidget {
  const DietScreen({super.key, required this.householdId});

  final String householdId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final diet = ref.watch(dietProvider(householdId));

    return Scaffold(
      appBar: AppBar(title: Text(l10n.dietTitle)),
      body: diet.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Text(
              errorMessage(l10n, error),
              textAlign: TextAlign.center,
              style: TextStyle(color: theme.colorScheme.error),
            ),
          ),
        ),
        // The form starts from what the server has; it is only built once that is known.
        data: (initial) => _DietForm(householdId: householdId, initial: initial),
      ),
    );
  }
}

class _DietForm extends ConsumerStatefulWidget {
  const _DietForm({required this.householdId, required this.initial});

  final String householdId;
  final Diet initial;

  @override
  ConsumerState<_DietForm> createState() => _DietFormState();
}

class _DietFormState extends ConsumerState<_DietForm> {
  late Diet _draft = widget.initial;
  bool _saving = false;

  void _toggle(FoodTrait trait, bool avoid) {
    final avoided = {..._draft.avoided};
    avoid ? avoided.add(trait) : avoided.remove(trait);
    setState(() => _draft = _draft.copyWith(avoided: avoided));
  }

  Future<void> _save() async {
    final l10n = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);
    setState(() => _saving = true);
    try {
      await ref.read(recipeRepositoryProvider).updateDiet(widget.householdId, _draft);
      // Every list of recipes was filtered with the old restrictions.
      ref.invalidate(dietProvider(widget.householdId));
      ref.invalidate(recipeListProvider);
      ref.invalidate(recommendationsProvider);
      messenger.showSnackBar(SnackBar(content: Text(l10n.dietSaved)));
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

    // Not a lazy list: the form is short, and all of it has to exist to be saved.
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(l10n.dietShared, style: muted),
          const SizedBox(height: 16),
          Text(l10n.dietTypeLabel, style: theme.textTheme.titleSmall),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: [
              for (final type in DietType.values)
                ChoiceChip(
                  label: Text(dietTypeOption(l10n, type)),
                  selected: _draft.type == type,
                  onSelected: (_) => setState(() => _draft = _draft.copyWith(type: type)),
                ),
            ],
          ),
          const SizedBox(height: 16),
          Text(l10n.dietAvoidLabel, style: theme.textTheme.titleSmall),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: [
              for (final trait in FoodTrait.avoidable)
                FilterChip(
                  label: Text(traitOption(l10n, trait)),
                  selected: _draft.avoided.contains(trait),
                  onSelected: (avoid) => _toggle(trait, avoid),
                ),
            ],
          ),
          const SizedBox(height: 16),
          Card(
            color: theme.colorScheme.tertiaryContainer,
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Text(l10n.dietDisclaimer, style: TextStyle(color: theme.colorScheme.onTertiaryContainer)),
            ),
          ),
          const SizedBox(height: 16),
          FilledButton(onPressed: _saving ? null : _save, child: Text(l10n.save)),
        ],
      ),
    );
  }
}
