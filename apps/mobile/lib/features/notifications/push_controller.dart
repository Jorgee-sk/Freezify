import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../auth/auth_controller.dart';
import 'notification_repository.dart';
import 'push_messaging.dart';

/// Keeps the backend informed of where the signed-in user receives pushes, and reacts to the pushes
/// themselves.
///
/// While someone is signed in, this installation is registered under their name. When they sign out, or their
/// session ends on its own, the installation's token is thrown away: a phone must never show the food of
/// whoever used it before.
class PushController {
  PushController(this._ref);

  final Ref _ref;
  final _opens = StreamController<void>.broadcast();

  final _subscriptions = <StreamSubscription<void>>[];
  String? _userId;
  String? _token;

  PushMessaging get _messaging => _ref.read(pushMessagingProvider);
  NotificationRepository get _repository => _ref.read(notificationRepositoryProvider);

  /// The user asked, by tapping a push, to see their notifications.
  Stream<void> get opens => _opens.stream;

  /// Called with the id of the signed-in user every time it changes; `null` when nobody is signed in.
  Future<void> userChanged(String? userId) async {
    if (userId == _userId) return;
    final wasSignedIn = _userId != null;
    _userId = userId;
    if (wasSignedIn) await _stop();
    if (userId != null) await _start(userId);
  }

  /// Tells the backend to stop sending here, while there is still a session to say it with. Signing out
  /// works without it (the token is thrown away afterwards), so a failure is ignored.
  Future<void> unregister() async {
    final token = _token;
    if (token == null) return;
    try {
      await _repository.unregisterDevice(token);
    } catch (_) {}
  }

  Future<void> _start(String userId) async {
    final token = await _messaging.start();
    // The user may have changed while the system dialog was open.
    if (token == null || _userId != userId) return;
    await _register(token);
    _subscriptions
      ..add(_messaging.tokenChanges.listen(_register))
      ..add(_messaging.received.listen((_) => _refresh()))
      ..add(
        _messaging.taps.listen((_) {
          _refresh();
          _opens.add(null);
        }),
      );
  }

  Future<void> _stop() async {
    for (final subscription in _subscriptions) {
      await subscription.cancel();
    }
    _subscriptions.clear();
    if (_token == null) return;
    _token = null;
    await _messaging.forget();
  }

  Future<void> _register(String token) async {
    if (_userId == null) return;
    _token = token;
    try {
      await _repository.registerDevice(token);
    } catch (error) {
      // Not being registered only costs pushes until the next start; the app goes on.
      debugPrint('Could not register this device for push notifications: $error');
    }
  }

  void _refresh() {
    _ref.invalidate(unreadNotificationsProvider);
    _ref.invalidate(notificationsProvider);
  }

  void dispose() {
    for (final subscription in _subscriptions) {
      subscription.cancel();
    }
    _opens.close();
  }
}

final pushControllerProvider = Provider<PushController>((ref) {
  final controller = PushController(ref);
  ref.onDispose(controller.dispose);
  ref.listen(
    authControllerProvider.select((auth) => auth.value?.id),
    (_, userId) => controller.userChanged(userId),
    fireImmediately: true,
  );
  return controller;
});
