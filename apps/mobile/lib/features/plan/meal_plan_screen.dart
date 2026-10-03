import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/errors.dart';
import '../../core/providers.dart';
import '../../l10n/app_localizations.dart';
import '../households/dialogs.dart';
import '../realtime/household_event_stream.dart';
import '../recipes/diet_models.dart';
import '../recipes/recipe_models.dart';
import '../recipes/recipe_repository.dart';
import '../recipes/recipe_wording.dart';
import 'plan_models.dart';
import 'plan_repository.dart';
import 'plan_wording.dart';

enum _MealAction { change, move, remove }

/// What the household plans to eat this week: one recipe per day for lunch and for dinner.
class MealPlanScreen extends ConsumerStatefulWidget {
  const MealPlanScreen({super.key, required this.householdId, this.initialWeek});

  final String householdId;

  /// Any day of the week to show first; the week of today when null.
  final String? initialWeek;

  @override
  ConsumerState<MealPlanScreen> createState() => _MealPlanScreenState();
}

class _MealPlanScreenState extends ConsumerState<MealPlanScreen> {
  /// Any day of the week on screen; null for the week of today.
  late String? _week = widget.initialWeek;
  bool _busy = false;
  late final HouseholdEventStream _events;

  String get _householdId => widget.householdId;
  PlanRepository get _repository => ref.read(planRepositoryProvider);

  @override
  void initState() {
    super.initState();
    // A change to the inventory matters as much as a change to the plan: what each meal will find at home
    // depends on it.
    _events = HouseholdEventStream(
      ref.read(apiClientProvider),
      _householdId,
      onInventoryChanged: _reload,
      onMealPlanChanged: _reload,
    )..start();
  }

  @override
  void dispose() {
    _events.dispose();
    super.dispose();
  }

  void _reload() {
    if (mounted) ref.invalidate(mealPlanProvider);
  }

  /// Runs a change to the plan and shows the plan as the server has it afterwards.
  Future<void> _change(Future<void> Function() action) async {
    setState(() => _busy = true);
    try {
      await action();
    } catch (error) {
      if (mounted) showError(context, error);
    } finally {
      if (mounted) {
        setState(() => _busy = false);
        ref.invalidate(mealPlanProvider);
      }
    }
  }

  Future<void> _choose(MealPlace place) async {
    final recipeId = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => _RecipePicker(householdId: _householdId),
    );
    if (recipeId == null || !mounted) return;
    await _change(() => _repository.choose(_householdId, place, recipeId));
  }

  /// What was cooked is said on the day: the server records it as cooked today.
  Future<void> _markCooked(PlannedMeal meal) => _change(() async {
    await ref.read(recipeRepositoryProvider).markCooked(_householdId, meal.recipeId);
    // Cooking something today makes it less of a novelty for the recommender.
    ref.invalidate(recommendationsProvider);
  });

  Future<void> _move(MealPlan plan, MealPlace from) async {
    final to = await showDialog<MealPlace>(
      context: context,
      builder: (_) => _MoveDialog(plan: plan, from: from),
    );
    if (to == null || !mounted) return;
    await _change(() => _repository.move(_householdId, from, to));
  }

  Future<void> _generate(MealPlan plan, {required bool replace}) async {
    final l10n = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);
    // It replaces meals that are on screen: never without asking.
    if (replace &&
        !await confirmDestructive(context, message: l10n.planRegenerateConfirm, confirmLabel: l10n.planRegenerate)) {
      return;
    }
    await _change(() async {
      final generated = await _repository.generate(_householdId, plan.weekStart, replaceGenerated: replace);
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(generatedMessage(l10n, generated))));
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final MealPlanQuery query = (householdId: _householdId, week: _week, language: l10n.localeName);
    final plan = ref.watch(mealPlanProvider(query));
    final diet = ref.watch(dietProvider(_householdId)).value;

    return Scaffold(
      appBar: AppBar(title: Text(l10n.planTitle)),
      body: plan.when(
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
        data: (plan) => RefreshIndicator(
          onRefresh: () => ref.refresh(mealPlanProvider(query).future),
          // A week is short: every day is built, so that none has to be scrolled to before it exists.
          child: SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _WeekBar(plan: plan, showsThisWeek: _week == null, onShow: (week) => setState(() => _week = week)),
                const SizedBox(height: 8),
                if (plan.isOver)
                  Text(l10n.planPastWeek, style: TextStyle(color: theme.colorScheme.onSurfaceVariant))
                else
                  _GenerateCard(
                    plan: plan,
                    busy: _busy,
                    onGenerate: () => _generate(plan, replace: false),
                    onRegenerate: () => _generate(plan, replace: true),
                  ),
                if (plan.unusedExpiring.isNotEmpty) ...[const SizedBox(height: 12), _UnusedCard(plan: plan)],
                for (final date in weekDays(plan.weekStart)) ...[
                  const SizedBox(height: 12),
                  _DayCard(
                    plan: plan,
                    date: date,
                    diet: diet,
                    busy: _busy,
                    onMarkCooked: _markCooked,
                    onOpen: (meal) => context.push('/households/$_householdId/recipes/${meal.recipeId}'),
                    onChoose: _choose,
                    onMove: (place) => _move(plan, place),
                    onRemove: (place) => _change(() => _repository.remove(_householdId, place)),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _WeekBar extends StatelessWidget {
  const _WeekBar({required this.plan, required this.showsThisWeek, required this.onShow});

  final MealPlan plan;
  final bool showsThisWeek;
  final void Function(String? week) onShow;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Row(
      children: [
        IconButton(
          tooltip: l10n.planPreviousWeek,
          icon: const Icon(Icons.chevron_left),
          onPressed: () => onShow(addDays(plan.weekStart, -7)),
        ),
        Expanded(
          child: Column(
            children: [
              Text(
                l10n.planWeekRange(shortDay(l10n, plan.weekStart), shortDay(l10n, plan.weekEnd)),
                style: Theme.of(context).textTheme.titleMedium,
              ),
              if (!showsThisWeek) TextButton(onPressed: () => onShow(null), child: Text(l10n.planThisWeek)),
            ],
          ),
        ),
        IconButton(
          tooltip: l10n.planNextWeek,
          icon: const Icon(Icons.chevron_right),
          onPressed: () => onShow(addDays(plan.weekStart, 7)),
        ),
      ],
    );
  }
}

class _GenerateCard extends StatelessWidget {
  const _GenerateCard({required this.plan, required this.busy, required this.onGenerate, required this.onRegenerate});

  final MealPlan plan;
  final bool busy;
  final VoidCallback onGenerate;
  final VoidCallback onRegenerate;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              l10n.planGenerateHelp,
              style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
            const SizedBox(height: 12),
            FilledButton(onPressed: busy ? null : onGenerate, child: Text(l10n.planGenerate)),
            // Nothing was suggested for the days to come: there is nothing to suggest again.
            if (plan.hasSuggestionsAhead) ...[
              const SizedBox(height: 8),
              OutlinedButton(onPressed: busy ? null : onRegenerate, child: Text(l10n.planRegenerate)),
            ],
          ],
        ),
      ),
    );
  }
}

