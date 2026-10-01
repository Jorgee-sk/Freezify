import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';

/// Shared layout of the sign-in and sign-up screens.
class AuthScaffold extends StatelessWidget {
  const AuthScaffold({
    super.key,
    required this.title,
    required this.formKey,
    required this.fields,
    required this.submitLabel,
    required this.onSubmit,
    required this.switchLabel,
    required this.onSwitch,
    this.error,
  });

  final String title;
  final GlobalKey<FormState> formKey;
  final List<Widget> fields;
  final String submitLabel;

  /// `null` while a submission is in progress.
  final VoidCallback? onSubmit;
  final String switchLabel;
  final VoidCallback onSwitch;
  final String? error;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: AutofillGroup(
                child: Form(
                  key: formKey,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Icon(Icons.eco_rounded, size: 56, color: theme.colorScheme.primary),
                      const SizedBox(height: 16),
                      Text(title, style: theme.textTheme.headlineMedium, textAlign: TextAlign.center),
                      const SizedBox(height: 8),
                      Text(
                        l10n.appTagline,
                        style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 32),
                      for (final field in fields) ...[field, const SizedBox(height: 16)],
                      if (error != null) ...[
                        Semantics(
                          liveRegion: true,
                          child: Text(error!, style: TextStyle(color: theme.colorScheme.error)),
                        ),
                        const SizedBox(height: 16),
                      ],
                      FilledButton(onPressed: onSubmit, child: Text(submitLabel)),
                      const SizedBox(height: 8),
                      TextButton(onPressed: onSwitch, child: Text(switchLabel)),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
