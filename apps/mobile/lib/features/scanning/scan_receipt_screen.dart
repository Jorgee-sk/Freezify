import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../core/errors.dart';
import '../../l10n/app_localizations.dart';
import '../households/dialogs.dart';
import '../inventory/inventory_format.dart';
import 'receipt_line_sheet.dart';
import 'receipt_ocr.dart';
import 'scan_models.dart';
import 'scan_repository.dart';

/// Scanning a receipt: a photo is read on the phone (or a digital receipt is pasted), the backend turns the text
/// into products, and the person reviews them before anything reaches the inventory.
class ScanReceiptScreen extends ConsumerStatefulWidget {
  const ScanReceiptScreen({super.key, required this.householdId});

  final String householdId;

  @override
  ConsumerState<ScanReceiptScreen> createState() => _ScanReceiptScreenState();
}

class _ScanReceiptScreenState extends ConsumerState<ScanReceiptScreen> {
  final _pasted = TextEditingController();
  ReceiptDraft? _draft;
  String? _purchaseDate;
  String? _notice;
  Object? _error;
  bool _busy = false;

  String get _language => Localizations.localeOf(context).languageCode;

  @override
  void dispose() {
    _pasted.dispose();
    super.dispose();
  }

  Future<void> _scanPhoto(PhotoSource source) async {
    final l10n = AppLocalizations.of(context);
    setState(() {
      _busy = true;
      _notice = null;
      _error = null;
    });
    try {
      final text = await ref.read(receiptOcrProvider).read(source);
      if (!mounted) return;
      if (text == null) {
        setState(() => _busy = false);
        return;
      }
      if (text.trim().isEmpty) {
        setState(() {
          _busy = false;
          _notice = l10n.scanNothingRead;
        });
        return;
      }
      await _read(text);
    } catch (error) {
      // The camera or the OCR failed on this device; pasting the text still works.
      if (mounted) {
        setState(() {
          _busy = false;
          _notice = l10n.scanNothingRead;
        });
      }
    }
  }