/// The food this plan would let go to waste.
class _UnusedCard extends StatelessWidget {
  const _UnusedCard({required this.plan});

  final MealPlan plan;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    return Card(
      color: colors.tertiaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: DefaultTextStyle.merge(
          style: TextStyle(color: colors.onTertiaryContainer),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(l10n.planUnusedTitle, style: const TextStyle(fontWeight: FontWeight.w700)),
              Text(l10n.planUnusedHelp, style: theme.textTheme.bodySmall?.copyWith(color: colors.onTertiaryContainer)),
              const SizedBox(height: 4),
              for (final food in plan.unusedExpiring) Text('• ${unusedLine(l10n, food)}'),
            ],
          ),
        ),
      ),
    );
  }
}

class _DayCard extends StatelessWidget {
  const _DayCard({
    required this.plan,
    required this.date,
    required this.diet,
    required this.busy,
    required this.onMarkCooked,
    required this.onOpen,
    required this.onChoose,
    required this.onMove,
    required this.onRemove,
  });

  final MealPlan plan;
  final String date;

  /// What the household does not eat, once known.
  final Diet? diet;
  final bool busy;
  final void Function(PlannedMeal meal) onMarkCooked;
  final void Function(PlannedMeal meal) onOpen;
  final void Function(MealPlace place) onChoose;
  final void Function(MealPlace place) onMove;
  final void Function(MealPlace place) onRemove;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant);

    return Card(
      key: ValueKey('day-$date'),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 8, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(
                  weekdayAndDay(l10n, date),
                  style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
                ),
                if (date == plan.today) ...[
                  const SizedBox(width: 8),
                  Text(l10n.planToday, style: theme.textTheme.labelMedium?.copyWith(color: theme.colorScheme.primary)),
                ],
              ],
            ),
            for (final slot in MealSlot.values) ...[
              const Divider(height: 16),
              _meal(context, (date: date, slot: slot), muted),
            ],
          ],
        ),
      ),
    );
  }

  Widget _meal(BuildContext context, MealPlace place, TextStyle? muted) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final meal = plan.mealAt(place);

    return Column(
      key: ValueKey('meal-${place.date}-${place.slot.wire}'),
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(child: Text(slotName(l10n, place.slot), style: muted)),
            if (meal?.cooked ?? false) ...[
              Text(l10n.planCooked, style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.primary)),
              const SizedBox(width: 8),
            ],
            // Only what the generator chose is marked as a suggestion.
            if (meal?.origin == MealOrigin.generated) Text(l10n.planSuggested, style: theme.textTheme.labelSmall),
            if (meal != null)
              PopupMenuButton<_MealAction>(
                tooltip: l10n.planMealActions,
                enabled: !busy,
                onSelected: (action) => switch (action) {
                  _MealAction.change => onChoose(place),
                  _MealAction.move => onMove(place),
                  _MealAction.remove => onRemove(place),
                },
                itemBuilder: (_) => [
                  PopupMenuItem(value: _MealAction.change, child: Text(l10n.planChange)),
                  PopupMenuItem(value: _MealAction.move, child: Text(l10n.planMove)),
                  PopupMenuItem(value: _MealAction.remove, child: Text(l10n.planRemove)),
                ],
              ),
          ],
        ),
        if (meal == null)
          Row(
            children: [
              Expanded(child: Text(l10n.planEmpty, style: muted)),
              TextButton(onPressed: busy ? null : () => onChoose(place), child: Text(l10n.planChoose)),
            ],
          )
        else ...[
          InkWell(
            onTap: () => onOpen(meal),
            child: Text(
              meal.recipeName,
              style: theme.textTheme.bodyLarge?.copyWith(fontWeight: FontWeight.w600, color: theme.colorScheme.primary),
            ),
          ),
          Text('${l10n.recipeMinutes(meal.totalMinutes)} · ${difficultyName(l10n, meal.difficulty)}', style: muted),
          // Generated meals never contain it; a recipe chosen by hand, or before the restrictions changed, might.
          if (diet != null && diet!.conflictsWith(meal.contains).isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 2, right: 8),
              child: Text(
                l10n.dietConflict(traitList(l10n, diet!.conflictsWith(meal.contains))),
                style: TextStyle(color: theme.colorScheme.error, fontWeight: FontWeight.w600),
              ),
            ),
          for (final note in mealNotes(l10n, meal))
            Padding(padding: const EdgeInsets.only(top: 2, right: 8), child: Text('• $note')),
          if (meal.date == plan.today && !meal.cooked)
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton(onPressed: busy ? null : () => onMarkCooked(meal), child: Text(l10n.markCooked)),
            ),
        ],
      ],
    );
  }
}

