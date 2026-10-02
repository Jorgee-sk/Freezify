import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:freezify/features/inventory/inventory_format.dart';
import 'package:freezify/features/inventory/inventory_models.dart';

import 'support/app_harness.dart';
import 'support/fake_backend.dart';

const _ana = {'id': 'u1', 'email': 'ana@example.com', 'displayName': 'Ana', 'locale': 'es'};
const _casa = {'id': 'h1', 'name': 'Casa', 'role': 'OWNER', 'memberCount': 2};

const _list = 'GET /households/h1/inventory';
const _create = 'POST /households/h1/inventory';
const _consumeFirst = 'GET /households/h1/inventory/consume-first';

Map<String, Object?> _item(String id, String name, [Map<String, Object?> overrides = const {}]) => {
  'id': id,
  'householdId': 'h1',
  'foodId': null,
  'name': name,
  'category': 'OTHER',
  'quantity': {'amount': 1, 'unit': 'UNIT'},
  'storageLocation': 'REFRIGERATOR',
  'status': 'AVAILABLE',
  'purchaseDate': '2026-10-01',
  'expirationDate': null,
  'expirationSource': null,
  'userExpirationDate': null,
  'openedDate': null,
  'barcode': null,
  'brand': null,
  'estimatedPrice': null,
  'notes': null,
  ...overrides,
};

final _pollo = _item('i1', 'Pollo', {
  'quantity': {'amount': 1, 'unit': 'KILOGRAM'},
  'expirationDate': '2026-10-03',
  'expirationSource': 'USER',
  'userExpirationDate': '2026-10-03',
});

/// What the consume-first endpoint answers for [items], counted by their priority.
FakeResponse _consumeFirstOf(List<Map<String, Object?>> items) {
  final counts = {'EXPIRED': 0, 'TODAY': 0, 'URGENT': 0, 'SOON': 0, 'UPCOMING': 0, 'OK': 0, 'NO_DATE': 0};
  for (final item in items) {
    final level = (item['priority'] as String?) ?? 'NO_DATE';
    counts[level] = counts[level]! + 1;
  }
  return FakeResponse.ok({'counts': counts, 'items': items});
}

Map<String, Object?> _expiring(String id, String name, int days, String priority) =>
    _item(id, name, {'expirationDate': '2026-10-05', 'expirationSource': 'USER', 'daysUntilExpiration': days, 'priority': priority});

FakeResponse _page(List<Map<String, Object?>> items, {int page = 0, int? totalItems, int? totalPages}) =>
    FakeResponse.ok({
      'items': items,
      'page': page,
      'size': 50,
      'totalItems': totalItems ?? items.length,
      'totalPages': totalPages ?? (items.isEmpty ? 0 : 1),
    });

