import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:freezify/features/notifications/notification_models.dart';
import 'package:freezify/features/notifications/notification_wording.dart';
import 'package:freezify/l10n/app_localizations.dart';

import 'support/app_harness.dart';
import 'support/fake_backend.dart';

const _ana = {'id': 'u1', 'email': 'ana@example.com', 'displayName': 'Ana', 'locale': 'es'};
const _casa = {'id': 'h1', 'name': 'Casa', 'role': 'OWNER', 'memberCount': 2};

const _list = 'GET /notifications';
const _unread = 'GET /notifications/unread-count';
const _preferences = 'GET /notifications/preferences';
const _savePreferences = 'PUT /notifications/preferences';

const _defaults = {
  'expirationAlerts': true,
  'deliveryHour': 9,
  'frequency': 'DAILY',
  'threshold': 'URGENT',
  'mutedCategories': <String>[],
};

Map<String, Object?> _food(String name, int days, {bool estimated = false}) => {
  'name': name,
  'expirationDate': '2026-10-03',
  'estimated': estimated,
  'daysUntilExpiration': days,
};

Map<String, Object?> _notification(
  String id,
  List<Map<String, Object?>> items, {
  int? itemCount,
  String day = '2026-10-02',
  bool read = false,
}) => {
  'id': id,
  'type': 'EXPIRATION',
  'householdId': 'h1',
  'householdName': 'Casa',
  'day': day,
  'itemCount': itemCount ?? items.length,
  'items': items,
  'createdAt': '2026-10-02T07:00:00Z',
  'read': read,
};

FakeResponse _page(List<Map<String, Object?>> items) => FakeResponse.ok({
  'items': items,
  'page': 0,
  'size': 50,
  'totalItems': items.length,
  'totalPages': items.isEmpty ? 0 : 1,
});

const _emptyInventory = {'items': <Object>[], 'page': 0, 'size': 50, 'totalItems': 0, 'totalPages': 0};
const _nothingToConsumeFirst = {
  'counts': {'EXPIRED': 0, 'TODAY': 0, 'URGENT': 0, 'SOON': 0, 'UPCOMING': 0, 'OK': 0, 'NO_DATE': 0},
  'items': <Object>[],
};

