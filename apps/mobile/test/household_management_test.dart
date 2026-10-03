import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/app_harness.dart';
import 'support/fake_backend.dart';

const _ana = {'id': 'u1', 'email': 'ana@example.com', 'displayName': 'Ana', 'locale': 'es'};
const _members = [
  {
    'userId': 'u1',
    'displayName': 'Ana',
    'email': 'ana@example.com',
    'role': 'OWNER',
    'joinedAt': '2026-10-01T10:00:00Z',
  },
  {
    'userId': 'u2',
    'displayName': 'Lucía',
    'email': 'lucia@example.com',
    'role': 'MEMBER',
    'joinedAt': '2026-10-01T12:00:00Z',
  },
];
const _anasCode = {'code': 'ABCD2345', 'expiresAt': '2026-10-08T10:00:00Z', 'createdBy': 'u1'};
const _luciasCode = {'code': 'WXYZ6789', 'expiresAt': '2026-10-09T10:00:00Z', 'createdBy': 'u2'};

const _emptyInventory = {'items': <Object>[], 'page': 0, 'size': 50, 'totalItems': 0, 'totalPages': 0};
const _nothingToConsumeFirst = {
  'counts': {'EXPIRED': 0, 'TODAY': 0, 'URGENT': 0, 'SOON': 0, 'UPCOMING': 0, 'OK': 0, 'NO_DATE': 0},
  'items': <Object>[],
};

Map<String, Object?> _casa(String role) => {'id': 'h1', 'name': 'Casa', 'role': role, 'memberCount': 2};

void main() {
  late InMemoryTokenStorage storage;

  setUp(() => storage = InMemoryTokenStorage('refresh-1'));

  FakeBackend backendWith([Map<String, FakeHandler> extra = const {}]) => FakeBackend({
    'POST /auth/refresh': (_) => const FakeResponse.ok({'accessToken': 'access-2', 'refreshToken': 'refresh-2'}),
    'GET /users/me': (_) => const FakeResponse.ok(_ana),
    'GET /households': (_) => FakeResponse.ok([_casa('OWNER')]),
    'GET /households/h1': (_) => FakeResponse.ok(_casa('OWNER')),
    'GET /households/h1/members': (_) => const FakeResponse.ok(_members),
    'GET /households/h1/invitations': (_) => const FakeResponse.ok([_anasCode, _luciasCode]),
    'GET /households/h1/inventory': (_) => const FakeResponse.ok(_emptyInventory),
    'GET /households/h1/inventory/consume-first': (_) => const FakeResponse.ok(_nothingToConsumeFirst),
    ...extra,
  });

  Future<void> openSettings(WidgetTester tester, FakeBackend backend) async {
    await pumpFreezify(tester, backend, storage);
    await tester.tap(find.text('Casa'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Miembros y ajustes'));
    await tester.pumpAndSettle();
  }

  Future<void> tapVisible(WidgetTester tester, Finder finder) async {
    await tester.ensureVisible(finder);
    await tester.pumpAndSettle();
    await tester.tap(finder);
    await tester.pumpAndSettle();
  }

  /// Drags the settings list until [finder] is built and visible; the list builds lazily.
  Future<void> scrollTo(WidgetTester tester, Finder finder, {required bool down}) async {
    await tester.dragUntilVisible(finder, find.byType(ListView), Offset(0, down ? -200 : 200));
    await tester.pumpAndSettle();
  }

  group('household settings', () {
    testWidgets('lists the codes that still let someone join', (tester) async {
      await openSettings(tester, backendWith());

      expect(find.text('Códigos activos'), findsOneWidget);
      expect(find.text('ABCD2345'), findsOneWidget);
      expect(find.text('Válido hasta el 08/10/2026'), findsOneWidget);
      // The owner can revoke any code.
      expect(find.byTooltip('Revocar el código WXYZ6789'), findsOneWidget);
    });

    testWidgets('asks before revoking a code', (tester) async {
      var codes = <Object>[_anasCode, _luciasCode];
      final backend = backendWith({
        'GET /households/h1/invitations': (_) => FakeResponse.ok(codes),
        'DELETE /households/h1/invitations/ABCD2345': (_) {
          codes = [_luciasCode];
          return const FakeResponse(204);
        },
      });
      await openSettings(tester, backend);

      await tapVisible(tester, find.byTooltip('Revocar el código ABCD2345'));
      expect(
        find.text('El código ABCD2345 dejará de servir para unirse. Quien ya se unió con él se queda.'),
        findsOneWidget,
      );
      await tester.tap(find.text('Cancelar'));
      await tester.pumpAndSettle();
      expect(backend.count('DELETE /households/h1/invitations/ABCD2345'), 0);

      await tapVisible(tester, find.byTooltip('Revocar el código ABCD2345'));
      await tester.tap(find.widgetWithText(TextButton, 'Revocar'));
      await tester.pumpAndSettle();

      expect(backend.count('DELETE /households/h1/invitations/ABCD2345'), 1);
      expect(find.text('ABCD2345'), findsNothing);
    });

    testWidgets('lets a member revoke only the codes they generated', (tester) async {
      await openSettings(
        tester,
        backendWith({
          'GET /users/me': (_) =>
              FakeResponse.ok({'id': 'u2', 'email': 'lucia@example.com', 'displayName': 'Lucía', 'locale': 'es'}),
          'GET /households': (_) => FakeResponse.ok([_casa('MEMBER')]),
          'GET /households/h1': (_) => FakeResponse.ok(_casa('MEMBER')),
        }),
      );

      expect(find.byTooltip('Revocar el código WXYZ6789'), findsOneWidget);
      expect(find.byTooltip('Revocar el código ABCD2345'), findsNothing);
      expect(find.byTooltip('Hacer propietario a Ana'), findsNothing);
    });

    testWidgets('lets the owner hand the household over, asking first', (tester) async {
      var role = 'OWNER';
      final backend = backendWith({
        'GET /households/h1': (_) => FakeResponse.ok(_casa(role)),
        'POST /households/h1/owner': (_) {
          role = 'MEMBER';
          return const FakeResponse(204);
        },
      });
      await openSettings(tester, backend);
      // The hint sits at the end of the list, beyond the test screen.
      await scrollTo(tester, find.textContaining('haz propietario antes a otro miembro'), down: true);
      expect(find.textContaining('haz propietario antes a otro miembro'), findsOneWidget);

      await scrollTo(tester, find.byTooltip('Hacer propietario a Lucía'), down: false);
      await tapVisible(tester, find.byTooltip('Hacer propietario a Lucía'));
      expect(
        find.text(
          'Lucía pasará a ser el propietario del hogar y tú seguirás como miembro. Solo Lucía podrá devolvértelo.',
        ),
        findsOneWidget,
      );
      await tester.tap(find.widgetWithText(TextButton, 'Hacer propietario'));
      await tester.pumpAndSettle();

      expect(backend.last('POST /households/h1/owner').body, {'userId': 'u2'});
      // Now a member: leaving is offered instead of deleting.
      await scrollTo(tester, find.text('Abandonar hogar'), down: true);
      expect(find.text('Abandonar hogar'), findsOneWidget);
      await scrollTo(tester, find.text('Miembros'), down: false);
      expect(find.byTooltip('Hacer propietario a Lucía'), findsNothing);
    });
  });
}
