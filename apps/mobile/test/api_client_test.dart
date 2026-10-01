import 'package:flutter_test/flutter_test.dart';
import 'package:freezify/core/api_client.dart';

import 'support/fake_backend.dart';

const _user = {'id': 'u1', 'displayName': 'Ana'};
const _renewed = {'accessToken': 'access-2', 'refreshToken': 'refresh-2'};

FakeResponse _requiresRenewedToken(RecordedCall call, Object body) =>
    call.authorization == 'Bearer access-2' ? FakeResponse.ok(body) : FakeResponse.problem(401, 'UNAUTHORIZED');

void main() {
  late InMemoryTokenStorage storage;

  ApiClient clientFor(FakeBackend backend) =>
      ApiClient(baseUrl: FakeBackend.baseUrl, storage: storage, adapter: backend);

  setUp(() => storage = InMemoryTokenStorage());

  test('sends the access token and returns the decoded body', () async {
    final backend = FakeBackend({'GET /users/me': (_) => const FakeResponse.ok(_user)});
    final client = clientFor(backend);
    await client.storeSession(accessToken: 'access-1', refreshToken: 'refresh-1');

    expect(await client.get('/users/me'), _user);
    expect(backend.last('GET /users/me').authorization, 'Bearer access-1');
  });

  test('does not send credentials to unauthenticated endpoints', () async {
    final backend = FakeBackend({'POST /auth/login': (_) => const FakeResponse.ok({})});
    final client = clientFor(backend);
    await client.storeSession(accessToken: 'access-1', refreshToken: 'refresh-1');

    await client.post('/auth/login', body: {'email': 'a@b.c'}, authenticated: false);

    expect(backend.last('POST /auth/login').authorization, isNull);
    expect(backend.last('POST /auth/login').body, {'email': 'a@b.c'});
  });

  test('renews an expired session and retries the request', () async {
    final backend = FakeBackend({
      'GET /users/me': (call) => _requiresRenewedToken(call, _user),
      'POST /auth/refresh': (_) => const FakeResponse.ok(_renewed),
    });
    final client = clientFor(backend);
    await client.storeSession(accessToken: 'expired', refreshToken: 'refresh-1');

    expect(await client.get('/users/me'), _user);
    expect(backend.last('POST /auth/refresh').body, {'refreshToken': 'refresh-1'});
    expect(storage.value, 'refresh-2');
  });

  test('shares a single refresh between concurrent requests', () async {
    // Refresh tokens are single use: a second refresh with the same token would revoke the session.
    final backend = FakeBackend({
      'GET /users/me': (call) => _requiresRenewedToken(call, _user),
      'GET /households': (call) => _requiresRenewedToken(call, const <Object>[]),
      'POST /auth/refresh': (_) => const FakeResponse.ok(_renewed),
    });
    final client = clientFor(backend);
    await client.storeSession(accessToken: 'expired', refreshToken: 'refresh-1');

    await Future.wait([client.get('/users/me'), client.get('/households'), client.get('/users/me')]);

    expect(backend.count('POST /auth/refresh'), 1);
  });

  test('restores the session after a restart, when only the refresh token is left', () async {
    storage.value = 'refresh-1';
    final backend = FakeBackend({
      'GET /users/me': (_) => const FakeResponse.ok(_user),
      'POST /auth/refresh': (_) => const FakeResponse.ok(_renewed),
    });

    await clientFor(backend).get('/users/me');

    expect(backend.calls.map((call) => call.route), ['POST /auth/refresh', 'GET /users/me']);
    expect(backend.last('GET /users/me').authorization, 'Bearer access-2');
  });

  test('ends the session when it cannot be renewed', () async {
    final backend = FakeBackend({
      'GET /users/me': (_) => FakeResponse.problem(401, 'UNAUTHORIZED'),
      'POST /auth/refresh': (_) => FakeResponse.problem(401, 'INVALID_REFRESH_TOKEN'),
    });
    final client = clientFor(backend);
    await client.storeSession(accessToken: 'expired', refreshToken: 'revoked');
    var expired = 0;
    client.onSessionExpired = () => expired++;

    await expectLater(
      client.get('/users/me'),
      throwsA(isA<ApiException>().having((e) => e.code, 'code', 'UNAUTHORIZED')),
    );
    expect(expired, 1);
    expect(storage.value, isNull);
  });

  test('exposes the error code of a problem response', () async {
    final backend = FakeBackend({'POST /households/join': (_) => FakeResponse.problem(404, 'INVITATION_NOT_FOUND')});
    final client = clientFor(backend);
    await client.storeSession(accessToken: 'access-1', refreshToken: 'refresh-1');

    await expectLater(
      client.post('/households/join', body: {'code': 'X'}),
      throwsA(
        isA<ApiException>()
            .having((e) => e.status, 'status', 404)
            .having((e) => e.code, 'code', 'INVITATION_NOT_FOUND'),
      ),
    );
  });

  test('reports missing connectivity without ending the session', () async {
    final backend = FakeBackend({})..offline = true;
    final client = clientFor(backend);
    await client.storeSession(accessToken: 'access-1', refreshToken: 'refresh-1');

    await expectLater(
      client.get('/users/me'),
      throwsA(isA<ApiException>().having((e) => e.code, 'code', ApiException.networkCode)),
    );
    expect(storage.value, 'refresh-1');
  });
}
