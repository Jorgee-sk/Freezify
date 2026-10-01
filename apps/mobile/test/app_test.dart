import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:freezify/app.dart';
import 'package:freezify/core/api_client.dart';
import 'package:freezify/core/providers.dart';

import 'support/fake_backend.dart';

const _ana = {'id': 'u1', 'email': 'ana@example.com', 'displayName': 'Ana', 'locale': 'es'};
const _session = {'accessToken': 'access-1', 'refreshToken': 'refresh-1', 'expiresIn': 900, 'user': _ana};
const _casa = {'id': 'h1', 'name': 'Casa', 'role': 'OWNER', 'memberCount': 2};
const _members = [
  {'userId': 'u1', 'displayName': 'Ana', 'email': 'ana@example.com', 'role': 'OWNER', 'joinedAt': '2026-10-01T10:00:00Z'},
  {'userId': 'u2', 'displayName': 'Lucía', 'email': 'lucia@example.com', 'role': 'MEMBER', 'joinedAt': '2026-10-01T12:00:00Z'},
];

void main() {
  late InMemoryTokenStorage storage;

  setUp(() => storage = InMemoryTokenStorage());

  Future<void> pumpApp(WidgetTester tester, FakeBackend backend, {Locale deviceLocale = const Locale('es')}) async {
    // The app resolves its language from the list of preferred locales, not from the single locale.
    tester.platformDispatcher.localesTestValue = [deviceLocale];
    addTearDown(tester.platformDispatcher.clearLocalesTestValue);
    await tester.pumpWidget(
      ProviderScope(
        retry: (_, _) => null,
        overrides: [
          apiClientProvider.overrideWithValue(
            ApiClient(baseUrl: FakeBackend.baseUrl, storage: storage, adapter: backend),
          ),
        ],
        child: const FreezifyApp(),
      ),
    );
    await tester.pumpAndSettle();
  }

  Finder field(String label) => find.widgetWithText(TextFormField, label);

  group('authentication', () {
    testWidgets('shows the login screen to anonymous users, in the device language', (tester) async {
      await pumpApp(tester, FakeBackend({}));
      expect(find.text('Inicia sesión'), findsOneWidget);
    });

    testWidgets('falls back to English for unsupported device languages', (tester) async {
      await pumpApp(tester, FakeBackend({}), deviceLocale: const Locale('fr'));
      expect(find.text('Sign in'), findsWidgets);
    });

    testWidgets('signs in and lists the households of the user', (tester) async {
      final backend = FakeBackend({
        'POST /auth/login': (_) => const FakeResponse.ok(_session),
        'GET /households': (_) => const FakeResponse.ok([_casa]),
      });
      await pumpApp(tester, backend);

      await tester.enterText(field('Correo electrónico'), ' ana@example.com ');
      await tester.enterText(field('Contraseña'), 'correct-horse');
      await tester.tap(find.widgetWithText(FilledButton, 'Entrar'));
      await tester.pumpAndSettle();

      expect(find.text('Hola, Ana 👋'), findsOneWidget);
      expect(find.text('Casa'), findsOneWidget);
      expect(find.text('2 miembros'), findsOneWidget);
      expect(find.text('Propietario'), findsOneWidget);
      expect(backend.last('POST /auth/login').body, {'email': 'ana@example.com', 'password': 'correct-horse'});
      expect(storage.value, 'refresh-1');
    });

    testWidgets('explains wrong credentials and lets the user try again', (tester) async {
      await pumpApp(
        tester,
        FakeBackend({'POST /auth/login': (_) => FakeResponse.problem(401, 'INVALID_CREDENTIALS')}),
      );

      await tester.enterText(field('Correo electrónico'), 'ana@example.com');
      await tester.enterText(field('Contraseña'), 'wrong-password');
      await tester.tap(find.widgetWithText(FilledButton, 'Entrar'));
      await tester.pumpAndSettle();

      expect(find.text('Correo o contraseña incorrectos.'), findsOneWidget);
      expect(tester.widget<FilledButton>(find.widgetWithText(FilledButton, 'Entrar')).onPressed, isNotNull);
    });

    testWidgets('does not call the backend with an empty form', (tester) async {
      final backend = FakeBackend({});
      await pumpApp(tester, backend);

      await tester.tap(find.widgetWithText(FilledButton, 'Entrar'));
      await tester.pumpAndSettle();

      expect(find.text('Obligatorio'), findsNWidgets(2));
      expect(backend.calls, isEmpty);
    });

    testWidgets('registers with the language being shown', (tester) async {
      final backend = FakeBackend({
        'POST /auth/register': (_) => const FakeResponse(201, _session),
        'GET /households': (_) => const FakeResponse.ok(<Object>[]),
      });
      await pumpApp(tester, backend);

      await tester.tap(find.text('¿No tienes cuenta? Regístrate'));
      await tester.pumpAndSettle();
      await tester.enterText(field('Tu nombre'), 'Ana');
      await tester.enterText(field('Correo electrónico'), 'ana@example.com');
      await tester.enterText(field('Contraseña'), 'correct-horse');
      await tester.tap(find.widgetWithText(FilledButton, 'Crear cuenta'));
      await tester.pumpAndSettle();

      expect(find.textContaining('Todavía no perteneces a ningún hogar'), findsOneWidget);
      expect(backend.last('POST /auth/register').body, {
        'email': 'ana@example.com',
        'password': 'correct-horse',
        'displayName': 'Ana',
        'locale': 'es',
      });
    });

    testWidgets('restores the stored session on launch', (tester) async {
      storage.value = 'refresh-1';
      final backend = FakeBackend({
        'POST /auth/refresh': (_) => const FakeResponse.ok({'accessToken': 'access-2', 'refreshToken': 'refresh-2'}),
        'GET /users/me': (_) => const FakeResponse.ok(_ana),
        'GET /households': (_) => const FakeResponse.ok([_casa]),
      });
      await pumpApp(tester, backend, deviceLocale: const Locale('en'));

      // The saved language of the account wins over the device language.
      expect(find.text('Hola, Ana 👋'), findsOneWidget);
      expect(storage.value, 'refresh-2');
    });

    testWidgets('returns to login when the stored session is no longer valid', (tester) async {
      storage.value = 'revoked';
      await pumpApp(
        tester,
        FakeBackend({
          'POST /auth/refresh': (_) => FakeResponse.problem(401, 'INVALID_REFRESH_TOKEN'),
          'GET /users/me': (_) => FakeResponse.problem(401, 'UNAUTHORIZED'),
        }),
      );

      expect(find.text('Inicia sesión'), findsOneWidget);
      expect(storage.value, isNull);
    });

    testWidgets('keeps the session and offers a retry when launched offline', (tester) async {
      storage.value = 'refresh-1';
      final backend = FakeBackend({
        'POST /auth/refresh': (_) => const FakeResponse.ok({'accessToken': 'access-2', 'refreshToken': 'refresh-2'}),
        'GET /users/me': (_) => const FakeResponse.ok(_ana),
        'GET /households': (_) => const FakeResponse.ok(<Object>[]),
      })..offline = true;
      await pumpApp(tester, backend);

      expect(find.text('No se puede conectar con el servidor.'), findsOneWidget);
      expect(storage.value, 'refresh-1');

      backend.offline = false;
      await tester.tap(find.text('Reintentar'));
      await tester.pumpAndSettle();

      expect(find.text('Hola, Ana 👋'), findsOneWidget);
    });

    testWidgets('signs out, revoking the refresh token', (tester) async {
      storage.value = 'refresh-1';
      final backend = FakeBackend({
        'POST /auth/refresh': (_) => const FakeResponse.ok({'accessToken': 'access-2', 'refreshToken': 'refresh-2'}),
        'GET /users/me': (_) => const FakeResponse.ok(_ana),
        'GET /households': (_) => const FakeResponse.ok(<Object>[]),
        'POST /auth/logout': (_) => const FakeResponse(204),
      });
      await pumpApp(tester, backend);

      await tester.tap(find.byType(PopupMenuButton<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Cerrar sesión'));
      await tester.pumpAndSettle();

      expect(find.text('Inicia sesión'), findsOneWidget);
      expect(backend.last('POST /auth/logout').body, {'refreshToken': 'refresh-2'});
      expect(storage.value, isNull);
    });
  });

  group('households', () {
    Map<String, FakeHandler> signedInRoutes() => {
      'POST /auth/refresh': (_) => const FakeResponse.ok({'accessToken': 'access-2', 'refreshToken': 'refresh-2'}),
      'GET /users/me': (_) => const FakeResponse.ok(_ana),
    };

    setUp(() => storage.value = 'refresh-1');

    testWidgets('creates a household and opens it', (tester) async {
      const piso = {'id': 'h2', 'name': 'Piso', 'role': 'OWNER', 'memberCount': 1};
      final backend = FakeBackend({
        ...signedInRoutes(),
        'GET /households': (_) => const FakeResponse.ok(<Object>[]),
        'POST /households': (_) => const FakeResponse(201, piso),
        'GET /households/h2': (_) => const FakeResponse.ok(piso),
        'GET /households/h2/members': (_) => FakeResponse.ok([_members.first]),
      });
      await pumpApp(tester, backend);

      await tester.tap(find.text('Crear un hogar'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'Piso');
      await tester.tap(find.text('Crear'));
      await tester.pumpAndSettle();

      expect(backend.last('POST /households').body, {'name': 'Piso'});
      expect(find.widgetWithText(AppBar, 'Piso'), findsOneWidget);
      expect(find.text('Ana (tú)'), findsOneWidget);
      expect(find.text('Propietario · Desde el 01/10/2026'), findsOneWidget);
    });

    testWidgets('explains why joining with a bad code failed', (tester) async {
      final backend = FakeBackend({
        ...signedInRoutes(),
        'GET /households': (_) => const FakeResponse.ok(<Object>[]),
        'POST /households/join': (_) => FakeResponse.problem(404, 'INVITATION_NOT_FOUND'),
      });
      await pumpApp(tester, backend);

      await tester.tap(find.text('Unirme con un código'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'ZZZZZZZZ');
      await tester.tap(find.text('Unirme'));
      await tester.pumpAndSettle();

      expect(find.text('El código no es válido o ha caducado.'), findsOneWidget);
    });

    testWidgets('lets the owner generate an invitation code', (tester) async {
      final backend = FakeBackend({
        ...signedInRoutes(),
        'GET /households': (_) => const FakeResponse.ok([_casa]),
        'GET /households/h1': (_) => const FakeResponse.ok(_casa),
        'GET /households/h1/members': (_) => const FakeResponse.ok(_members),
        'POST /households/h1/invitations': (_) =>
            const FakeResponse(201, {'code': 'ABCD2345', 'expiresAt': '2026-10-08T10:00:00Z'}),
      });
      await pumpApp(tester, backend);
      await tester.tap(find.text('Casa'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Generar código'));
      await tester.pumpAndSettle();

      expect(find.text('ABCD2345'), findsOneWidget);
      expect(find.text('Válido hasta el 08/10/2026'), findsOneWidget);
    });

    testWidgets('asks for confirmation before deleting a household', (tester) async {
      var deleted = false;
      final backend = FakeBackend({
        ...signedInRoutes(),
        'GET /households': (_) => FakeResponse.ok(deleted ? const <Object>[] : const [_casa]),
        'GET /households/h1': (_) => const FakeResponse.ok(_casa),
        'GET /households/h1/members': (_) => const FakeResponse.ok(_members),
        'DELETE /households/h1': (_) {
          deleted = true;
          return const FakeResponse(204);
        },
      });
      await pumpApp(tester, backend);
      await tester.tap(find.text('Casa'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Eliminar hogar'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Esta acción no se puede deshacer'), findsOneWidget);
      await tester.tap(find.text('Cancelar'));
      await tester.pumpAndSettle();
      expect(backend.count('DELETE /households/h1'), 0);

      await tester.tap(find.text('Eliminar hogar'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(TextButton, 'Eliminar hogar').last);
      await tester.pumpAndSettle();

      expect(backend.count('DELETE /households/h1'), 1);
      expect(find.textContaining('Todavía no perteneces a ningún hogar'), findsOneWidget);
    });

    testWidgets('offers members leaving instead of owner actions', (tester) async {
      const asMember = {'id': 'h1', 'name': 'Casa', 'role': 'MEMBER', 'memberCount': 2};
      final backend = FakeBackend({
        ...signedInRoutes(),
        'GET /households': (_) => const FakeResponse.ok([asMember]),
        'GET /households/h1': (_) => const FakeResponse.ok(asMember),
        'GET /households/h1/members': (_) => const FakeResponse.ok(_members),
      });
      await pumpApp(tester, backend);
      await tester.tap(find.text('Casa'));
      await tester.pumpAndSettle();

      expect(find.text('Abandonar hogar'), findsOneWidget);
      expect(find.text('Eliminar hogar'), findsNothing);
      expect(find.byTooltip('Renombrar'), findsNothing);
      expect(find.byTooltip('Quitar'), findsNothing);
    });

    testWidgets('switches the language of the account', (tester) async {
      final backend = FakeBackend({
        ...signedInRoutes(),
        'GET /households': (_) => const FakeResponse.ok(<Object>[]),
        'PATCH /users/me': (_) => FakeResponse(200, {..._ana, 'locale': 'en'}),
      });
      await pumpApp(tester, backend);

      await tester.tap(find.byType(PopupMenuButton<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('EN'));
      await tester.pumpAndSettle();

      expect(backend.last('PATCH /users/me').body, {'locale': 'en'});
      expect(find.text('Hi, Ana 👋'), findsOneWidget);
    });
  });
}