/// The recipes the household eats, to choose one for a meal. Pops with the id of the chosen recipe.
class _RecipePicker extends ConsumerStatefulWidget {
  const _RecipePicker({required this.householdId});

  final String householdId;

  @override
  ConsumerState<_RecipePicker> createState() => _RecipePickerState();
}

class _RecipePickerState extends ConsumerState<_RecipePicker> {
  static const _searchDelay = Duration(milliseconds: 300);

  String _text = '';
  Timer? _searchTimer;

  @override
  void dispose() {
    _searchTimer?.cancel();
    super.dispose();
  }

  void _search(String text) {
    // Waits for the typing to pause instead of asking the server on every key.
    _searchTimer?.cancel();
    _searchTimer = Timer(_searchDelay, () => setState(() => _text = text.trim()));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final RecipeQuery query = (
      householdId: widget.householdId,
      language: l10n.localeName,
      text: _text,
      maxMinutes: null,
      course: null,
    );
    final recipes = ref.watch(recipeListProvider(query));

    return SafeArea(
      child: Padding(
        // The keyboard must not cover the search field.
        padding: EdgeInsets.fromLTRB(16, 0, 16, MediaQuery.viewInsetsOf(context).bottom),
        child: SizedBox(
          height: MediaQuery.sizeOf(context).height * 0.7,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(l10n.planPickerTitle, style: theme.textTheme.titleMedium),
              const SizedBox(height: 8),
              TextField(
                decoration: InputDecoration(
                  hintText: l10n.searchRecipes,
                  prefixIcon: const Icon(Icons.search),
                  isDense: true,
                ),
                textInputAction: TextInputAction.search,
                onChanged: _search,
              ),
              const SizedBox(height: 8),
              Expanded(
                child: recipes.when(
                  loading: () => const Center(child: CircularProgressIndicator()),
                  error: (error, _) => Center(
                    child: Text(errorMessage(l10n, error), style: TextStyle(color: theme.colorScheme.error)),
                  ),
                  data: (items) => items.isEmpty
                      ? Center(child: Text(l10n.recipesEmptyFiltered))
                      : ListView.builder(
                          itemCount: items.length,
                          itemBuilder: (context, index) => ListTile(
                            contentPadding: EdgeInsets.zero,
                            title: Text(items[index].name),
                            subtitle: Text(recipeFacts(l10n, items[index])),
                            onTap: () => Navigator.of(context).pop(items[index].id),
                          ),
                        ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Where to move a meal to: any other meal of the week on screen. A taken one swaps with it.
class _MoveDialog extends StatelessWidget {
  const _MoveDialog({required this.plan, required this.from});

  final MealPlan plan;
  final MealPlace from;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final places = <MealPlace>[
      for (final date in weekDays(plan.weekStart))
        for (final slot in MealSlot.values)
          if ((date: date, slot: slot) != from) (date: date, slot: slot),
    ];
    return SimpleDialog(
      title: Text(l10n.planMoveTitle),
      children: [
        for (final place in places)
          ListTile(
            dense: true,
            contentPadding: const EdgeInsets.symmetric(horizontal: 24),
            title: Text(l10n.planMovePlace(weekdayAndDay(l10n, place.date), slotName(l10n, place.slot))),
            subtitle: switch (plan.mealAt(place)) {
              null => null,
              final taken => Text(l10n.planMoveSwap(taken.recipeName)),
            },
            onTap: () => Navigator.of(context).pop(place),
          ),
      ],
    );
  }
}
