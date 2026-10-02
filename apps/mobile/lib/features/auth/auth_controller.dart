import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../../core/providers.dart';
import '../notifications/push_controller.dart';

class User {
  const User({required this.id, required this.email, required this.displayName, required this.locale});

  factory User.fromJson(Map<String, dynamic> json) => User(
    id: json['id'] as String,
    email: json['email'] as String,
    displayName: json['displayName'] as String,
    locale: json['locale'] as String,
  );

  final String id;
  final String email;
  final String displayName;
  final String locale;
}

/// The signed-in user, or `null` when nobody is signed in.
final authControllerProvider = AsyncNotifierProvider<AuthController, User?>(AuthController.new);

class AuthController extends AsyncNotifier<User?> {
  ApiClient get _api => ref.read(apiClientProvider);

  @override
  Future<User?> build() async {
    final api = ref.watch(apiClientProvider);
    api.onSessionExpired = () => state = const AsyncData(null);
    ref.onDispose(() => api.onSessionExpired = null);

    if (!await api.hasStoredSession()) return null;
    try {
      return User.fromJson(await api.get('/users/me') as Map<String, dynamic>);
    } on ApiException catch (error) {
      // Offline at launch must not sign the user out; an invalid session already cleared itself.
      if (error.code == ApiException.networkCode) rethrow;
      return null;
    }
  }

  Future<void> login({required String email, required String password}) async {
    await _startSession(
      await _api.post('/auth/login', body: {'email': email, 'password': password}, authenticated: false),
    );
  }

  Future<void> register({
    required String email,
    required String password,
    required String displayName,
    required String locale,
  }) async {
    await _startSession(
      await _api.post(
        '/auth/register',
        body: {'email': email, 'password': password, 'displayName': displayName, 'locale': locale},
        authenticated: false,
      ),
    );
  }

  Future<void> logout() async {
    // While there is still a session to say it with: this phone stops receiving the pushes of this user.
    await ref.read(pushControllerProvider).unregister();
    final refreshToken = await _api.clearSession();
    state = const AsyncData(null);
    if (refreshToken == null) return;
    try {
      await _api.post('/auth/logout', body: {'refreshToken': refreshToken}, authenticated: false);
    } on ApiException {
      // Best effort: the local session is gone either way.
    }
  }

  Future<void> changeLocale(String locale) async {
    final json = await _api.patch('/users/me', body: {'locale': locale});
    state = AsyncData(User.fromJson(json as Map<String, dynamic>));
  }

  Future<void> _startSession(dynamic json) async {
    final session = json as Map<String, dynamic>;
    await _api.storeSession(
      accessToken: session['accessToken'] as String,
      refreshToken: session['refreshToken'] as String,
    );
    state = AsyncData(User.fromJson(session['user'] as Map<String, dynamic>));
  }
}
