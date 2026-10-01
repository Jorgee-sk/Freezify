import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/errors.dart';
import '../../l10n/app_localizations.dart';

/// Asks for one line of text. Returns `null` when cancelled.
Future<String?> promptText(
  BuildContext context, {
  required String title,
  required String label,
  required String confirmLabel,
  String initialValue = '',
  int maxLength = 80,
  TextCapitalization capitalization = TextCapitalization.sentences,
}) {
  return showDialog<String>(
    context: context,
    builder: (context) => _TextPrompt(
      title: title,
      label: label,
      confirmLabel: confirmLabel,
      initialValue: initialValue,
      maxLength: maxLength,
      capitalization: capitalization,
    ),
  );
}

class _TextPrompt extends StatefulWidget {
  const _TextPrompt({
    required this.title,
    required this.label,
    required this.confirmLabel,
    required this.initialValue,
    required this.maxLength,
    required this.capitalization,
  });

  final String title;
  final String label;
  final String confirmLabel;
  final String initialValue;
  final int maxLength;
  final TextCapitalization capitalization;

  @override
  State<_TextPrompt> createState() => _TextPromptState();
}

class _TextPromptState extends State<_TextPrompt> {
  late final _controller = TextEditingController(text: widget.initialValue);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _confirm() {
    final value = _controller.text.trim();
    if (value.isNotEmpty) Navigator.of(context).pop(value);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return AlertDialog(
      title: Text(widget.title),
      content: TextField(
        controller: _controller,
        autofocus: true,
        decoration: InputDecoration(labelText: widget.label),
        textCapitalization: widget.capitalization,
        inputFormatters: [LengthLimitingTextInputFormatter(widget.maxLength)],
        onSubmitted: (_) => _confirm(),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(), child: Text(l10n.cancel)),
        FilledButton.tonal(onPressed: _confirm, child: Text(widget.confirmLabel)),
      ],
    );
  }
}

/// Every destructive action goes through this confirmation.
Future<bool> confirmDestructive(BuildContext context, {required String message, required String confirmLabel}) async {
  final l10n = AppLocalizations.of(context);
  final confirmed = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      content: Text(message),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(false), child: Text(l10n.cancel)),
        TextButton(
          onPressed: () => Navigator.of(context).pop(true),
          style: TextButton.styleFrom(foregroundColor: Theme.of(context).colorScheme.error),
          child: Text(confirmLabel),
        ),
      ],
    ),
  );
  return confirmed ?? false;
}

void showError(BuildContext context, Object error) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(errorMessage(AppLocalizations.of(context), error))));
}
