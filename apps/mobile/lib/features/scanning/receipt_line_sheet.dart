import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/app_localizations.dart';
import '../inventory/inventory_format.dart';
import '../inventory/inventory_models.dart';
import '../inventory/inventory_repository.dart';
import 'scan_models.dart';

/// Lets the person correct one product of a scanned receipt. Returns `true` when [line] was changed.
Future<bool> editReceiptLine(BuildContext context, DraftLine line) async {
  final changed = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (context) => _ReceiptLineSheet(line: line),
  );
  return changed ?? false;
}

class _ReceiptLineSheet extends ConsumerStatefulWidget {
  const _ReceiptLineSheet({required this.line});

  final DraftLine line;

  @override
  ConsumerState<_ReceiptLineSheet> createState() => _ReceiptLineSheetState();
}

class _ReceiptLineSheetState extends ConsumerState<_ReceiptLineSheet> {
  static const _minSearchLength = 2;
  static const _searchDelay = Duration(milliseconds: 300);

  final _formKey = GlobalKey<FormState>();
  late final _name = TextEditingController(text: widget.line.name);
  TextEditingController? _amountController;
  TextEditingController? _priceController;

  late String? _foodId = widget.line.foodId;
  late FoodCategory _category = widget.line.category;
  late Unit _unit = widget.line.quantity.unit;
  late StorageLocation _location = widget.line.storageLocation;
  late String? _expirationDate = widget.line.expirationDate;
  List<Food> _suggestions = const [];
  Timer? _searchTimer;

  String get _language => Localizations.localeOf(context).languageCode;

  TextEditingController get _amount =>
      _amountController ??= TextEditingController(text: formatAmount(widget.line.quantity.amount, _language));
  TextEditingController get _price => _priceController ??= TextEditingController(
    text: widget.line.price == null ? '' : formatAmount(widget.line.price!, _language),
  );

  @override
  void dispose() {
    _searchTimer?.cancel();
    _name.dispose();
    _amountController?.dispose();
    _priceController?.dispose();
    super.dispose();
  }

  void _nameChanged(String text) {
    // A different name is no longer the catalog food that was chosen.
    setState(() => _foodId = null);
    _searchTimer?.cancel();
    final wanted = text.trim();
    if (wanted.length < _minSearchLength) {
      setState(() => _suggestions = const []);
      return;
    }
    final language = _language;
    _searchTimer = Timer(_searchDelay, () async {
      try {
        final foods = await ref.read(inventoryRepositoryProvider).searchFoods(wanted, language);
        if (mounted && _name.text.trim() == wanted) setState(() => _suggestions = foods);
      } catch (_) {
        // Suggestions are a convenience: the name can still be typed in full.
      }
    });
  }

  Future<void> _pickCandidate(FoodCandidate candidate) async {
    _searchTimer?.cancel();
    setState(() {
      _name.text = candidate.name;
      _foodId = candidate.foodId;
      _suggestions = const [];
    });
    // The catalog says where the food is usually kept.
    try {
      final foods = await ref.read(inventoryRepositoryProvider).searchFoods(candidate.name, _language);
      final food = foods.where((food) => food.id == candidate.foodId).firstOrNull;
      if (food != null && mounted && _foodId == food.id) _pickFood(food);
    } catch (_) {
      // Without it, the place stays as it was and can be changed by hand.
    }
  }

  void _pickFood(Food food) {
    _searchTimer?.cancel();
    setState(() {
      _name.text = food.name;
      _foodId = food.id;
      _category = food.category;
      _location = food.defaultStorage;
      _suggestions = const [];
    });
  }

  Future<void> _pickExpiration() async {
    final initial = _expirationDate == null ? DateTime.now() : parseIsoDay(_expirationDate!);
    final picked = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime(initial.year - 1),
      lastDate: DateTime(initial.year + 10),
    );
    if (picked != null) setState(() => _expirationDate = toIsoDay(picked));
  }

  void _apply() {
    if (!_formKey.currentState!.validate()) return;
    final line = widget.line;
    line
      ..name = _name.text.trim()
      ..foodId = _foodId
      ..category = _category
      ..storageLocation = _location
      ..quantity = Quantity(parseAmount(_amount.text)!, _unit)
      ..price = parseAmount(_price.text)
      ..expirationDate = _expirationDate
      ..include = true
      ..reviewed = true;
    Navigator.of(context).pop(true);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final candidates = [
      for (final candidate in widget.line.candidates)
        if (candidate.foodId != _foodId) candidate,
    ];

    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: Form(
        key: _formKey,
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(l10n.scanEditLine, style: theme.textTheme.titleLarge),
              const SizedBox(height: 4),
              Text(
                l10n.scanPrintedAs(widget.line.text),
                style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _name,
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
                )
              else if (candidates.isNotEmpty)
                _Chips(
                  label: l10n.scanCandidates,
                  names: [for (final candidate in candidates) candidate.name],
                  onPick: (index) => _pickCandidate(candidates[index]),
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
              // A catalog food brings its own category; anything else is filed where the person says.
              if (_foodId == null) ...[
                const SizedBox(height: 16),
                DropdownButtonFormField<FoodCategory>(
                  initialValue: _category,
                  decoration: InputDecoration(labelText: l10n.category),
                  items: [
                    for (final category in FoodCategory.values)
                      DropdownMenuItem(value: category, child: Text(categoryName(l10n, category))),
                  ],
                  onChanged: (value) => setState(() => _category = value ?? _category),
                ),
              ],
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
              const SizedBox(height: 16),
              TextFormField(
                controller: _price,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: InputDecoration(labelText: l10n.price),
              ),
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.event_outlined),
                title: Text(l10n.expirationDate),
                subtitle: Text(_expirationDate == null ? l10n.scanExpirationHelp : formatDay(_expirationDate!)),
                trailing: _expirationDate == null
                    ? null
                    : IconButton(
                        tooltip: l10n.clearDate,
                        icon: const Icon(Icons.clear),
                        onPressed: () => setState(() => _expirationDate = null),
                      ),
                onTap: _pickExpiration,
              ),
              const SizedBox(height: 8),
              FilledButton(onPressed: _apply, child: Text(l10n.save)),
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
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: Theme.of(context).textTheme.labelMedium),
            Wrap(
              spacing: 8,
              children: [
                for (final (index, name) in names.indexed)
                  ActionChip(label: Text(name), onPressed: () => onPick(index)),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