void main() {
  late InMemoryTokenStorage storage;

  setUp(() => storage = InMemoryTokenStorage('refresh-1'));

  /// A backend with Ana signed in; [extra] adds or overrides routes.
  FakeBackend backendWith(Map<String, FakeHandler> extra) => FakeBackend({
    'POST /auth/refresh': (_) => const FakeResponse.ok({'accessToken': 'access-2', 'refreshToken': 'refresh-2'}),
    'GET /users/me': (_) => const FakeResponse.ok(_ana),
    'GET /households': (_) => const FakeResponse.ok([_casa]),
    'GET /households/h1': (_) => const FakeResponse.ok(_casa),
    'GET /households/h1/inventory': (_) => const FakeResponse.ok(_emptyInventory),
    'GET /households/h1/inventory/recent': (_) => const FakeResponse.ok(<Object>[]),
    'GET /households/h1/inventory/consume-first': (_) => const FakeResponse.ok(_nothingToConsumeFirst),
    ...extra,
  });

  Future<void> openNotifications(WidgetTester tester, FakeBackend backend) async {
    await pumpFreezify(tester, backend, storage);
    await tester.tap(find.byIcon(Icons.notifications_outlined));
    await tester.pumpAndSettle();
  }

  Future<void> openPreferences(WidgetTester tester, FakeBackend backend) async {
    await openNotifications(tester, backend);
    await tester.tap(find.byTooltip('Preferencias de avisos'));
    await tester.pumpAndSettle();
  }

  Future<void> save(WidgetTester tester) async {
    final button = find.widgetWithText(FilledButton, 'Guardar');
    await tester.ensureVisible(button);
    await tester.pumpAndSettle();
    await tester.tap(button);
    await tester.pumpAndSettle();
  }

  group('notification bell', () {
    testWidgets('says how many notifications are waiting', (tester) async {
      await pumpFreezify(
        tester,
        backendWith({
          _unread: (_) => const FakeResponse.ok({'count': 2}),
        }),
        storage,
      );

      expect(find.byTooltip('Avisos: 2 sin leer'), findsOneWidget);
      expect(find.text('2'), findsOneWidget);
    });

    testWidgets('shows no count when everything has been read', (tester) async {
      await pumpFreezify(tester, backendWith({}), storage);

      expect(find.byTooltip('Avisos'), findsOneWidget);
      expect(find.text('0'), findsNothing);
    });

    testWidgets('looks again when the user comes back to the app', (tester) async {
      var waiting = 0;
      final backend = backendWith({
        _unread: (_) => FakeResponse.ok({'count': waiting}),
      });
      await pumpFreezify(tester, backend, storage);
      expect(find.byTooltip('Avisos'), findsOneWidget);

      // A notification is created while the app is in the background.
      waiting = 1;
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();

      expect(find.byTooltip('Avisos: 1 sin leer'), findsOneWidget);
    });
  });

  group('notifications', () {
    testWidgets('names a single food and counts several', (tester) async {
      final backend = backendWith({
        _list: (_) => _page([
          _notification('n2', [_food('Yogur', -1), _food('Jamón', 0), _food('Leche', 2)], itemCount: 4),
          _notification('n1', [_food('Brócoli', 2)], day: '2026-10-01', read: true),
        ]),
      });
      await openNotifications(tester, backend);

      expect(find.text('Tienes 4 alimentos que deberías consumir pronto'), findsOneWidget);
      expect(find.text('• Yogur caducó hace 1 día'), findsOneWidget);
      expect(find.text('• Jamón caduca hoy'), findsOneWidget);
      expect(find.text('• Leche caduca en 2 días'), findsOneWidget);
      expect(find.text('y 1 más'), findsOneWidget);
      expect(find.text('Casa · 02/10/2026'), findsOneWidget);

      expect(find.text('Brócoli caduca en 2 días'), findsOneWidget);
      expect(find.text('Casa · 01/10/2026'), findsOneWidget);
      // Only the unread one is marked as new.
      expect(find.text('Nuevo'), findsOneWidget);
      expect(backend.last(_list).query, {'size': '50'});
    });

    testWidgets('words an estimated date as an estimate', (tester) async {
      await openNotifications(
        tester,
        backendWith({
          _list: (_) => _page([
            _notification('n1', [_food('Merluza', 1, estimated: true)]),
          ]),
        }),
      );

      expect(find.text('Merluza caduca en aproximadamente 1 día (fecha estimada)'), findsOneWidget);
    });

    testWidgets('says so when there is nothing to show', (tester) async {
      await openNotifications(tester, backendWith({_list: (_) => _page([])}));

      expect(find.textContaining('No tienes avisos'), findsOneWidget);
      expect(find.byTooltip('Marcar todo como leído'), findsNothing);
    });

    testWidgets('opens the inventory the notification talks about and marks it as read', (tester) async {
      final backend = backendWith({
        _list: (_) => _page([
          _notification('n1', [_food('Leche', 1)]),
        ]),
        'POST /notifications/n1/read': (_) => const FakeResponse(204),
      });
      await openNotifications(tester, backend);

      await tester.tap(find.text('Leche caduca en 1 día'));
      await tester.pumpAndSettle();

      expect(find.text('Añadir alimento'), findsOneWidget);
      expect(backend.count('POST /notifications/n1/read'), 1);
    });

    testWidgets('still opens the inventory when the notification cannot be marked', (tester) async {
      final backend = backendWith({
        _list: (_) => _page([
          _notification('n1', [_food('Leche', 1)]),
        ]),
        'POST /notifications/n1/read': (_) => FakeResponse.problem(404, 'NOTIFICATION_NOT_FOUND'),
      });
      await openNotifications(tester, backend);

      await tester.tap(find.text('Leche caduca en 1 día'));
      await tester.pumpAndSettle();

      expect(find.text('Añadir alimento'), findsOneWidget);
    });

    testWidgets('does not mark again what was already read', (tester) async {
      final backend = backendWith({
        _list: (_) => _page([
          _notification('n1', [_food('Leche', 1)], read: true),
        ]),
      });
      await openNotifications(tester, backend);

      await tester.tap(find.text('Leche caduca en 1 día'));
      await tester.pumpAndSettle();

      expect(find.text('Añadir alimento'), findsOneWidget);
      expect(backend.count('POST /notifications/n1/read'), 0);
    });

    testWidgets('marks everything as read at once', (tester) async {
      var read = false;
      final backend = backendWith({
        _list: (_) => _page([
          _notification('n1', [_food('Leche', 1)], read: read),
        ]),
        'POST /notifications/read-all': (_) {
          read = true;
          return const FakeResponse(204);
        },
      });
      await openNotifications(tester, backend);
      expect(find.text('Nuevo'), findsOneWidget);

      await tester.tap(find.byTooltip('Marcar todo como leído'));
      await tester.pumpAndSettle();

      expect(find.text('Nuevo'), findsNothing);
      expect(find.byTooltip('Marcar todo como leído'), findsNothing);
      expect(backend.count('POST /notifications/read-all'), 1);
    });

    testWidgets('explains a failure and lets the user try again', (tester) async {
      var failing = true;
      final backend = backendWith({_list: (_) => failing ? FakeResponse.problem(500, 'INTERNAL_ERROR') : _page([])});
      await openNotifications(tester, backend);
      expect(find.text('Algo ha salido mal. Inténtalo de nuevo.'), findsOneWidget);

      failing = false;
      await tester.tap(find.text('Reintentar'));
      await tester.pumpAndSettle();

      expect(find.textContaining('No tienes avisos'), findsOneWidget);
    });
  });

  group('notification preferences', () {
    testWidgets('saves what the user chooses', (tester) async {
      final backend = backendWith({
        _list: (_) => _page([]),
        _preferences: (_) => const FakeResponse.ok(_defaults),
        _savePreferences: (call) => FakeResponse.ok(call.body),
      });
      await openPreferences(tester, backend);

      expect(find.text('09:00'), findsOneWidget);
      expect(find.text('Una vez al día'), findsOneWidget);
      expect(find.text('Caduque en 2 días o menos'), findsOneWidget);

      await tester.tap(find.text('09:00'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('10:00').last);
      await tester.pumpAndSettle();

      await tester.tap(find.text('Una vez al día'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Cada 3 días').last);
      await tester.pumpAndSettle();

      await tester.tap(find.text('Caduque en 2 días o menos'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Caduque en 5 días o menos').last);
      await tester.pumpAndSettle();

      for (final category in ['Bebidas', 'Lácteos']) {
        final chip = find.widgetWithText(FilterChip, category);
        await tester.ensureVisible(chip);
        await tester.pumpAndSettle();
        await tester.tap(chip);
        await tester.pumpAndSettle();
      }
      await save(tester);

      expect(find.text('Preferencias guardadas'), findsOneWidget);
      expect(backend.last(_savePreferences).body, {
        'expirationAlerts': true,
        'deliveryHour': 10,
        'frequency': 'EVERY_THREE_DAYS',
        'threshold': 'SOON',
        'mutedCategories': ['DAIRY', 'BEVERAGES'],
      });
    });

    testWidgets('shows what was chosen before and lets the user turn everything off', (tester) async {
      final backend = backendWith({
        _list: (_) => _page([]),
        _preferences: (_) => const FakeResponse.ok({
          'expirationAlerts': true,
          'deliveryHour': 21,
          'frequency': 'WEEKLY',
          'threshold': 'TODAY',
          'mutedCategories': ['MEAT'],
        }),
        _savePreferences: (call) => FakeResponse.ok(call.body),
      });
      await openPreferences(tester, backend);

      expect(find.text('21:00'), findsOneWidget);
      expect(find.text('Una vez a la semana'), findsOneWidget);
      expect(find.text('Caduque hoy'), findsOneWidget);
      expect(tester.widget<FilterChip>(find.widgetWithText(FilterChip, 'Carne')).selected, isFalse);
      expect(tester.widget<FilterChip>(find.widgetWithText(FilterChip, 'Pescado')).selected, isTrue);

      await tester.tap(find.byType(Switch));
      await tester.pumpAndSettle();
      // Nothing else can be chosen while notifications are off.
      expect(tester.widget<FilterChip>(find.widgetWithText(FilterChip, 'Carne')).onSelected, isNull);
      await save(tester);

      final body = backend.last(_savePreferences).body! as Map<String, dynamic>;
      expect(body['expirationAlerts'], isFalse);
      expect(body['deliveryHour'], 21);
      expect(body['mutedCategories'], ['MEAT']);
    });

    testWidgets('explains why saving failed', (tester) async {
      final backend = backendWith({
        _list: (_) => _page([]),
        _preferences: (_) => const FakeResponse.ok(_defaults),
        _savePreferences: (_) => FakeResponse.problem(400, 'VALIDATION_ERROR'),
      });
      await openPreferences(tester, backend);

      await save(tester);

      expect(find.text('Revisa los datos introducidos.'), findsOneWidget);
      expect(find.text('Preferencias guardadas'), findsNothing);
    });
  });

  group('wording', () {
    final es = lookupAppLocalizations(const Locale('es'));
    final en = lookupAppLocalizations(const Locale('en'));

    NotifiedItem food(int days, {bool estimated = false}) =>
        NotifiedItem(name: 'Leche', expirationDate: '2026-10-03', estimated: estimated, daysUntilExpiration: days);

    test('says how long is left, or how long ago the date passed', () {
      expect(itemSentence(es, food(3)), 'Leche caduca en 3 días');
      expect(itemSentence(es, food(1)), 'Leche caduca en 1 día');
      expect(itemSentence(es, food(0)), 'Leche caduca hoy');
      expect(itemSentence(es, food(-1)), 'Leche caducó hace 1 día');
      expect(itemSentence(es, food(-4)), 'Leche caducó hace 4 días');
      expect(itemSentence(en, food(2)), 'Leche expires in 2 days');
    });

    test('never presents an estimate as a fact', () {
      for (final days in [3, 1, 0, -1, -4]) {
        expect(itemSentence(es, food(days, estimated: true)), contains('(fecha estimada)'));
        expect(itemSentence(en, food(days, estimated: true)), contains('(estimated date)'));
      }
      expect(itemSentence(es, food(0, estimated: true)), 'Leche probablemente caduca hoy (fecha estimada)');
    });

    test('names the food when there is only one and counts several', () {
      AppNotification notification(List<NotifiedItem> items, int itemCount) => AppNotification(
        id: 'n1',
        householdId: 'h1',
        householdName: 'Casa',
        day: '2026-10-02',
        itemCount: itemCount,
        items: items,
        read: false,
      );

      expect(notificationTitle(es, notification([food(2)], 1)), 'Leche caduca en 2 días');
      expect(
        notificationTitle(es, notification([food(2), food(3)], 7)),
        'Tienes 7 alimentos que deberías consumir pronto',
      );
    });
  });
}
