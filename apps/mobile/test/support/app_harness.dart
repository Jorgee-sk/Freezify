import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:freezify/app.dart';
import 'package:freezify/core/api_client.dart';
import 'package:freezify/core/providers.dart';
import 'package:freezify/features/notifications/push_messaging.dart';
import 'package:freezify/features/scanning/receipt_ocr.dart';

import 'fake_backend.dart';

/// Starts the whole application against [backend], as a device set to [deviceLocale] would.
Future<void> pumpFreezify(
  WidgetTester tester,
  FakeBackend backend,
  InMemoryTokenStorage storage, {
  Locale deviceLocale = const Locale('es'),
  FakePushMessaging? push,
  FakeReceiptOcr? ocr,
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
        // Never the real push service; unless a test says otherwise, this device cannot receive pushes.
        pushMessagingProvider.overrideWithValue(push ?? FakePushMessaging()),
        // Never the real camera and OCR; unless a test says otherwise, photos cannot be read on this device.
        receiptOcrProvider.overrideWithValue(ocr ?? FakeReceiptOcr()),
      ],
      child: const FreezifyApp(),
    ),
  );
  await tester.pumpAndSettle();
}

/// Stands in for the push service of the device.
class FakePushMessaging implements PushMessaging {
  FakePushMessaging({this.token});

  /// What [start] answers; null means pushes are not possible on this device.
  String? token;

  final _tokenChanges = StreamController<String>.broadcast();
  final _received = StreamController<void>.broadcast();
  final _taps = StreamController<void>.broadcast();

  int starts = 0;
  int forgotten = 0;

  @override
  Future<String?> start() async {
    starts++;
    return token;
  }

  @override
  Stream<String> get tokenChanges => _tokenChanges.stream;

  @override
  Stream<void> get received => _received.stream;

  @override
  Stream<void> get taps => _taps.stream;

  @override
  Future<void> forget() async => forgotten++;

  /// The push service replaces the token of this installation.
  void replaceToken(String token) => _tokenChanges.add(token);

  /// A notification arrives while the app is on screen.
  void receive() => _received.add(null);

  /// The user taps a notification.
  void tap() => _taps.add(null);
}

/// Stands in for the camera and the on-device OCR.
class FakeReceiptOcr implements ReceiptOcr {
  FakeReceiptOcr({this.available = false, this.text});

  @override
  final bool available;

  /// What reading a photo gives; null means the person cancelled.
  String? text;

  final List<PhotoSource> reads = [];

  @override
  Future<String?> read(PhotoSource source) async {
    reads.add(source);
    return text;
  }
}
