import 'package:dio/dio.dart';

import 'token_storage.dart';

/// A failed API call. [code] is the stable identifier the UI translates.
class ApiException implements Exception {
  const ApiException(this.status, this.code, [this.detail = '']);

  /// The request never got a response (offline, backend down, timeout).
  const ApiException.network() : this(0, networkCode);

  static const networkCode = 'NETWORK';

  final int status;
  final String code;
  final String detail;

  @override
  String toString() => 'ApiException($status, $code)';
}

/// HTTP access to the Freezify backend, including transparent session renewal.
class ApiClient {
  ApiClient({
    required String baseUrl,
    required this._storage,
    HttpClientAdapter? adapter,
    Duration? timeout,
  }) : _dio = Dio(
         BaseOptions(
           baseUrl: baseUrl,
           connectTimeout: timeout,
           receiveTimeout: timeout,
           // Status codes are handled here, not through exceptions.
           validateStatus: (_) => true,
           headers: {Headers.acceptHeader: Headers.jsonContentType},
         ),
       ) {
    if (adapter != null) _dio.httpClientAdapter = adapter;
  }

  final Dio _dio;
  final TokenStorage _storage;

  // The access token lives only in memory; the refresh token is in secure storage.
  String? _accessToken;
  Future<bool>? _renewal;

  /// Called when the session can no longer be renewed and the user must sign in again.
  void Function()? onSessionExpired;

  Future<bool> hasStoredSession() async => await _storage.read() != null;

  Future<void> storeSession({required String accessToken, required String refreshToken}) async {
    _accessToken = accessToken;
    await _storage.write(refreshToken);
  }

  Future<String?> clearSession() async {
    final refreshToken = await _storage.read();
    _accessToken = null;
    await _storage.delete();
    return refreshToken;
  }

  Future<dynamic> get(String path) => _request('GET', path);

  Future<dynamic> post(String path, {Object? body, bool authenticated = true}) =>
      _request('POST', path, body: body, authenticated: authenticated);

  Future<dynamic> put(String path, {Object? body}) => _request('PUT', path, body: body);

  Future<dynamic> patch(String path, {Object? body}) => _request('PATCH', path, body: body);

  Future<dynamic> delete(String path) => _request('DELETE', path);

  Future<dynamic> _request(String method, String path, {Object? body, bool authenticated = true}) async {
    // After an app restart only the refresh token is left: renew first instead of provoking a 401.
    if (authenticated && _accessToken == null && await hasStoredSession()) {
      await _renewSession();
    }

    var response = await _send(method, path, body, authenticated);
    if (response.statusCode == 401 && authenticated) {
      if (await _renewSession()) {
        response = await _send(method, path, body, authenticated);
      }
      if (response.statusCode == 401) {
        await clearSession();
        onSessionExpired?.call();
      }
    }

    final status = response.statusCode ?? 0;
    if (status >= 400) throw _toException(response);
    return response.data;
  }

  Future<Response<dynamic>> _send(String method, String path, Object? body, bool authenticated) async {
    try {
      return await _dio.request<dynamic>(
        path,
        data: body,
        options: Options(
          method: method,
          headers: {if (authenticated && _accessToken != null) 'Authorization': 'Bearer $_accessToken'},
        ),
      );
    } on DioException {
      throw const ApiException.network();
    }
  }

  /// Refresh tokens are single use and the backend revokes the whole session when one is presented twice,
  /// so concurrent requests must share a single refresh.
  Future<bool> _renewSession() {
    return _renewal ??= _refresh().whenComplete(() => _renewal = null);
  }

  Future<bool> _refresh() async {
    final refreshToken = await _storage.read();
    if (refreshToken == null) return false;
    final response = await _send('POST', '/auth/refresh', {'refreshToken': refreshToken}, false);
    final data = response.data;
    if (response.statusCode != 200 || data is! Map) return false;
    await storeSession(
      accessToken: data['accessToken'] as String,
      refreshToken: data['refreshToken'] as String,
    );
    return true;
  }

  ApiException _toException(Response<dynamic> response) {
    final data = response.data;
    final status = response.statusCode ?? 0;
    if (data is Map && data['code'] is String) {
      return ApiException(status, data['code'] as String, (data['detail'] as String?) ?? '');
    }
    return ApiException(status, 'UNKNOWN');
  }
}
