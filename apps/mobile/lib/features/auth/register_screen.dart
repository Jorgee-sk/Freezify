import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/errors.dart';
import '../../l10n/app_localizations.dart';
import 'auth_controller.dart';
import 'auth_scaffold.dart';

class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({super.key});

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  static const _minPasswordLength = 8;

  final _formKey = GlobalKey<FormState>();
  final _displayName = TextEditingController();
  final _email = TextEditingController();
  final _password = TextEditingController();
  Object? _error;
  bool _submitting = false;

  @override
  void dispose() {
    _displayName.dispose();
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    // The account starts in the language the user is reading right now.
    final locale = Localizations.localeOf(context).languageCode;
    setState(() {
      _error = null;
      _submitting = true;
    });
    try {
      await ref
          .read(authControllerProvider.notifier)
          .register(
            email: _email.text.trim(),
            password: _password.text,
            displayName: _displayName.text.trim(),
            locale: locale,
          );
    } catch (error) {
      if (mounted) {
        setState(() {
          _error = error;
          _submitting = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    String? required(String? value) => (value == null || value.trim().isEmpty) ? l10n.fieldRequired : null;

    return AuthScaffold(
      title: l10n.registerTitle,
      formKey: _formKey,
      error: _error == null ? null : errorMessage(l10n, _error!),
      fields: [
        TextFormField(
          controller: _displayName,
          decoration: InputDecoration(labelText: l10n.displayName),
          textCapitalization: TextCapitalization.words,
          autofillHints: const [AutofillHints.givenName],
          textInputAction: TextInputAction.next,
          maxLength: 80,
          validator: required,
        ),
        TextFormField(
          controller: _email,
          decoration: InputDecoration(labelText: l10n.email),
          keyboardType: TextInputType.emailAddress,
          autofillHints: const [AutofillHints.email],
          textInputAction: TextInputAction.next,
          validator: required,
        ),
        TextFormField(
          controller: _password,
          decoration: InputDecoration(labelText: l10n.password, helperText: l10n.passwordHint),
          obscureText: true,
          autofillHints: const [AutofillHints.newPassword],
          onFieldSubmitted: (_) => _submit(),
          validator: (value) =>
              (value == null || value.length < _minPasswordLength) ? l10n.passwordTooShort : null,
        ),
      ],
      submitLabel: l10n.registerAction,
      onSubmit: _submitting ? null : _submit,
      switchLabel: l10n.haveAccount,
      onSwitch: () => context.go('/login'),
    );
  }
}
