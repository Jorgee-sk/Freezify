import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/errors.dart';
import '../../core/providers.dart';
import '../../l10n/app_localizations.dart';
import '../households/dialogs.dart';
import '../inventory/inventory_format.dart';
import '../inventory/inventory_models.dart';
import '../inventory/inventory_repository.dart';
import '../plan/plan_wording.dart';
import '../realtime/household_event_stream.dart';
import 'shopping_models.dart';
import 'shopping_repository.dart';

enum _ListAction { thisWeek, nextWeek, removeChecked }

enum _LineAction { change, remove }

/// The shared shopping list of a household, made to be used in the shop: aisle by aisle, ticking things off.
class ShoppingListScreen extends ConsumerStatefulWidget {
  const ShoppingListScreen({super.key, required this.householdId});

  final String householdId;

  @override
  ConsumerState<ShoppingListScreen> createState() => _ShoppingListScreenState();
}

class _ShoppingListScreenState extends ConsumerState<ShoppingListScreen> {
  bool _busy = false;
  late final HouseholdEventStream _events;

  String get _householdId => widget.householdId;
  ShoppingRepository get _repository => ref.read(shoppingRepositoryProvider);

  @override
  void initState() {
    super.initState();
    // Whoever else is in the shop, or at home adding things, is seen at once.
    _events = HouseholdEventStream(
      ref.read(apiClientProvider),
      _householdId,
      onInventoryChanged: _reload,
      onShoppingListChanged: _reload,
    )..start();
  }

  @override
  void dispose() {
    _events.dispose();
    super.dispose();
  }

  void _reload() {
    if (mounted) ref.invalidate(shoppingListProvider);
  }

  /// Runs a change to the list and shows the list as the server has it afterwards.
  Future<void> _change(Future<void> Function() action) async {
    setState(() => _busy = true);
    try {
      await action();
    } catch (error) {
      if (mounted) showError(context, error);
    } finally {
      if (mounted) {
        setState(() => _busy = false);
        ref.invalidate(shoppingListProvider);
      }
    }
  }

  Future<void> _fill(String week) async {
    final l10n = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);
    await _change(() async {
      final lines = await _repository.fillFromPlan(_householdId, week);
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(lines == 0 ? l10n.shoppingNothingLacking : l10n.shoppingFilled(lines))));
    });
  }

  Future<void> _removeChecked(int bought) async {
    final l10n = AppLocalizations.of(context);
    // They leave the list for good: never without asking.
    final confirmed = await confirmDestructive(
      context,
      message: l10n.shoppingRemoveCheckedConfirm(bought),
      confirmLabel: l10n.shoppingRemoveChecked(bought),
    );
    if (confirmed) await _change(() => _repository.removeChecked(_householdId));
  }

  Future<void> _add() async {
    final input = await showModalBottomSheet<ShoppingItemInput>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => const _AddLineSheet(),
    );
    if (input == null || !mounted) return;
    final language = AppLocalizations.of(context).localeName;
    await _change(() => _repository.add(_householdId, input, language));
  }

  Future<void> _edit(ShoppingItem item) async {
    final quantity = await showDialog<_QuantityChoice>(
      context: context,
      builder: (_) => _QuantityDialog(item: item),
    );
    if (quantity == null || !mounted) return;
    final language = AppLocalizations.of(context).localeName;
    await _change(
      () => _repository.edit(
        _householdId,
        item.id,
        ShoppingItemInput(
          foodId: item.foodId,
          name: item.foodId == null ? item.name : null,
          category: item.category,
          quantity: quantity.quantity,
        ),
        language,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final list = ref.watch(shoppingListProvider((householdId: _householdId, language: l10n.localeName)));
    final items = list.value ?? const <ShoppingItem>[];
    final bought = items.where((item) => item.checked).length;
    final today = toIsoDay(DateTime.now());

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.shoppingTitle),
        actions: [
          PopupMenuButton<_ListAction>(
            tooltip: l10n.shoppingActions,
            enabled: !_busy,
            onSelected: (action) => switch (action) {
              _ListAction.thisWeek => _fill(today),
              _ListAction.nextWeek => _fill(addDays(today, 7)),
              _ListAction.removeChecked => _removeChecked(bought),
            },
            itemBuilder: (_) => [
              PopupMenuItem(value: _ListAction.thisWeek, child: Text(l10n.shoppingFromPlanThisWeek)),
              PopupMenuItem(value: _ListAction.nextWeek, child: Text(l10n.shoppingFromPlanNextWeek)),
              if (bought > 0)
                PopupMenuItem(value: _ListAction.removeChecked, child: Text(l10n.shoppingRemoveChecked(bought))),
            ],
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _busy ? null : _add,
        icon: const Icon(Icons.add),
        label: Text(l10n.shoppingAdd),
      ),
      body: list.when(
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
        data: (items) => RefreshIndicator(
          onRefresh: () =>
              ref.refresh(shoppingListProvider((householdId: _householdId, language: l10n.localeName)).future),
          child: items.isEmpty
              ? ListView(
                  padding: const EdgeInsets.all(24),
                  children: [Text(l10n.shoppingEmpty, textAlign: TextAlign.center)],
                )
              : ListView(
                  padding: const EdgeInsets.only(bottom: 96),
                  children: [
                    for (final (index, item) in items.indexed) ...[
                      // The list comes aisle by aisle: a heading wherever the aisle changes.
                      if (index == 0 || items[index - 1].category != item.category)
                        Padding(
                          padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
                          child: Text(categoryName(l10n, item.category), style: theme.textTheme.titleSmall),
                        ),
                      _Line(
                        item: item,
                        busy: _busy,
                        onCheck: (checked) => _change(() => _repository.check(_householdId, item.id, checked: checked)),
                        onAction: (action) => switch (action) {
                          _LineAction.change => _edit(item),
                          _LineAction.remove => _change(() => _repository.remove(_householdId, item.id)),
                        },
                      ),
                    ],
                  ],
                ),
        ),
      ),
    );
  }
}

