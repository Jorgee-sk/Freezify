import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/app_localizations.dart';
import '../households/dialogs.dart';
import '../inventory/inventory_format.dart';
import 'plan_models.dart';
import 'plan_repository.dart';
import 'plan_wording.dart';

/// How far ahead a recipe can be added from its page; further days are planned from the plan itself.
const _daysAhead = 14;

/// Asks for a day and a meal and puts the recipe there, saying first what it would replace. Pops with the place
/// the recipe was added to, or nothing when cancelled.
Future<MealPlace?> showAddToPlan(BuildContext context, {required String householdId, required String recipeId}) =>
    showModalBottomSheet<MealPlace>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => _AddToPlanSheet(householdId: householdId, recipeId: recipeId),
    );

class _AddToPlanSheet extends ConsumerStatefulWidget {
  const _AddToPlanSheet({required this.householdId, required this.recipeId});

  final String householdId;
  final String recipeId;

  @override
  ConsumerState<_AddToPlanSheet> createState() => _AddToPlanSheetState();
}

class _AddToPlanSheetState extends ConsumerState<_AddToPlanSheet> {
  final String _today = toIsoDay(DateTime.now());
  late String _date = _today;
  MealSlot _slot = MealSlot.dinner;
  bool _saving = false;

  Future<void> _add() async {
    setState(() => _saving = true);
    final place = (date: _date, slot: _slot);
    try {
      await ref.read(planRepositoryProvider).choose(widget.householdId, place, widget.recipeId);
      ref.invalidate(mealPlanProvider);
      if (mounted) Navigator.of(context).pop(place);
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
    // The week of the chosen day, to tell whether something is already planned there.
    final week = ref.watch(mealPlanProvider((householdId: widget.householdId, week: _date, language: l10n.localeName)));
    final taken = week.value?.mealAt((date: _date, slot: _slot));

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.planAddTitle, style: theme.textTheme.titleMedium),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              initialValue: _date,
              decoration: InputDecoration(labelText: l10n.planAddDay),
              items: [
                for (var day = 0; day < _daysAhead; day++)
                  DropdownMenuItem(value: addDays(_today, day), child: Text(weekdayAndDay(l10n, addDays(_today, day)))),
              ],
              onChanged: (date) => setState(() => _date = date ?? _date),
            ),
            const SizedBox(height: 12),
            SegmentedButton<MealSlot>(
              segments: [
                for (final slot in MealSlot.values) ButtonSegment(value: slot, label: Text(slotName(l10n, slot))),
              ],
              selected: {_slot},
              onSelectionChanged: (selected) => setState(() => _slot = selected.first),
            ),
            // Adding replaces what is there: never without saying so.
            if (taken != null && taken.recipeId != widget.recipeId) ...[
              const SizedBox(height: 12),
              Text(
                l10n.planAddReplaces(taken.recipeName),
                style: TextStyle(color: theme.colorScheme.error, fontWeight: FontWeight.w600),
              ),
            ],
            const SizedBox(height: 16),
            FilledButton(onPressed: _saving ? null : _add, child: Text(l10n.planAdd)),
          ],
        ),
      ),
    );
  }
}
