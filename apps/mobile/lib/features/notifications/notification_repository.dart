import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../../core/providers.dart';
import '../auth/auth_controller.dart';
import 'notification_models.dart';

class NotificationRepository {
  const NotificationRepository(this._api);

  /// The screen shows the most recent notifications only; older ones stay on the server.
  static const pageSize = 50;

  final ApiClient _api;

  Future<List<AppNotification>> latest() async {
    final json = await _api.get('/notifications?size=$pageSize') as Map<String, dynamic>;
    return [for (final item in json['items'] as List<dynamic>) AppNotification.fromJson(item as Map<String, dynamic>)];
  }

  Future<int> unreadCount() async {
    final json = await _api.get('/notifications/unread-count') as Map<String, dynamic>;
    return json['count'] as int;
  }

  Future<void> markRead(String id) => _api.post('/notifications/$id/read');

  Future<void> markAllRead() => _api.post('/notifications/read-all');

  /// Only Android is configured in the Firebase project, so that is the only platform that registers.
  Future<void> registerDevice(String token) =>
      _api.put('/notifications/devices', body: {'token': token, 'platform': 'ANDROID'});

  Future<void> unregisterDevice(String token) => _api.post('/notifications/devices/unregister', body: {'token': token});

  Future<NotificationPreferences> preferences() async =>
      NotificationPreferences.fromJson(await _api.get('/notifications/preferences') as Map<String, dynamic>);

  Future<NotificationPreferences> updatePreferences(NotificationPreferences preferences) async =>
      NotificationPreferences.fromJson(
        await _api.put('/notifications/preferences', body: preferences.toJson()) as Map<String, dynamic>,
      );
}

final notificationRepositoryProvider = Provider<NotificationRepository>(
  (ref) => NotificationRepository(ref.watch(apiClientProvider)),
);

// These depend on the signed-in user so that nothing cached survives a change of account.
final notificationsProvider = FutureProvider.autoDispose<List<AppNotification>>((ref) {
  ref.watch(authControllerProvider.select((auth) => auth.value?.id));
  return ref.watch(notificationRepositoryProvider).latest();
});

final unreadNotificationsProvider = FutureProvider.autoDispose<int>((ref) {
  ref.watch(authControllerProvider.select((auth) => auth.value?.id));
  return ref.watch(notificationRepositoryProvider).unreadCount();
});

final notificationPreferencesProvider = FutureProvider.autoDispose<NotificationPreferences>((ref) {
  ref.watch(authControllerProvider.select((auth) => auth.value?.id));
  return ref.watch(notificationRepositoryProvider).preferences();
});