void main() {
  late InMemoryTokenStorage storage;

  setUp(() => storage = InMemoryTokenStorage('refresh-1'));

  /// A backend with Ana signed in and [items] in the inventory of her household; [extra] adds or overrides routes.
  FakeBackend backendWith(List<Map<String, Object?>> items, [Map<String, FakeHandler> extra = const {}]) =>
      FakeBackend({
        'POST /auth/refresh': (_) => const FakeResponse.ok({'accessToken': 'access-2', 'refreshToken': 'refresh-2'}),
        'GET /users/me': (_) => const FakeResponse.ok(_ana),
        'GET /households': (_) => const FakeResponse.ok([_casa]),
        'GET /households/h1': (_) => const FakeResponse.ok(_casa),
        _list: (_) => _page(items),
        'GET /households/h1/inventory/recent': (_) => const FakeResponse.ok(<Object>[]),
        _consumeFirst: (_) => _consumeFirstOf(const []),
        ...extra,
      });

  Future<void> openInventory(WidgetTester tester, FakeBackend backend) async {
    await pumpFreezify(tester, backend, storage);
    await tester.tap(find.text('Casa'));
    await tester.pumpAndSettle();
  }

  /// Lets a debounced search fire and its result be drawn.
  Future<void> waitForSearch(WidgetTester tester) async {
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();
  }

  Future<void> openForm(WidgetTester tester) async {
    await tester.tap(find.text('Añadir alimento'));
    await tester.pumpAndSettle();
  }

  Future<void> save(WidgetTester tester) async {
    final button = find.widgetWithText(FilledButton, 'Guardar');
    await tester.ensureVisible(button);
    await tester.pumpAndSettle();
    await tester.tap(button);
    await tester.pumpAndSettle();
  }

  Future<void> chooseAction(WidgetTester tester, String action) async {
    await tester.tap(find.byTooltip('Acciones'));
    await tester.pumpAndSettle();
    await tester.tap(find.text(action));
    await tester.pumpAndSettle();
  }

  Finder formField(String label) => find.widgetWithText(TextFormField, label);

  group('inventory list', () {
    testWidgets('shows what the household has, with quantity, place and expiration', (tester) async {
      await openInventory(
        tester,
        backendWith([
          _pollo,
          _item('i2', 'Huevos', {
            'quantity': {'amount': 6, 'unit': 'UNIT'},
            'brand': 'Campo',
          }),
          _item('i3', 'Leche', {
            'quantity': {'amount': 0.75, 'unit': 'LITER'},
            'status': 'OPENED',
            'openedDate': '2026-09-30',
            'expirationDate': '2026-10-06',
            'expirationSource': 'ESTIMATED',
          }),
        ]),
      );

      expect(find.widgetWithText(AppBar, 'Casa'), findsOneWidget);
      expect(find.text('3 alimentos'), findsOneWidget);
      expect(find.text('1 kg · Nevera · Caduca el 03/10/2026'), findsOneWidget);
      expect(find.text('Huevos · Campo'), findsOneWidget);
      expect(find.text('6 uds · Nevera · Sin fecha de caducidad'), findsOneWidget);
      // An estimated date is never shown as if it were the one on the package.
      expect(
        find.text('0,75 l · Nevera · Caduca hacia el 06/10/2026 (fecha estimada) · Abierto el 30/09/2026'),
        findsOneWidget,
      );
      expect(find.text('Abierto'), findsOneWidget);
    });

    testWidgets('invites to add food when the inventory is empty', (tester) async {
      await openInventory(tester, backendWith([]));

      expect(find.textContaining('Aún no hay alimentos'), findsOneWidget);
      expect(find.text('0 alimentos'), findsOneWidget);
    });

    testWidgets('filters by place and by text', (tester) async {
      final backend = backendWith([_pollo]);
      await openInventory(tester, backend);
      expect(backend.last(_list).query, {'state': 'ACTIVE', 'page': '0', 'size': '50'});

      await tester.tap(find.widgetWithText(ChoiceChip, 'Congelador'));
      await tester.pumpAndSettle();
      expect(backend.last(_list).query['location'], 'FREEZER');

      await tester.enterText(find.byType(TextField), 'pol');
      await waitForSearch(tester);
      expect(backend.last(_list).query['q'], 'pol');
      expect(backend.last(_list).query['location'], 'FREEZER');
    });

    testWidgets('filters by state and category', (tester) async {
      final backend = backendWith([]);
      await openInventory(tester, backend);

      await tester.tap(find.byTooltip('Filtros'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('En casa'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Terminados').last);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Todas las categorías'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Carne').last);
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Confirmar'));
      await tester.pumpAndSettle();

      expect(backend.last(_list).query['state'], 'FINISHED');
      expect(backend.last(_list).query['category'], 'MEAT');
      expect(find.text('Ningún alimento coincide con el filtro.'), findsOneWidget);
    });

    testWidgets('does not offer actions on food that is already finished', (tester) async {
      await openInventory(
        tester,
        backendWith([
          _item('i9', 'Yogur', {
            'status': 'CONSUMED',
            'quantity': {'amount': 0, 'unit': 'UNIT'},
          }),
        ]),
      );

      expect(find.text('Consumido'), findsOneWidget);
      expect(find.byTooltip('Acciones'), findsNothing);
    });

    testWidgets('pages through long inventories', (tester) async {
      final backend = backendWith([], {
        _list: (call) {
          final page = int.parse(call.query['page']!);
          return _page([_item('p$page', 'Alimento de la página $page')], page: page, totalItems: 120, totalPages: 3);
        },
      });
      await openInventory(tester, backend);
      expect(find.text('Página 1 de 3'), findsOneWidget);
      expect(tester.widget<TextButton>(find.widgetWithText(TextButton, 'Anterior')).onPressed, isNull);

      await tester.tap(find.widgetWithText(TextButton, 'Siguiente'));
      await tester.pumpAndSettle();

      expect(find.text('Alimento de la página 1'), findsOneWidget);
      expect(find.text('Página 2 de 3'), findsOneWidget);
      expect(backend.last(_list).query['page'], '1');
    });

    testWidgets('opens the members and settings of the household', (tester) async {
      await openInventory(
        tester,
        backendWith([], {'GET /households/h1/members': (_) => const FakeResponse.ok(<Object>[])}),
      );

      await tester.tap(find.byTooltip('Miembros y ajustes'));
      await tester.pumpAndSettle();

      expect(find.text('Miembros'), findsOneWidget);
      expect(find.text('Invitar a alguien'), findsOneWidget);
    });
  });

  group('expiration priority', () {
    final expired = _expiring('e1', 'Leche', -3, 'EXPIRED');
    final today = _expiring('e2', 'Yogur', 0, 'TODAY');
    final urgent = _expiring('e3', 'Pollo', 1, 'URGENT');
    final soon = _expiring('e4', 'Lechuga', 5, 'SOON');
    final upcoming = _expiring('e5', 'Queso', 8, 'UPCOMING');
    final fine = _expiring('e6', 'Arroz', 150, 'OK');

    testWidgets('labels each item with how soon it should be eaten', (tester) async {
      await openInventory(tester, backendWith([expired, today, urgent, soon, upcoming, fine, _item('e7', 'Sal')]));

      expect(find.text('Caducado · hace 3 días'), findsOneWidget);
      expect(find.text('Vence hoy'), findsOneWidget);
      expect(find.text('Urgente · queda 1 día'), findsOneWidget);
      expect(find.text('Consumir pronto · quedan 5 días'), findsOneWidget);
      expect(find.text('Próximo · quedan 8 días'), findsOneWidget);
      // No label when there is plenty of time, or no date at all.
      expect(find.textContaining('150'), findsNothing);
    });

testWidgets('says "expired" once for food the daily sweep has marked as expired', (tester) async {
      await openInventory(tester, backendWith([{...expired, 'status': 'EXPIRED'}]));

      expect(find.textContaining('Caducado'), findsOneWidget);
      // It is still in the house: it can be eaten at one's own risk, thrown away, or its date corrected.
      await tester.tap(find.byTooltip('Acciones'));
      await tester.pumpAndSettle();
      expect(find.text('Consumir'), findsOneWidget);
      expect(find.text('Tirar'), findsOneWidget);
      expect(find.text('Editar'), findsOneWidget);
      expect(find.text('Marcar como abierto'), findsNothing);
    });

    testWidgets('answers "what should I eat first?" at the top of the inventory', (tester) async {
      await openInventory(
        tester,
        backendWith([], {_consumeFirst: (_) => _consumeFirstOf([expired, urgent, soon])}),
      );

      expect(find.text('Consume primero'), findsOneWidget);
      expect(find.text('3 alimentos que no pueden esperar'), findsOneWidget);
      expect(find.text('Leche · 1 ud'), findsOneWidget);
      expect(find.text('Caducado · hace 3 días'), findsOneWidget);
      expect(find.text('Pollo · 1 ud'), findsOneWidget);
      expect(find.text('Urgente · queda 1 día'), findsOneWidget);
      expect(find.text('Lechuga · 1 ud'), findsOneWidget);
    });

    testWidgets('shows the five most pressing and says how many more there are', (tester) async {
      final many = [for (var index = 0; index < 7; index++) _expiring('u$index', 'Alimento $index', 1, 'URGENT')];
      await openInventory(tester, backendWith([], {_consumeFirst: (_) => _consumeFirstOf(many)}));

      expect(find.text('7 alimentos que no pueden esperar'), findsOneWidget);
      expect(find.text('Urgente · queda 1 día'), findsNWidgets(5));
      expect(find.text('y 2 más'), findsOneWidget);
    });

    testWidgets('says nothing when no food needs attention', (tester) async {
      await openInventory(tester, backendWith([fine], {_consumeFirst: (_) => _consumeFirstOf([fine, upcoming])}));

      expect(find.text('Arroz'), findsOneWidget);
      expect(find.text('Consume primero'), findsNothing);
    });

    testWidgets('is refreshed when the inventory changes', (tester) async {
      var pressing = [urgent];
      final backend = backendWith([urgent], {
        _consumeFirst: (_) => _consumeFirstOf(pressing),
        'POST /households/h1/inventory/e3/consume': (_) {
          pressing = [];
          return FakeResponse.ok(urgent);
        },
      });
      await openInventory(tester, backend);
      expect(find.text('Consume primero'), findsOneWidget);

      await chooseAction(tester, 'Consumir');
      await tester.tap(find.widgetWithText(FilledButton, 'Confirmar'));
      await tester.pumpAndSettle();

      expect(find.text('Consume primero'), findsNothing);
      expect(backend.count(_consumeFirst), 2);
    });
  });

  group('live updates', () {
    testWidgets('shows what another member changes without reloading', (tester) async {
      final events = StreamController<Uint8List>();
      addTearDown(events.close);
      final items = [_pollo];
      final backend = backendWith([], {
        _list: (_) => _page(items),
        'GET /households/h1/events': (_) => FakeResponse.stream(events.stream),
      });
      await openInventory(tester, backend);
      final requestsBefore = backend.count(_list);

      // Someone else adds eggs; the server only says that the inventory changed.
      items.add(_item('i2', 'Huevos'));
      events.add(utf8.encode('event:inventory-changed\ndata:{}\n\n'));
      await tester.pumpAndSettle();

      expect(find.text('Huevos'), findsOneWidget);
      expect(backend.count(_list), requestsBefore + 1);
    });

    testWidgets('stops listening when the inventory is closed', (tester) async {
      final backend = backendWith([_pollo]);
      await openInventory(tester, backend);
      expect(backend.count('GET /households/h1/events'), 1);

      // Not tester.pageBack(): it looks for the English tooltip, and the app is shown in Spanish.
      await tester.tap(find.byType(BackButton));
      await tester.pumpAndSettle();
      // Long enough for a reconnection attempt, if one were still scheduled.
      await tester.pump(const Duration(seconds: 5));

      expect(find.text('Mis hogares'), findsOneWidget);
      expect(backend.count('GET /households/h1/events'), 1);
    });
  });

  group('adding and editing food', () {
    testWidgets('adds a food picked from the catalog suggestions', (tester) async {
      final backend = backendWith([], {
        'GET /foods': (_) => const FakeResponse.ok([
          {
            'id': 'f1',
            'name': 'Tomate',
            'category': 'VEGETABLES',
            'defaultUnit': 'UNIT',
            'defaultStorage': 'REFRIGERATOR',
          },
          {
            'id': 'f2',
            'name': 'Tomate frito',
            'category': 'PANTRY',
            'defaultUnit': 'GRAM',
            'defaultStorage': 'PANTRY',
          },
        ]),
        _create: (_) => FakeResponse(201, _item('new', 'Tomate frito')),
      });
      await openInventory(tester, backend);
      final listRequests = backend.count(_list);

      await openForm(tester);
      expect(find.widgetWithText(AppBar, 'Nuevo alimento'), findsOneWidget);
      await tester.enterText(formField('Alimento'), 'tom');
      await waitForSearch(tester);
      expect(backend.last('GET /foods').query, {'q': 'tom', 'lang': 'es'});
      await tester.tap(find.widgetWithText(ActionChip, 'Tomate frito'));
      await tester.pumpAndSettle();

      // The catalog food brings its usual unit and place.
      expect(find.text('gramos'), findsOneWidget);
      expect(find.text('Despensa'), findsOneWidget);

      await tester.enterText(formField('Cantidad'), '400');
      await save(tester);

      expect(backend.last(_create).body, {
        'foodId': 'f2',
        'name': 'Tomate frito',
        'category': 'PANTRY',
        'quantity': {'amount': 400, 'unit': 'GRAM'},
        'storageLocation': 'PANTRY',
        'purchaseDate': toIsoDay(DateTime.now()),
        'expirationDate': null,
        'openedDate': null,
        'barcode': null,
        'brand': null,
        'estimatedPrice': null,
        'notes': null,
      });
      // Back on the inventory, which is fetched again after the change.
      expect(find.widgetWithText(AppBar, 'Casa'), findsOneWidget);
      expect(backend.count(_list), greaterThan(listRequests));
    });

    testWidgets('adds a food that is not in the catalog, with a decimal comma', (tester) async {
      final backend = backendWith([], {
        'GET /foods': (_) => const FakeResponse.ok(<Object>[]),
        _create: (_) => FakeResponse(201, _item('new', 'Kimchi')),
      });
      await openInventory(tester, backend);

      await openForm(tester);
      await tester.enterText(formField('Alimento'), 'Kimchi');
      await tester.enterText(formField('Cantidad'), '0,5');
      await save(tester);

      final body = backend.last(_create).body! as Map<String, dynamic>;
      expect(body['foodId'], isNull);
      expect(body['name'], 'Kimchi');
      expect(body['category'], isNull);
      expect(body['quantity'], {'amount': 0.5, 'unit': 'UNIT'});
    });

    testWidgets('offers recently added foods to add them again in one tap', (tester) async {
      final backend = backendWith([], {
        'GET /households/h1/inventory/recent': (_) => const FakeResponse.ok([
          {
            'name': 'Leche',
            'foodId': 'f5',
            'category': 'DAIRY',
            'quantity': {'amount': 2, 'unit': 'LITER'},
            'storageLocation': 'REFRIGERATOR',
          },
        ]),
        _create: (_) => FakeResponse(201, _item('new', 'Leche')),
      });
      await openInventory(tester, backend);

      await openForm(tester);
      await tester.tap(find.widgetWithText(ActionChip, 'Leche'));
      await tester.pumpAndSettle();
      await save(tester);

      final body = backend.last(_create).body! as Map<String, dynamic>;
      expect(body['name'], 'Leche');
      expect(body['foodId'], 'f5');
      expect(body['category'], 'DAIRY');
      expect(body['quantity'], {'amount': 2, 'unit': 'LITER'});
    });

    testWidgets('does not send a quantity that is not a positive number', (tester) async {
      final backend = backendWith([], {'GET /foods': (_) => const FakeResponse.ok(<Object>[])});
      await openInventory(tester, backend);

      await openForm(tester);
      await tester.enterText(formField('Alimento'), 'Arroz');
      await tester.enterText(formField('Cantidad'), 'mucho');
      await save(tester);

      expect(find.text('Introduce una cantidad mayor que cero.'), findsOneWidget);
      expect(backend.count(_create), 0);
    });

    testWidgets('requires a name', (tester) async {
      final backend = backendWith([]);
      await openInventory(tester, backend);

      await openForm(tester);
      await save(tester);

      expect(find.text('Obligatorio'), findsOneWidget);
      expect(backend.count(_create), 0);
    });

    testWidgets('explains a rejected item and keeps the form open', (tester) async {
      final backend = backendWith([], {
        'GET /foods': (_) => const FakeResponse.ok(<Object>[]),
        _create: (_) => FakeResponse.problem(400, 'VALIDATION_ERROR'),
      });
      await openInventory(tester, backend);

      await openForm(tester);
      await tester.enterText(formField('Alimento'), 'Arroz');
      await save(tester);

      expect(find.text('Revisa los datos introducidos.'), findsOneWidget);
      expect(find.widgetWithText(AppBar, 'Nuevo alimento'), findsOneWidget);
    });

    testWidgets('edits an item keeping what the form does not show', (tester) async {
      const update = 'PUT /households/h1/inventory/i1';
      final opened = {..._pollo, 'status': 'OPENED', 'openedDate': '2026-10-01', 'barcode': '8412345678905'};
      final backend = backendWith([opened], {update: (_) => FakeResponse.ok(opened)});
      await openInventory(tester, backend);

      await tester.tap(find.text('Pollo'));
      await tester.pumpAndSettle();
      expect(find.widgetWithText(AppBar, 'Editar alimento'), findsOneWidget);
      expect(find.text('kilogramos'), findsOneWidget);

      final price = formField('Precio estimado (€)');
      await tester.ensureVisible(price);
      await tester.enterText(price, '7,95');
      await save(tester);

      final body = backend.last(update).body! as Map<String, dynamic>;
      expect(body['name'], 'Pollo');
      expect(body['quantity'], {'amount': 1, 'unit': 'KILOGRAM'});
      expect(body['estimatedPrice'], 7.95);
      expect(body['expirationDate'], '2026-10-03');
      expect(body['purchaseDate'], '2026-10-01');
      expect(body['openedDate'], '2026-10-01');
      expect(body['barcode'], '8412345678905');
    });
  });

  group('estimated dates', () {
    // Opened milk: the carton says December, but once opened it is estimated to last until the 7th.
    final openedMilk = _item('m1', 'Leche', {
      'status': 'OPENED',
      'openedDate': '2026-10-04',
      'expirationDate': '2026-10-07',
      'expirationSource': 'ESTIMATED',
      'userExpirationDate': '2026-12-24',
    });
    final chicken = _item('c1', 'Pollo', {'expirationDate': '2026-10-03', 'expirationSource': 'ESTIMATED'});

    testWidgets('edits the date the user gave, not the estimate that replaced it', (tester) async {
      const update = 'PUT /households/h1/inventory/m1';
      final backend = backendWith([openedMilk], {update: (_) => FakeResponse.ok(openedMilk)});
      await openInventory(tester, backend);
      expect(find.textContaining('Caduca hacia el 07/10/2026 (fecha estimada)'), findsOneWidget);

      await tester.tap(find.text('Leche'));
      await tester.pumpAndSettle();
      expect(find.text('24/12/2026'), findsOneWidget);
      await save(tester);

      expect((backend.last(update).body! as Map<String, dynamic>)['expirationDate'], '2026-12-24');
    });

    testWidgets('never sends an estimate back as if the user had typed it', (tester) async {
      const update = 'PUT /households/h1/inventory/c1';
      final backend = backendWith([chicken], {update: (_) => FakeResponse.ok(chicken)});
      await openInventory(tester, backend);

      await tester.tap(find.text('Pollo'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Ahora mismo estimamos el 03/10/2026'), findsOneWidget);
      await save(tester);

      final body = backend.last(update).body! as Map<String, dynamic>;
      expect(body.containsKey('expirationDate'), isTrue);
      expect(body['expirationDate'], isNull);
    });

    testWidgets('tells the user that an empty date will be estimated', (tester) async {
      await openInventory(tester, backendWith([]));

      await openForm(tester);

      expect(find.textContaining('Si la dejas vacía, la estimamos'), findsOneWidget);
    });
  });

  group('using food', () {
    testWidgets('consumes part of an item, in a compatible unit', (tester) async {
      const consume = 'POST /households/h1/inventory/i1/consume';
      final backend = backendWith([_pollo], {consume: (_) => FakeResponse.ok(_pollo)});
      await openInventory(tester, backend);

      await chooseAction(tester, 'Consumir');
      expect(find.text('¿Cuánto has consumido?'), findsOneWidget);
      // Everything that is left is proposed by default.
      final amount = find.widgetWithText(TextField, 'Cantidad');
      expect(tester.widget<TextField>(amount).controller!.text, '1');

      await tester.enterText(amount, '250');
      await tester.tap(find.text('kilogramos'));
      await tester.pumpAndSettle();
      // Kilograms can be given in grams, but never in liters or units.
      expect(find.text('litros'), findsNothing);
      expect(find.text('unidades'), findsNothing);
      await tester.tap(find.text('gramos').last);
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Confirmar'));
      await tester.pumpAndSettle();

      expect(backend.last(consume).body, {
        'quantity': {'amount': 250, 'unit': 'GRAM'},
      });
      expect(find.text('¿Cuánto has consumido?'), findsNothing);
    });

    testWidgets('records why food was thrown away', (tester) async {
      const discard = 'POST /households/h1/inventory/i1/discard';
      final backend = backendWith([_pollo], {discard: (_) => FakeResponse.ok(_pollo)});
      await openInventory(tester, backend);

      await chooseAction(tester, 'Tirar');
      await tester.tap(find.text('Caducado'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('En mal estado').last);
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Confirmar'));
      await tester.pumpAndSettle();

      expect(backend.last(discard).body, {
        'quantity': {'amount': 1, 'unit': 'KILOGRAM'},
        'reason': 'SPOILED',
      });
    });

    testWidgets('does not accept an empty quantity', (tester) async {
      const consume = 'POST /households/h1/inventory/i1/consume';
      final backend = backendWith([_pollo], {consume: (_) => FakeResponse.ok(_pollo)});
      await openInventory(tester, backend);

      await chooseAction(tester, 'Consumir');
      await tester.enterText(find.widgetWithText(TextField, 'Cantidad'), '');
      await tester.tap(find.widgetWithText(FilledButton, 'Confirmar'));
      await tester.pumpAndSettle();

      expect(find.text('Introduce una cantidad mayor que cero.'), findsOneWidget);
      expect(backend.count(consume), 0);
    });

    testWidgets('explains a quantity the backend rejects', (tester) async {
      final backend = backendWith([_pollo], {
        'POST /households/h1/inventory/i1/consume': (_) => FakeResponse.problem(400, 'QUANTITY_EXCEEDS_AVAILABLE'),
      });
      await openInventory(tester, backend);

      await chooseAction(tester, 'Consumir');
      await tester.tap(find.widgetWithText(FilledButton, 'Confirmar'));
      await tester.pumpAndSettle();

      expect(find.text('Queda menos cantidad de la indicada.'), findsOneWidget);
    });

    testWidgets('marks an item as opened', (tester) async {
      const open = 'POST /households/h1/inventory/i1/open';
      final backend = backendWith([_pollo], {open: (_) => FakeResponse.ok(_pollo)});
      await openInventory(tester, backend);

      await chooseAction(tester, 'Marcar como abierto');

      expect(backend.count(open), 1);
    });

    testWidgets('asks for confirmation before deleting an item', (tester) async {
      const delete = 'DELETE /households/h1/inventory/i1';
      final backend = backendWith([_pollo], {delete: (_) => const FakeResponse(204)});
      await openInventory(tester, backend);

      await chooseAction(tester, 'Eliminar');
      expect(find.textContaining('No contará como consumido'), findsOneWidget);
      await tester.tap(find.text('Cancelar'));
      await tester.pumpAndSettle();
      expect(backend.count(delete), 0);

      await chooseAction(tester, 'Eliminar');
      await tester.tap(find.widgetWithText(TextButton, 'Eliminar'));
      await tester.pumpAndSettle();

      expect(backend.count(delete), 1);
    });
  });

  group('formatting', () {
    test('accepts a comma or a point as decimal separator', () {
      expect(parseAmount('1,5'), 1.5);
      expect(parseAmount(' 0.25 '), 0.25);
      expect(parseAmount('6'), 6);
    });

    test('rejects anything that is not a positive number', () {
      for (final text in ['', '  ', '0', '-2', 'abc', '1,2,3']) {
        expect(parseAmount(text), isNull, reason: text);
      }
    });

    test('formats amounts in the language of the user', () {
      expect(formatAmount(1.5, 'es'), '1,5');
      expect(formatAmount(1.5, 'en'), '1.5');
      expect(formatAmount(500, 'es'), '500');
      expect(formatAmount(0.125, 'es'), '0,125');
    });

    test('keeps calendar days as they are', () {
      expect(formatDay('2026-10-05'), '05/10/2026');
      expect(toIsoDay(DateTime(2026, 1, 5, 23, 59)), '2026-01-05');
      expect(toIsoDay(parseIsoDay('2026-12-31')), '2026-12-31');
    });

    test('only offers units of the same dimension', () {
      expect(Unit.kilogram.compatible, [Unit.gram, Unit.kilogram]);
      expect(Unit.milliliter.compatible, [Unit.milliliter, Unit.liter]);
      expect(Unit.unit.compatible, [Unit.unit]);
    });
  });
}
