import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/errors.dart';
import '../../l10n/app_localizations.dart';
import 'auth_controller.dart';
import 'auth_scaffold.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  Object? _error;
  bool _submitting = false;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _error = null;
      _submitting = true;
    });
    try {
      await ref.read(authControllerProvider.notifier).login(email: _email.text.trim(), password: _password.text);
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
      title: l10n.loginTitle,
      formKey: _formKey,
      error: _error == null ? null : errorMessage(l10n, _error!),
      fields: [
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
          decoration: InputDecoration(labelText: l10n.password),
          obscureText: true,
          autofillHints: const [AutofillHints.password],
          onFieldSubmitted: (_) => _submit(),
          validator: required,
        ),
      ],
      submitLabel: l10n.loginAction,
      onSubmit: _submitting ? null : _submit,
      switchLabel: l10n.noAccount,
      onSwitch: () => context.go('/register'),
    );
  }
}
