import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/errors.dart';
import '../../l10n/app_localizations.dart';
import 'inventory_format.dart';
import 'inventory_models.dart';
import 'inventory_repository.dart';

/// Adds a food to the inventory, or edits [item]. Pops with `true` when something was saved.
class ItemFormScreen extends ConsumerStatefulWidget {
  const ItemFormScreen({super.key, required this.householdId, this.item});

  final String householdId;
  final InventoryItem? item;

  @override
  ConsumerState<ItemFormScreen> createState() => _ItemFormScreenState();
}

class _ItemFormScreenState extends ConsumerState<ItemFormScreen> {
  static const _minSearchLength = 2;
  static const _searchDelay = Duration(milliseconds: 300);

  final _formKey = GlobalKey<FormState>();
  late final _name = TextEditingController(text: widget.item?.name ?? '');
  late final _brand = TextEditingController(text: widget.item?.brand ?? '');
  late final _notes = TextEditingController(text: widget.item?.notes ?? '');
  TextEditingController? _amountController;
  TextEditingController? _priceController;

  late String? _foodId = widget.item?.foodId;
  late FoodCategory? _category = widget.item?.category;
  late Unit _unit = widget.item?.quantity.unit ?? Unit.unit;
  late StorageLocation _location = widget.item?.storageLocation ?? StorageLocation.refrigerator;
  // Only the date the user gave is editable; an estimate is shown as a hint and recalculated on save.
  late String? _expirationDate = widget.item?.userExpirationDate;
  late String _purchaseDate = widget.item?.purchaseDate ?? toIsoDay(DateTime.now());

  List<Food> _suggestions = const [];
  Timer? _searchTimer;
  Object? _error;
  bool _saving = false;

  InventoryRepository get _repository => ref.read(inventoryRepositoryProvider);
  String get _language => Localizations.localeOf(context).languageCode;

  // Created on first use because formatting numbers needs the language of the context.
  TextEditingController get _amount =>
      _amountController ??= TextEditingController(text: formatAmount(widget.item?.quantity.amount ?? 1, _language));
  TextEditingController get _price => _priceController ??= TextEditingController(
    text: widget.item?.estimatedPrice == null ? '' : formatAmount(widget.item!.estimatedPrice!, _language),
  );

  @override
  void dispose() {
    _searchTimer?.cancel();
    _name.dispose();
    _brand.dispose();
    _notes.dispose();
    _amountController?.dispose();
    _priceController?.dispose();
    super.dispose();
  }

  void _nameChanged(String text) {
    // A different name is no longer the catalog food that was picked.
    _foodId = null;
    _searchTimer?.cancel();
    final wanted = text.trim();
    if (wanted.length < _minSearchLength) {
      setState(() => _suggestions = const []);
      return;
    }
    final language = _language;
    _searchTimer = Timer(_searchDelay, () async {
      try {
        final foods = await _repository.searchFoods(wanted, language);
        // Ignores answers that arrive after the text has changed again.
        if (mounted && _name.text.trim() == wanted) setState(() => _suggestions = foods);
      } catch (_) {
        // Suggestions are a convenience: without them the name can still be typed in full.
      }
    });
  }

  void _pickFood(Food food) {
    _searchTimer?.cancel();
    setState(() {
      _name.text = food.name;
      _foodId = food.id;
      _category = food.category;
      _unit = food.defaultUnit;
      _location = food.defaultStorage;
      _suggestions = const [];
    });
  }

  void _pickRecent(RecentFood food) {
    setState(() {
      _name.text = food.name;
      _foodId = food.foodId;
      _category = food.category;
      _amount.text = formatAmount(food.quantity.amount, _language);
      _unit = food.quantity.unit;
      _location = food.storageLocation;
    });
  }

