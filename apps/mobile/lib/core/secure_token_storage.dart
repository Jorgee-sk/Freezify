import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'token_storage.dart';

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
