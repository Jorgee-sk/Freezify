import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/app_harness.dart';
import 'support/fake_backend.dart';

const _ana = {'id': 'u1', 'email': 'ana@example.com', 'displayName': 'Ana', 'locale': 'es'};
const _session = {'accessToken': 'access-1', 'refreshToken': 'refresh-1', 'expiresIn': 900, 'user': _ana};
const _casa = {'id': 'h1', 'name': 'Casa', 'role': 'OWNER', 'memberCount': 2};

const _register = 'PUT /notifications/devices';
const _unregister = 'POST /notifications/devices/unregister';
const _unread = 'GET /notifications/unread-count';

void main() {
  late InMemoryTokenStorage storage;

  setUp(() => storage = InMemoryTokenStorage('refresh-1'));

  /// A backend with Ana signed in; [extra] adds or overrides routes.
  FakeBackend backendWith([Map<String, FakeHandler> extra = const {}]) => FakeBackend({
    'POST /auth/refresh': (_) => const FakeResponse.ok({'accessToken': 'access-2', 'refreshToken': 'refresh-2'}),
    'GET /users/me': (_) => const FakeResponse.ok(_ana),
    'GET /households': (_) => const FakeResponse.ok([_casa]),
    _register: (_) => const FakeResponse(204),
    _unregister: (_) => const FakeResponse(204),
    'POST /auth/logout': (_) => const FakeResponse(204),
    ...extra,
  });

  Future<void> signOut(WidgetTester tester) async {
    await tester.tap(find.byType(PopupMenuButton<String>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cerrar sesión'));
    await tester.pumpAndSettle();
  }

  testWidgets('registers this phone for the user who is signed in', (tester) async {
    final backend = backendWith();
    await pumpFreezify(tester, backend, storage, push: FakePushMessaging(token: 'device-1'));

    expect(find.text('Hola, Ana 👋'), findsOneWidget);
    expect(backend.count(_register), 1);
    expect(backend.last(_register).body, {'token': 'device-1', 'platform': 'ANDROID'});
    expect(backend.last(_register).authorization, 'Bearer access-2');
  });

  testWidgets('registers after signing in, not before', (tester) async {
    storage.value = null;
    final push = FakePushMessaging(token: 'device-1');
    final backend = backendWith({'POST /auth/login': (_) => const FakeResponse.ok(_session)});
    await pumpFreezify(tester, backend, storage, push: push);
    // Nobody is asked for permission, nor registered, on the sign-in screen.
    expect(push.starts, 0);
    expect(backend.count(_register), 0);

    await tester.enterText(find.widgetWithText(TextFormField, 'Correo electrónico'), 'ana@example.com');
    await tester.enterText(find.widgetWithText(TextFormField, 'Contraseña'), 'correct-horse');
    await tester.tap(find.widgetWithText(FilledButton, 'Entrar'));
    await tester.pumpAndSettle();

    expect(push.starts, 1);
    expect(backend.last(_register).body, {'token': 'device-1', 'platform': 'ANDROID'});
  });

  testWidgets('registers nothing when the phone cannot receive pushes', (tester) async {
    final backend = backendWith();
    await pumpFreezify(tester, backend, storage, push: FakePushMessaging());

    expect(find.text('Hola, Ana 👋'), findsOneWidget);
    expect(backend.count(_register), 0);
  });

  testWidgets('works the same when the registration fails', (tester) async {
    final backend = backendWith({_register: (_) => FakeResponse.problem(500, 'INTERNAL_ERROR')});
    await pumpFreezify(tester, backend, storage, push: FakePushMessaging(token: 'device-1'));

    expect(find.text('Hola, Ana 👋'), findsOneWidget);
    expect(find.text('Casa'), findsOneWidget);
    expect(backend.count(_register), 1);
  });

  testWidgets('registers again when the push service replaces the token', (tester) async {
    final push = FakePushMessaging(token: 'device-1');
    final backend = backendWith();
    await pumpFreezify(tester, backend, storage, push: push);

    push.replaceToken('device-2');
    await tester.pumpAndSettle();

    expect(backend.count(_register), 2);
    expect(backend.last(_register).body, {'token': 'device-2', 'platform': 'ANDROID'});
  });

  testWidgets('tapping a push opens the notifications', (tester) async {
    final push = FakePushMessaging(token: 'device-1');
    final backend = backendWith({
      'GET /notifications': (_) =>
          const FakeResponse.ok({'items': <Object>[], 'page': 0, 'size': 50, 'totalItems': 0, 'totalPages': 0}),
    });
    await pumpFreezify(tester, backend, storage, push: push);

    push.tap();
    await tester.pumpAndSettle();

    expect(find.textContaining('No tienes avisos'), findsOneWidget);
  });

  testWidgets('a push that arrives with the app on screen updates the bell', (tester) async {
    var waiting = 0;
    final push = FakePushMessaging(token: 'device-1');
    final backend = backendWith({
      _unread: (_) => FakeResponse.ok({'count': waiting}),
    });
    await pumpFreezify(tester, backend, storage, push: push);
    expect(find.byTooltip('Avisos'), findsOneWidget);

    waiting = 1;
    push.receive();
    await tester.pumpAndSettle();

    expect(find.byTooltip('Avisos: 1 sin leer'), findsOneWidget);
  });

  testWidgets('signing out withdraws this phone before the session ends', (tester) async {
    final push = FakePushMessaging(token: 'device-1');
    final backend = backendWith();
    await pumpFreezify(tester, backend, storage, push: push);

    await signOut(tester);

    expect(find.text('Inicia sesión'), findsOneWidget);
    expect(backend.last(_unregister).body, {'token': 'device-1'});
    expect(backend.last(_unregister).authorization, 'Bearer access-2');
    final routes = backend.calls.map((call) => call.route).toList();
    expect(routes.indexOf(_unregister), lessThan(routes.indexOf('POST /auth/logout')));
    // And the token is thrown away, so nothing reaches the phone even if the backend never heard.
    expect(push.forgotten, 1);
  });

  testWidgets('signing out still works when the phone cannot be withdrawn', (tester) async {
    final push = FakePushMessaging(token: 'device-1');
    final backend = backendWith({_unregister: (_) => FakeResponse.problem(500, 'INTERNAL_ERROR')});
    await pumpFreezify(tester, backend, storage, push: push);

    await signOut(tester);

    expect(find.text('Inicia sesión'), findsOneWidget);
    expect(storage.value, isNull);
    expect(push.forgotten, 1);
  });

  testWidgets('a session that ends on its own throws the token away', (tester) async {
    final push = FakePushMessaging(token: 'device-1');
    var sessionValid = true;
    FakeResponse unauthorized(RecordedCall _) => FakeResponse.problem(401, 'UNAUTHORIZED');
    final backend = backendWith({
      'POST /auth/refresh': (_) => sessionValid
          ? const FakeResponse.ok({'accessToken': 'access-2', 'refreshToken': 'refresh-2'})
          : FakeResponse.problem(401, 'INVALID_REFRESH_TOKEN'),
      'GET /households/h1': unauthorized,
      'GET /households/h1/inventory': unauthorized,
      'GET /households/h1/inventory/consume-first': unauthorized,
    });
    await pumpFreezify(tester, backend, storage, push: push);
    expect(find.text('Casa'), findsOneWidget);

    // The session is revoked elsewhere; the next requests find out.
    sessionValid = false;
    await tester.tap(find.text('Casa'));
    await tester.pumpAndSettle();

    expect(find.text('Inicia sesión'), findsOneWidget);
    expect(push.forgotten, 1);
    expect(backend.count(_unregister), 0);
  });

  testWidgets('a phone without pushes has nothing to withdraw', (tester) async {
    final push = FakePushMessaging();
    final backend = backendWith();
    await pumpFreezify(tester, backend, storage, push: push);

    await signOut(tester);

    expect(find.text('Inicia sesión'), findsOneWidget);
    expect(backend.count(_unregister), 0);
    expect(push.forgotten, 0);
  });
}