class _Line extends StatelessWidget {
  const _Line({required this.item, required this.busy, required this.onCheck, required this.onAction});

  final ShoppingItem item;
  final bool busy;
  final ValueChanged<bool> onCheck;
  final ValueChanged<_LineAction> onAction;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final title = item.quantity == null ? item.name : '${item.name} · ${formatQuantity(l10n, item.quantity!)}';
    return Row(
      key: ValueKey('line-${item.id}'),
      children: [
        Expanded(
          child: CheckboxListTile(
            value: item.checked,
            controlAffinity: ListTileControlAffinity.leading,
            onChanged: busy ? null : (checked) => onCheck(checked ?? false),
            title: Text(
              title,
              style: item.checked
                  ? TextStyle(decoration: TextDecoration.lineThrough, color: theme.colorScheme.onSurfaceVariant)
                  : null,
            ),
            subtitle: item.origin == ShoppingOrigin.plan && item.neededOn != null
                ? Text(l10n.shoppingForPlan(weekdayAndDay(l10n, item.neededOn!)))
                : null,
          ),
        ),
        PopupMenuButton<_LineAction>(
          tooltip: l10n.shoppingLineActions,
          enabled: !busy,
          onSelected: onAction,
          itemBuilder: (_) => [
            PopupMenuItem(value: _LineAction.change, child: Text(l10n.shoppingChange)),
            PopupMenuItem(value: _LineAction.remove, child: Text(l10n.planRemove)),
          ],
        ),
      ],
    );
  }
}

/// Asks what to put on the list: a catalog food when one is picked from the suggestions, free text otherwise.
class _AddLineSheet extends ConsumerStatefulWidget {
  const _AddLineSheet();

  @override
  ConsumerState<_AddLineSheet> createState() => _AddLineSheetState();
}

class _AddLineSheetState extends ConsumerState<_AddLineSheet> {
  static const _searchDelay = Duration(milliseconds: 300);
  static const _minSearchLength = 2;

  final _name = TextEditingController();
  final _amount = TextEditingController();
  Unit _unit = Unit.unit;
  Food? _food;
  List<Food> _suggestions = const [];
  Timer? _searchTimer;

  @override
  void dispose() {
    _searchTimer?.cancel();
    _name.dispose();
    _amount.dispose();
    super.dispose();
  }

  void _nameChanged(String text) {
    // A different name is no longer the catalog food that was picked.
    _food = null;
    _searchTimer?.cancel();
    final wanted = text.trim();
    if (wanted.length < _minSearchLength) {
      setState(() => _suggestions = const []);
      return;
    }
    final language = AppLocalizations.of(context).localeName;
    _searchTimer = Timer(_searchDelay, () async {
      try {
        final foods = await ref.read(inventoryRepositoryProvider).searchFoods(wanted, language);
        // Ignores answers that arrive after the text has changed again.
        if (mounted && _name.text.trim() == wanted) setState(() => _suggestions = foods);
      } catch (_) {
        // Suggestions are a convenience: without them the name can still be typed in full.
      }
    });
  }

