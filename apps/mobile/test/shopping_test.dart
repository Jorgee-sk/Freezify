import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:freezify/features/inventory/inventory_format.dart';
import 'package:freezify/features/plan/plan_wording.dart';

import 'support/app_harness.dart';
import 'support/fake_backend.dart';

const _ana = {'id': 'u1', 'email': 'ana@example.com', 'displayName': 'Ana', 'locale': 'es'};
const _casa = {'id': 'h1', 'name': 'Casa', 'role': 'OWNER', 'memberCount': 2};

const _list = 'GET /households/h1/shopping-list';
const _add = 'POST /households/h1/shopping-list/items';
const _fromPlan = 'POST /households/h1/shopping-list/from-plan';

Map<String, Object?> _line(
  String id,
  String name, {
  String? foodId,
  String category = 'OTHER',
  Map<String, Object?>? quantity,
  String origin = 'MANUAL',
  String? neededOn,
  bool checked = false,
}) => {
  'id': id,
  'foodId': foodId,
  'name': name,
  'category': category,
  'quantity': quantity,
  'origin': origin,
  'neededOn': neededOn,
  'checked': checked,
  'checkedAt': null,
};

final _zucchini = _line(
  'i1',
  'Calabacín',
  foodId: 'f1',
  category: 'VEGETABLES',
  quantity: {'amount': 2, 'unit': 'UNIT'},
);
final _chicken = _line(
  'i2',
  'Pechuga de pollo',
  foodId: 'f2',
  category: 'MEAT',
  quantity: {'amount': 400, 'unit': 'GRAM'},
  origin: 'PLAN',
  neededOn: '2026-10-05',
);
final _batteries = _line('i3', 'Pilas');

const _emptyInventory = {'items': <Object>[], 'page': 0, 'size': 50, 'totalItems': 0, 'totalPages': 0};
const _nothingToConsumeFirst = {
  'counts': {'EXPIRED': 0, 'TODAY': 0, 'URGENT': 0, 'SOON': 0, 'UPCOMING': 0, 'OK': 0, 'NO_DATE': 0},
  'items': <Object>[],
};

