import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'api_client.dart';
import 'token_storage.dart';

/// Override at build time: `--dart-define=FREEZIFY_API_URL=https://api.example.com/api/v1`.
/// The default reaches a backend on the development machine from the Android emulator.
const apiBaseUrl = String.fromEnvironment('FREEZIFY_API_URL', defaultValue: 'http://10.0.2.2:8080/api/v1');

final apiClientProvider = Provider<ApiClient>((ref) {
  return ApiClient(
    baseUrl: apiBaseUrl,
    storage: const SecureTokenStorage(),
    timeout: const Duration(seconds: 15),
  );
});