  void _pick(Food food) {
    _searchTimer?.cancel();
    setState(() {
      _name.text = food.name;
      _food = food;
      _unit = food.defaultUnit;
      _suggestions = const [];
    });
  }

  void _submit() {
    final name = _name.text.trim();
    if (name.isEmpty) return;
    final amount = parseAmount(_amount.text);
    Navigator.of(context).pop(
      ShoppingItemInput(
        foodId: _food?.id,
        name: _food == null ? name : null,
        quantity: amount == null ? null : Quantity(amount, _unit),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return SafeArea(
      child: Padding(
        // The keyboard must not cover the fields.
        padding: EdgeInsets.fromLTRB(16, 0, 16, 16 + MediaQuery.viewInsetsOf(context).bottom),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.shoppingAddTitle, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 12),
            TextField(
              controller: _name,
              autofocus: true,
              maxLength: 120,
              textCapitalization: TextCapitalization.sentences,
              decoration: InputDecoration(labelText: l10n.shoppingWhat, counterText: ''),
              onChanged: _nameChanged,
            ),
            if (_suggestions.isNotEmpty)
              Semantics(
                label: l10n.suggestions,
                container: true,
                child: Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Wrap(
                    spacing: 8,
                    children: [
                      for (final food in _suggestions) ActionChip(label: Text(food.name), onPressed: () => _pick(food)),
                    ],
                  ),
                ),
              ),
            const SizedBox(height: 12),
            _QuantityFields(amount: _amount, unit: _unit, onUnit: (unit) => setState(() => _unit = unit)),
            const SizedBox(height: 16),
            FilledButton(onPressed: _submit, child: Text(l10n.shoppingAdd)),
          ],
        ),
      ),
    );
  }
}

/// What the quantity dialog answers: `quantity` is null when the amount was left empty.
class _QuantityChoice {
  const _QuantityChoice(this.quantity);

  final Quantity? quantity;
}

/// Changes how much of a line to buy. The line then belongs to people: the plan no longer changes it.
class _QuantityDialog extends StatefulWidget {
  const _QuantityDialog({required this.item});

  final ShoppingItem item;

  @override
  State<_QuantityDialog> createState() => _QuantityDialogState();
}

class _QuantityDialogState extends State<_QuantityDialog> {
  final _amount = TextEditingController();
  late Unit _unit = widget.item.quantity?.unit ?? Unit.unit;
  bool _filled = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // The amount is written as the user's language writes numbers ("1,5").
    if (!_filled && widget.item.quantity != null) {
      _amount.text = formatAmount(widget.item.quantity!.amount, AppLocalizations.of(context).localeName);
    }
    _filled = true;
  }

  @override
  void dispose() {
    _amount.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return AlertDialog(
      title: Text(l10n.shoppingChangeTitle(widget.item.name)),
      content: _QuantityFields(amount: _amount, unit: _unit, onUnit: (unit) => setState(() => _unit = unit)),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(), child: Text(l10n.cancel)),
        FilledButton.tonal(
          onPressed: () {
            final amount = parseAmount(_amount.text);
            Navigator.of(context).pop(_QuantityChoice(amount == null ? null : Quantity(amount, _unit)));
          },
          child: Text(l10n.save),
        ),
      ],
    );
  }
}

/// How much, optionally: "pan" can go on the list without saying how much.
class _QuantityFields extends StatelessWidget {
  const _QuantityFields({required this.amount, required this.unit, required this.onUnit});

  final TextEditingController amount;
  final Unit unit;
  final ValueChanged<Unit> onUnit;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Row(
      children: [
        Expanded(
          child: TextField(
            controller: amount,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: InputDecoration(labelText: l10n.shoppingAmountOptional),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: DropdownButtonFormField<Unit>(
            // Picking a food changes the unit from outside: a new field shows it.
            key: ValueKey(unit),
            initialValue: unit,
            decoration: InputDecoration(labelText: l10n.unit),
            items: [
              for (final option in Unit.values) DropdownMenuItem(value: option, child: Text(unitName(l10n, option))),
            ],
            onChanged: (value) => onUnit(value ?? unit),
          ),
        ),
      ],
    );
  }
}