void main() {
  late InMemoryTokenStorage storage;

  setUp(() => storage = InMemoryTokenStorage('refresh-1'));

  FakeBackend backendWith([Map<String, FakeHandler> extra = const {}]) => FakeBackend({
    'POST /auth/refresh': (_) => const FakeResponse.ok({'accessToken': 'access-2', 'refreshToken': 'refresh-2'}),
    'GET /users/me': (_) => const FakeResponse.ok(_ana),
    'GET /households': (_) => const FakeResponse.ok([_casa]),
    'GET /households/h1': (_) => const FakeResponse.ok(_casa),
    'GET /households/h1/inventory': (_) => const FakeResponse.ok(_emptyInventory),
    'GET /households/h1/inventory/consume-first': (_) => const FakeResponse.ok(_nothingToConsumeFirst),
    _list: (_) => FakeResponse.ok({
      'items': [_zucchini, _chicken, _batteries],
    }),
    ...extra,
  });

  Future<void> openList(WidgetTester tester, FakeBackend backend) async {
    await pumpFreezify(tester, backend, storage);
    await tester.tap(find.text('Casa'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Lista de la compra'));
    await tester.pumpAndSettle();
  }

  Finder inLine(String id, Finder matching) =>
      find.descendant(of: find.byKey(ValueKey('line-$id')), matching: matching);

  Future<void> lineAction(WidgetTester tester, String id, String action) async {
    await tester.tap(inLine(id, find.byTooltip('Opciones')));
    await tester.pumpAndSettle();
    await tester.tap(find.text(action));
    await tester.pumpAndSettle();
  }

  Future<void> listAction(WidgetTester tester, String action) async {
    await tester.tap(find.byTooltip('Opciones de la lista'));
    await tester.pumpAndSettle();
    await tester.tap(find.text(action));
    await tester.pumpAndSettle();
  }

  group('shopping list', () {
    testWidgets('shows the list aisle by aisle, with amounts and what each plan line is for', (tester) async {
      await openList(tester, backendWith());

      expect(find.text('Verduras'), findsOneWidget);
      expect(find.text('Carne'), findsOneWidget);
      expect(find.text('Otros'), findsOneWidget);
      expect(inLine('i1', find.text('Calabacín · 2 uds')), findsOneWidget);
      expect(inLine('i2', find.text('Pechuga de pollo · 400 g')), findsOneWidget);
      expect(inLine('i2', find.text('Para el plan · lunes 5 oct')), findsOneWidget);
      expect(inLine('i3', find.text('Pilas')), findsOneWidget);
      // The aisles come in the order of the list.
      expect(tester.getTopLeft(find.text('Verduras')).dy, lessThan(tester.getTopLeft(find.text('Carne')).dy));
      expect(tester.getTopLeft(find.text('Carne')).dy, lessThan(tester.getTopLeft(find.text('Otros')).dy));
    });

    testWidgets('says so when the list is empty', (tester) async {
      await openList(
        tester,
        backendWith({
          _list: (_) => const FakeResponse.ok({'items': <Object>[]}),
        }),
      );

      expect(find.textContaining('La lista está vacía'), findsOneWidget);
    });

    testWidgets('ticks off what is bought', (tester) async {
      var checked = false;
      final backend = backendWith({
        _list: (_) => FakeResponse.ok({
          'items': [_line('i1', 'Calabacín', foodId: 'f1', category: 'VEGETABLES', checked: checked)],
        }),
        'PUT /households/h1/shopping-list/items/i1/checked': (call) {
          checked = (call.body! as Map)['checked'] as bool;
          return FakeResponse.ok(_zucchini);
        },
      });
      await openList(tester, backend);

      await tester.tap(inLine('i1', find.byType(Checkbox)));
      await tester.pumpAndSettle();

      expect(backend.last('PUT /households/h1/shopping-list/items/i1/checked').body, {'checked': true});
      expect(tester.widget<Checkbox>(inLine('i1', find.byType(Checkbox))).value, isTrue);
    });

    testWidgets('adds free text without a quantity', (tester) async {
      final backend = backendWith({_add: (call) => FakeResponse(201, call.body)});
      await openList(tester, backend);

      await tester.tap(find.widgetWithText(FloatingActionButton, 'Añadir'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'Qué'), 'Papel de cocina');
      await tester.tap(find.widgetWithText(FilledButton, 'Añadir'));
      await tester.pumpAndSettle();

      expect(backend.last(_add).body, {'foodId': null, 'name': 'Papel de cocina', 'category': null, 'quantity': null});
      expect(find.text('Añadir a la lista'), findsNothing);
    });

    testWidgets('adds a food of the catalog with an amount', (tester) async {
      final backend = backendWith({
        'GET /foods': (_) => const FakeResponse.ok([
          {
            'id': 'f9',
            'name': 'Patatas',
            'category': 'VEGETABLES',
            'defaultUnit': 'KILOGRAM',
            'defaultStorage': 'PANTRY',
          },
        ]),
        _add: (call) => FakeResponse(201, call.body),
      });
      await openList(tester, backend);

      await tester.tap(find.widgetWithText(FloatingActionButton, 'Añadir'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'Qué'), 'pat');
      await tester.pump(const Duration(milliseconds: 400));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(ActionChip, 'Patatas'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'Cantidad (opcional)'), '1,5');
      await tester.tap(find.widgetWithText(FilledButton, 'Añadir'));
      await tester.pumpAndSettle();

      expect(backend.last(_add).body, {
        'foodId': 'f9',
        'name': null,
        'category': null,
        'quantity': {'amount': 1.5, 'unit': 'KILOGRAM'},
      });
    });

    testWidgets('changes how much to buy', (tester) async {
      final backend = backendWith({'PUT /households/h1/shopping-list/items/i2': (call) => FakeResponse.ok(call.body)});
      await openList(tester, backend);

      await lineAction(tester, 'i2', 'Cambiar');
      expect(find.text('Cantidad de Pechuga de pollo'), findsOneWidget);
      await tester.enterText(find.widgetWithText(TextField, 'Cantidad (opcional)'), '500');
      await tester.tap(find.text('Guardar'));
      await tester.pumpAndSettle();

      expect(backend.last('PUT /households/h1/shopping-list/items/i2').body, {
        'foodId': 'f2',
        'name': null,
        'category': 'MEAT',
        'quantity': {'amount': 500, 'unit': 'GRAM'},
      });
    });

    testWidgets('removes a line', (tester) async {
      final backend = backendWith({'DELETE /households/h1/shopping-list/items/i3': (_) => const FakeResponse(204)});
      await openList(tester, backend);

      await lineAction(tester, 'i3', 'Quitar');

      expect(backend.count('DELETE /households/h1/shopping-list/items/i3'), 1);
    });

    testWidgets('asks before taking what was bought off the list', (tester) async {
      final backend = backendWith({
        _list: (_) => FakeResponse.ok({
          'items': [_line('i1', 'Calabacín', category: 'VEGETABLES', checked: true), _batteries],
        }),
        'DELETE /households/h1/shopping-list/items/checked': (_) => const FakeResponse.ok({'removed': 1}),
      });
      await openList(tester, backend);

      await listAction(tester, 'Quitar lo comprado (1)');
      expect(find.text('Se quitará de la lista 1 cosa comprada.'), findsOneWidget);
      await tester.tap(find.text('Cancelar'));
      await tester.pumpAndSettle();
      expect(backend.count('DELETE /households/h1/shopping-list/items/checked'), 0);

      await listAction(tester, 'Quitar lo comprado (1)');
      await tester.tap(find.widgetWithText(TextButton, 'Quitar lo comprado (1)'));
      await tester.pumpAndSettle();
      expect(backend.count('DELETE /households/h1/shopping-list/items/checked'), 1);
    });

    testWidgets('fills the list with what the plan of this week or the next lacks', (tester) async {
      var lines = 3;
      final backend = backendWith({
        _fromPlan: (_) => FakeResponse.ok({'lines': lines}),
      });
      await openList(tester, backend);
      final today = toIsoDay(DateTime.now());

      await listAction(tester, 'Añadir lo que falta para el plan de esta semana');
      expect(find.text('Hay 3 cosas en la lista para el plan de esa semana.'), findsOneWidget);
      expect(backend.last(_fromPlan).body, {'week': today});

      lines = 0;
      await listAction(tester, 'Añadir lo que falta para el plan de la semana que viene');
      expect(find.text('Al plan de esa semana no le falta nada.'), findsOneWidget);
      expect(backend.last(_fromPlan).body, {'week': addDays(today, 7)});
    });

    testWidgets('shows at once what another member changes', (tester) async {
      // Both the inventory underneath and the list listen to the household.
      final events = StreamController<Uint8List>.broadcast();
      addTearDown(events.close);
      var items = [_batteries];
      final backend = backendWith({
        _list: (_) => FakeResponse.ok({'items': items}),
        'GET /households/h1/events': (_) => FakeResponse.stream(events.stream),
      });
      await openList(tester, backend);
      expect(find.text('Calabacín · 2 uds'), findsNothing);

      items = [_zucchini, _batteries];
      events.add(utf8.encode('event:shopping-list-changed\ndata:{}\n\n'));
      await tester.pumpAndSettle();

      expect(find.text('Calabacín · 2 uds'), findsOneWidget);
    });

    testWidgets('explains why something could not be done', (tester) async {
      final backend = backendWith({
        'DELETE /households/h1/shopping-list/items/i3': (_) => FakeResponse.problem(404, 'SHOPPING_ITEM_NOT_FOUND'),
      });
      await openList(tester, backend);

      await lineAction(tester, 'i3', 'Quitar');

      expect(find.text('Eso ya no está en la lista.'), findsOneWidget);
    });
  });
}