  Future<void> _read(String text) async {
    final l10n = AppLocalizations.of(context);
    setState(() {
      _busy = true;
      _notice = null;
      _error = null;
    });
    try {
      final draft = await ref.read(scanRepositoryProvider).read(widget.householdId, text, _language);
      if (!mounted) return;
      setState(() {
        _busy = false;
        if (draft.lines.isEmpty) {
          _notice = l10n.scanNoProducts;
        } else {
          _draft = draft;
          _purchaseDate = draft.purchaseDate;
        }
      });
    } catch (error) {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = error;
        });
      }
    }
  }

  Future<void> _confirm() async {
    final draft = _draft!;
    final lines = [
      for (final line in draft.lines)
        if (line.include) line,
    ];
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final stocked = await ref.read(scanRepositoryProvider).confirm(widget.householdId, _purchaseDate!, lines);
      if (!mounted) return;
      final l10n = AppLocalizations.of(context);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.scanAdded(stocked))));
      _close(stocked: true);
    } catch (error) {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = error;
        });
      }
    }
  }

  Future<void> _pickPurchaseDate() async {
    final current = parseIsoDay(_purchaseDate!);
    final today = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: current.isAfter(today) ? today : current,
      firstDate: DateTime(today.year - 1),
      lastDate: today,
    );
    if (picked != null) setState(() => _purchaseDate = toIsoDay(picked));
  }

  Future<void> _edit(DraftLine line) async {
    if (await editReceiptLine(context, line) && mounted) setState(() {});
  }

  /// Leaving while reviewing loses the review: ask first.
  Future<void> _leave() async {
    final l10n = AppLocalizations.of(context);
    final confirmed = await confirmDestructive(
      context,
      message: l10n.scanDiscardConfirm,
      confirmLabel: l10n.scanDiscard,
    );
    if (confirmed && mounted) _close(stocked: false);
  }

  /// Leaves once the screen no longer holds a review, so that leaving is not taken for a half-done review.
  /// Says whether anything reached the inventory, so the inventory refreshes.
  void _close({required bool stocked}) {
    setState(() => _draft = null);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) context.pop(stocked);
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final draft = _draft;
    return PopScope(
      canPop: draft == null,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _leave();
      },
      child: Scaffold(
        appBar: AppBar(title: Text(l10n.scanReceipt)),
        body: draft == null ? _capture(l10n) : _review(l10n, draft),
      ),
    );
  }

  Widget _capture(AppLocalizations l10n) {
    final theme = Theme.of(context);
    final ocr = ref.watch(receiptOcrProvider);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (ocr.available) ...[
          Text(l10n.scanIntro, style: theme.textTheme.bodyLarge),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: _busy ? null : () => _scanPhoto(PhotoSource.camera),
            icon: const Icon(Icons.photo_camera_outlined),
            label: Text(l10n.scanTakePhoto),
          ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: _busy ? null : () => _scanPhoto(PhotoSource.gallery),
            icon: const Icon(Icons.photo_library_outlined),
            label: Text(l10n.scanPickPhoto),
          ),
        ] else
          Text(l10n.scanNoPhotosHere, style: TextStyle(color: theme.colorScheme.onSurfaceVariant)),
        if (_busy) ...[const SizedBox(height: 16), const LinearProgressIndicator()],
        if (_notice != null || _error != null) ...[
          const SizedBox(height: 16),
          Semantics(
            liveRegion: true,
            child: Text(
              _error != null ? errorMessage(l10n, _error!) : _notice!,
              style: TextStyle(color: theme.colorScheme.error),
            ),
          ),
        ],
        const SizedBox(height: 24),
        Text(l10n.scanPasteTitle, style: theme.textTheme.titleMedium),
        const SizedBox(height: 4),
        Text(l10n.scanPasteHelp, style: TextStyle(color: theme.colorScheme.onSurfaceVariant)),
        const SizedBox(height: 8),
        TextField(
          controller: _pasted,
          minLines: 4,
          maxLines: 10,
          maxLength: 20000,
          decoration: InputDecoration(labelText: l10n.scanReceiptText, counterText: ''),
          onChanged: (_) => setState(() {}),
        ),
        const SizedBox(height: 8),
        OutlinedButton(
          onPressed: _busy || _pasted.text.trim().isEmpty ? null : () => _read(_pasted.text),
          child: Text(l10n.scanReadText),
        ),
      ],
    );
  }

  Widget _review(AppLocalizations l10n, ReceiptDraft draft) {
    final theme = Theme.of(context);
    final included = draft.lines.where((line) => line.include).length;
    return Column(
      children: [
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
            children: [
              Text(
                draft.readBy == ReadBy.ai ? l10n.scanReadByAi : l10n.scanReadByRules,
                style: TextStyle(color: theme.colorScheme.onSurfaceVariant),
              ),
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.event_outlined),
                title: Text(l10n.scanPurchaseDate),
                subtitle: Text(
                  draft.purchaseDateFromReceipt && _purchaseDate == draft.purchaseDate
                      ? l10n.scanDateFromReceipt(formatDay(_purchaseDate!))
                      : _purchaseDate == draft.purchaseDate
                      ? l10n.scanDateNotOnReceipt(formatDay(_purchaseDate!))
                      : formatDay(_purchaseDate!),
                ),
                onTap: _busy ? null : _pickPurchaseDate,
              ),
              const Divider(),
              for (final line in draft.lines)
                _LineTile(line: line, onEdit: () => _edit(line), onChanged: () => setState(() {})),
              if (_error != null) ...[
                const SizedBox(height: 8),
                Semantics(
                  liveRegion: true,
                  child: Text(errorMessage(l10n, _error!), style: TextStyle(color: theme.colorScheme.error)),
                ),
              ],
            ],
          ),
        ),
        SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (_busy) const LinearProgressIndicator(),
                FilledButton(
                  onPressed: _busy || included == 0 ? null : _confirm,
                  child: Text(l10n.scanAddToInventory(included)),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _LineTile extends StatelessWidget {
  const _LineTile({required this.line, required this.onEdit, required this.onChanged});

  final DraftLine line;
  final VoidCallback onEdit;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final quantity = formatQuantity(l10n, line.quantity);
    final details = line.reviewed
        ? [quantity, if (line.price != null) _price(l10n, line.price!), l10n.scanReviewed]
        : [
            line.quantityFromReceipt ? l10n.scanQuantityFromReceipt(quantity) : l10n.scanQuantityAssumed(quantity),
            if (line.price != null) _price(l10n, line.price!),
            switch (line.match) {
              MatchedBy.learned => l10n.scanMatchLearned,
              MatchedBy.alias || MatchedBy.name => l10n.scanMatchFound,
              MatchedBy.none => l10n.scanMatchNone,
            },
          ];
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: Checkbox(
        value: line.include,
        semanticLabel: l10n.scanInclude(line.name),
        onChanged: (value) {
          line.include = value ?? false;
          onChanged();
        },
      ),
      title: Text(line.name),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(line.text, style: theme.textTheme.bodySmall?.copyWith(fontFamily: 'monospace')),
          Text(details.join(' · ')),
          if (line.expirationDate != null) Text(l10n.scanExpiresOn(formatDay(line.expirationDate!))),
        ],
      ),
      trailing: IconButton(
        tooltip: l10n.scanEditLineOf(line.name),
        icon: const Icon(Icons.edit_outlined),
        onPressed: onEdit,
      ),
      onTap: onEdit,
    );
  }
}

String _price(AppLocalizations l10n, double price) => '${NumberFormat('0.00', l10n.localeName).format(price)} €';
