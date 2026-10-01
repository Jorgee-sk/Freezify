import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import 'inventory_format.dart';
import 'inventory_models.dart';

typedef TakeOut = ({Quantity quantity, WasteReason reason});

/// Asks how much of [item] was consumed or thrown away. Returns `null` when cancelled.
Future<TakeOut?> showTakeOutSheet(BuildContext context, {required InventoryItem item, required bool discard}) {
  return showModalBottomSheet<TakeOut>(
    context: context,
    isScrollControlled: true,
    builder: (_) => _TakeOutSheet(item: item, discard: discard),
  );
}

class _TakeOutSheet extends StatefulWidget {
  const _TakeOutSheet({required this.item, required this.discard});

  final InventoryItem item;
  final bool discard;

  @override
  State<_TakeOutSheet> createState() => _TakeOutSheetState();
}

class _TakeOutSheetState extends State<_TakeOutSheet> {
  TextEditingController? _amountController;
  late Unit _unit = widget.item.quantity.unit;
  WasteReason _reason = WasteReason.expired;
  bool _invalid = false;

  /// Starts with everything that is left: the common case, and a smaller amount is one edit away.
  /// Created on first use because formatting the number needs the language of the context.
  TextEditingController get _amount => _amountController ??= TextEditingController(
    text: formatAmount(widget.item.quantity.amount, Localizations.localeOf(context).languageCode),
  );

  @override
  void dispose() {
    _amountController?.dispose();
    super.dispose();
  }

  void _confirm() {
    final amount = parseAmount(_amount.text);
    if (amount == null) {
      setState(() => _invalid = true);
      return;
    }
    Navigator.of(context).pop((quantity: Quantity(amount, _unit), reason: _reason));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return SafeArea(
      child: Padding(
        // Stays above the keyboard.
        padding: EdgeInsets.fromLTRB(16, 16, 16, 16 + MediaQuery.viewInsetsOf(context).bottom),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(widget.discard ? l10n.discardTitle : l10n.consumeTitle, style: theme.textTheme.titleMedium),
            Text(widget.item.name, style: TextStyle(color: theme.colorScheme.onSurfaceVariant)),
            const SizedBox(height: 16),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: TextField(
                    controller: _amount,
                    autofocus: true,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: InputDecoration(
                      labelText: l10n.amount,
                      errorText: _invalid ? l10n.invalidAmount : null,
                    ),
                    onSubmitted: (_) => _confirm(),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: DropdownButtonFormField<Unit>(
                    initialValue: _unit,
                    decoration: InputDecoration(labelText: l10n.unit),
                    items: [
                      for (final unit in widget.item.quantity.unit.compatible)
                        DropdownMenuItem(value: unit, child: Text(unitName(l10n, unit))),
                    ],
                    onChanged: (value) => setState(() => _unit = value ?? _unit),
                  ),
                ),
              ],
            ),
            if (widget.discard) ...[
              const SizedBox(height: 16),
              DropdownButtonFormField<WasteReason>(
                initialValue: _reason,
                decoration: InputDecoration(labelText: l10n.reason),
                items: [
                  for (final reason in WasteReason.values)
                    DropdownMenuItem(value: reason, child: Text(reasonName(l10n, reason))),
                ],
                onChanged: (value) => setState(() => _reason = value ?? _reason),
              ),
            ],
            const SizedBox(height: 16),
            FilledButton(onPressed: _confirm, child: Text(l10n.confirm)),
          ],
        ),
      ),
    );
  }
}