  Future<String?> _pickDay(String? current) async {
    final initial = current == null ? DateTime.now() : parseIsoDay(current);
    final picked = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime(initial.year - 5),
      lastDate: DateTime(initial.year + 10),
    );
    return picked == null ? null : toIsoDay(picked);
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    final item = widget.item;
    final input = ItemInput(
      foodId: _foodId,
      name: _name.text.trim(),
      category: _category,
      quantity: Quantity(parseAmount(_amount.text)!, _unit),
      storageLocation: _location,
      purchaseDate: _purchaseDate,
      expirationDate: _expirationDate,
      // Not editable here, so they are sent back unchanged.
      openedDate: item?.openedDate,
      barcode: item?.barcode,
      brand: _brand.text.trim().isEmpty ? null : _brand.text.trim(),
      estimatedPrice: parseAmount(_price.text),
      notes: _notes.text.trim().isEmpty ? null : _notes.text.trim(),
    );
    setState(() {
      _error = null;
      _saving = true;
    });
    try {
      if (item == null) {
        await _repository.create(widget.householdId, input);
      } else {
        await _repository.update(widget.householdId, item.id, input);
      }
      if (mounted) Navigator.of(context).pop(true);
    } catch (error) {
      if (mounted) {
        setState(() {
          _error = error;
          _saving = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final editing = widget.item != null;
    final recent = editing ? null : ref.watch(recentFoodsProvider(widget.householdId)).value;
    final showRecent = recent != null && recent.isNotEmpty && _name.text.isEmpty;

    return Scaffold(
      appBar: AppBar(title: Text(editing ? l10n.editFood : l10n.newFood)),
      body: Form(
        key: _formKey,
        // Not a lazy list: every field must exist for validation, whether or not it is on screen.
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextFormField(
                controller: _name,
                autofocus: !editing,
                maxLength: 120,
                textCapitalization: TextCapitalization.sentences,
                decoration: InputDecoration(labelText: l10n.foodName, counterText: ''),
                onChanged: _nameChanged,
                validator: (value) => (value == null || value.trim().isEmpty) ? l10n.fieldRequired : null,
              ),
              if (_suggestions.isNotEmpty)
                _Chips(
                  label: l10n.suggestions,
                  names: [for (final food in _suggestions) food.name],
                  onPick: (index) => _pickFood(_suggestions[index]),
                ),
              if (showRecent)
                _Chips(
                  label: l10n.recentlyAdded,
                  names: [for (final food in recent) food.name],
                  onPick: (index) => _pickRecent(recent[index]),
                ),
              const SizedBox(height: 16),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: TextFormField(
                      controller: _amount,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      decoration: InputDecoration(labelText: l10n.amount),
                      validator: (value) => parseAmount(value ?? '') == null ? l10n.invalidAmount : null,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: DropdownButtonFormField<Unit>(
                      // Rebuilt when a suggestion changes the unit from outside the field.
                      key: ValueKey(_unit),
                      initialValue: _unit,
                      decoration: InputDecoration(labelText: l10n.unit),
                      items: [
                        for (final unit in Unit.values)
                          DropdownMenuItem(value: unit, child: Text(unitName(l10n, unit))),
                      ],
                      onChanged: (value) => setState(() => _unit = value ?? _unit),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<StorageLocation>(
                key: ValueKey(_location),
                initialValue: _location,
                decoration: InputDecoration(labelText: l10n.location),
                items: [
                  for (final location in StorageLocation.values)
                    DropdownMenuItem(value: location, child: Text(locationName(l10n, location))),
                ],
                onChanged: (value) => setState(() => _location = value ?? _location),
              ),
              const SizedBox(height: 8),
              _DayTile(
                label: l10n.expirationDate,
                value: _expirationDate,
                // Without a date from the user the backend estimates one; say so, and show the current estimate.
                emptyText: switch (widget.item) {
                  InventoryItem(expirationSource: ExpirationSource.estimated, :final expirationDate?) =>
                    l10n.currentEstimate(formatDay(expirationDate)),
                  _ => l10n.estimateHelp,
                },
                clearTooltip: l10n.clearDate,
                onPick: () async {
                  final day = await _pickDay(_expirationDate);
                  if (day != null) setState(() => _expirationDate = day);
                },
                onClear: () => setState(() => _expirationDate = null),
              ),
              ExpansionTile(
                title: Text(l10n.moreDetails),
                tilePadding: EdgeInsets.zero,
                childrenPadding: const EdgeInsets.only(bottom: 8),
                initiallyExpanded: editing,
                children: [
                  DropdownButtonFormField<FoodCategory?>(
                    key: ValueKey(_category),
                    initialValue: _category,
                    decoration: InputDecoration(labelText: l10n.category),
                    items: [
                      const DropdownMenuItem(child: Text('—')),
                      for (final category in FoodCategory.values)
                        DropdownMenuItem(value: category, child: Text(categoryName(l10n, category))),
                    ],
                    onChanged: (value) => setState(() => _category = value),
                  ),
                  _DayTile(
                    label: l10n.purchaseDate,
                    value: _purchaseDate,
                    emptyText: l10n.noDateSet,
                    onPick: () async {
                      final day = await _pickDay(_purchaseDate);
                      if (day != null) setState(() => _purchaseDate = day);
                    },
                  ),
                  TextFormField(
                    controller: _brand,
                    maxLength: 80,
                    decoration: InputDecoration(labelText: l10n.brand, counterText: ''),
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: _price,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: InputDecoration(labelText: l10n.price),
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: _notes,
                    maxLength: 500,
                    maxLines: null,
                    decoration: InputDecoration(labelText: l10n.notes, counterText: ''),
                  ),
                ],
              ),
              if (_error != null) ...[
                const SizedBox(height: 8),
                Semantics(
                  liveRegion: true,
                  child: Text(errorMessage(l10n, _error!), style: TextStyle(color: theme.colorScheme.error)),
                ),
              ],
              const SizedBox(height: 16),
              FilledButton(onPressed: _saving ? null : _save, child: Text(l10n.save)),
            ],
          ),
        ),
      ),
    );
  }
}

class _Chips extends StatelessWidget {
  const _Chips({required this.label, required this.names, required this.onPick});

  final String label;
  final List<String> names;
  final ValueChanged<int> onPick;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: label,
      container: true,
      child: Padding(
        padding: const EdgeInsets.only(top: 8),
        child: Wrap(
          spacing: 8,
          children: [
            for (final (index, name) in names.indexed) ActionChip(label: Text(name), onPressed: () => onPick(index)),
          ],
        ),
      ),
    );
  }
}

/// A calendar day shown as a row that opens the date picker when tapped.
class _DayTile extends StatelessWidget {
  const _DayTile({
    required this.label,
    required this.value,
    required this.emptyText,
    required this.onPick,
    this.clearTooltip,
    this.onClear,
  });

  final String label;
  final String? value;
  final String emptyText;
  final VoidCallback onPick;
  final String? clearTooltip;
  final VoidCallback? onClear;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: const Icon(Icons.event_outlined),
      title: Text(label),
      subtitle: Text(value == null ? emptyText : formatDay(value!)),
      trailing: value != null && onClear != null
          ? IconButton(tooltip: clearTooltip, icon: const Icon(Icons.clear), onPressed: onClear)
          : null,
      onTap: onPick,
    );
  }
}
