import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:freezify/app.dart';
import 'package:freezify/core/api_client.dart';
import 'package:freezify/core/providers.dart';

import 'fake_backend.dart';

/// Starts the whole application against [backend], as a device set to [deviceLocale] would.
Future<void> pumpFreezify(
  WidgetTester tester,
  FakeBackend backend,
  InMemoryTokenStorage storage, {
  Locale deviceLocale = const Locale('es'),
}) async {
  // The app resolves its language from the list of preferred locales, not from the single locale.
  tester.platformDispatcher.localesTestValue = [deviceLocale];
  addTearDown(tester.platformDispatcher.clearLocalesTestValue);
  await tester.pumpWidget(
    ProviderScope(
      retry: (_, _) => null,
      overrides: [
        apiClientProvider.overrideWithValue(
          ApiClient(baseUrl: FakeBackend.baseUrl, storage: storage, adapter: backend),
        ),
      ],
      child: const FreezifyApp(),
    ),
  );
  await tester.pumpAndSettle();
}
