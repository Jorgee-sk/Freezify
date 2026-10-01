import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Where the refresh token is kept between app launches.
abstract interface class TokenStorage {
  Future<String?> read();
  Future<void> write(String refreshToken);
  Future<void> delete();
}

/// Android Keystore / iOS Keychain.
class SecureTokenStorage implements TokenStorage {
  const SecureTokenStorage([this._storage = const FlutterSecureStorage()]);

  static const _key = 'freezify.refreshToken';

  final FlutterSecureStorage _storage;

  @override
  Future<String?> read() => _storage.read(key: _key);

  @override
  Future<void> write(String refreshToken) => _storage.write(key: _key, value: refreshToken);

  @override
  Future<void> delete() => _storage.delete(key: _key);
}
