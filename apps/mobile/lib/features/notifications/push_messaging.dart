import 'dart:async';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// The push service of the device, reduced to what the app needs from it. The rest of the app never talks to
/// Firebase directly, so tests can stand in for it.
abstract interface class PushMessaging {
  /// Gets the app ready to receive pushes and asks the user for permission if needed.
  ///
  /// Returns the token that identifies this installation, or `null` when pushes are not possible here: the
  /// build has no Firebase configuration, the platform is not supported, or the user said no.
  Future<String?> start();

  /// The push service replaced the token of this installation.
  Stream<String> get tokenChanges;

  /// A notification arrived while the app was on screen. The system does not show it in that case.
  Stream<void> get received;

  /// The user tapped a notification, including the one that opened the app.
  Stream<void> get taps;

  /// Makes the current token useless, so that nothing more is delivered to this installation until [start]
  /// is called again. Works without a session, which is why it is what signing out relies on.
  Future<void> forget();
}

/// Firebase Cloud Messaging. Every failure ends in "no pushes": the app must work the same without them.
class FirebasePushMessaging implements PushMessaging {
  final _taps = StreamController<void>.broadcast();
  bool _ready = false;
  bool _launchedByTap = false;

  @override
  Future<String?> start() async {
    // Only Android is configured in the Firebase project so far.
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return null;
    try {
      if (!_ready) {
        // Reads the configuration that google-services.json put into the build; throws when there is none.
        await Firebase.initializeApp();
        FirebaseMessaging.onMessageOpenedApp.listen((_) => _taps.add(null));
        _ready = true;
        // The notification that opened the app, if any, counts as a tap once somebody listens.
        _launchedByTap = await FirebaseMessaging.instance.getInitialMessage() != null;
      }
      final settings = await FirebaseMessaging.instance.requestPermission();
      if (settings.authorizationStatus == AuthorizationStatus.denied) return null;
      return await FirebaseMessaging.instance.getToken();
    } catch (error) {
      debugPrint('Push notifications are not available: $error');
      return null;
    }
  }

  @override
  Stream<String> get tokenChanges => _ready ? FirebaseMessaging.instance.onTokenRefresh : const Stream.empty();

  @override
  Stream<void> get received => _ready ? FirebaseMessaging.onMessage.map((_) {}) : const Stream.empty();

  @override
  Stream<void> get taps async* {
    if (_launchedByTap) {
      _launchedByTap = false;
      yield null;
    }
    yield* _taps.stream;
  }

  @override
  Future<void> forget() async {
    if (!_ready) return;
    try {
      await FirebaseMessaging.instance.deleteToken();
    } catch (error) {
      debugPrint('Could not delete the push token: $error');
    }
  }
}

final pushMessagingProvider = Provider<PushMessaging>((ref) => FirebasePushMessaging());
