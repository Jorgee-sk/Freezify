import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/app_harness.dart';
import 'support/fake_backend.dart';

const _ana = {'id': 'u1', 'email': 'ana@example.com', 'displayName': 'Ana', 'locale': 'es'};
const _casa = {'id': 'h1', 'name': 'Casa', 'role': 'OWNER', 'memberCount': 1};
const _emptyInventory = {'items': <Object>[], 'page': 0, 'size': 50, 'totalItems': 0, 'totalPages': 0};
const _nothingToConsumeFirst = {
  'counts': {'EXPIRED': 0, 'TODAY': 0, 'URGENT': 0, 'SOON': 0, 'UPCOMING': 0, 'OK': 0, 'NO_DATE': 0},
  'items': <Object>[],
};

const _generate = 'POST /households/h1/recipes/generated';
const _available = 'GET /households/h1/recipes/generated/ingredients';

Map<String, Object?> _atHome(String? foodId, String name, num amount, String unit, int? daysLeft) => {
  'foodId': foodId,
  'name': name,
  'amount': amount,
  'unit': unit,
  'daysLeft': daysLeft,
  'estimated': false,
};

final _foodsAtHome = [
  _atHome('f-zucchini', 'Calabacín', 2, 'UNIT', 1),
  _atHome('f-chicken', 'Pechuga de pollo', 400, 'GRAM', 3),
  _atHome('f-rice', 'Arroz', 1000, 'GRAM', null),
  _atHome('f-eggs', 'Huevos', 6, 'UNIT', 10),
  _atHome(null, 'Salsa de la abuela', 1, 'UNIT', 4),
];

Map<String, Object?> _ingredient(
  String name, {
  num amount = 1,
  String unit = 'UNIT',
  bool optional = false,
  bool staple = false,
  int? daysLeft,
  bool estimated = false,
}) => {
  'foodId': 'food-$name',
  'name': name,
  'amount': amount,
  'unit': unit,
  'optional': optional,
  'staple': staple,
  'daysLeft': daysLeft,
  'priority': null,
  'estimated': estimated,
};

final _recipe = {
  'title': 'Pollo salteado con calabacín',
  'summary': 'Un salteado rápido.',
  'servings': 3,
  'minutes': 20,
  'difficulty': 'EASY',
  'ingredients': [
    _ingredient('Pechuga de pollo', amount: 400, unit: 'GRAM', daysLeft: 3, estimated: true),
    _ingredient('Calabacín', amount: 2, daysLeft: 1),
    _ingredient('Arroz', amount: 150, unit: 'GRAM', optional: true, daysLeft: 200),
    _ingredient('Sal', amount: 2, unit: 'GRAM', staple: true),
  ],
  'steps': ['Corta el pollo y el calabacín.', 'Saltéalos con aceite y sal.'],
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
    'GET /recipes': (_) =>
        const FakeResponse.ok({'items': <Object>[], 'page': 0, 'size': 100, 'totalItems': 0, 'totalPages': 0}),
    'GET /households/h1/diet': (_) => const FakeResponse.ok({'type': 'NONE', 'avoided': <String>[]}),
    'GET /households/h1/recipes/recommendations': (_) => const FakeResponse.ok(<Object>[]),
    'GET /ai': (_) => const FakeResponse.ok({'enabled': true}),
    _generate: (_) => FakeResponse.ok(_recipe),
    _available: (_) => FakeResponse.ok(_foodsAtHome),
    ...extra,
  });

  Future<void> openRecipes(WidgetTester tester, FakeBackend backend) async {
    await pumpFreezify(tester, backend, storage);
    await tester.tap(find.text('Casa'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Recetas'));
    await tester.pumpAndSettle();
  }

  group('recipe written by AI', () {
    testWidgets('is not offered when the server has no model', (tester) async {
      await openRecipes(
        tester,
        backendWith({
          'GET /ai': (_) => const FakeResponse.ok({'enabled': false}),
        }),
      );

      expect(find.text('Qué cocinar con lo que tienes'), findsOneWidget);
      expect(find.text('Crear una receta con lo que tengo'), findsNothing);
    });

    testWidgets('writes a recipe with what is at home and says why it uses each food', (tester) async {
      final backend = backendWith();
      await openRecipes(tester, backend);

      await tester.tap(find.text('Crear una receta con lo que tengo'));
      await tester.pumpAndSettle();
      expect(find.textContaining('solo con los alimentos de tu inventario'), findsOneWidget);

      await tester.tap(find.byType(DropdownButtonFormField<int>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('3').last);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Crear receta'));
      await tester.pumpAndSettle();

      expect(backend.last(_generate).body, {'servings': 3, 'use': <String>[]});
      expect(backend.last(_generate).query['lang'], 'es');
      expect(find.text('Pollo salteado con calabacín'), findsOneWidget);
      expect(find.text('20 min · Fácil · 3 raciones'), findsOneWidget);
      expect(find.textContaining('Receta escrita por IA'), findsOneWidget);
      // The list builds lazily: the ingredients and the steps may be below the test screen.
      await tester.scrollUntilVisible(find.text('Caduca en unos 3 días (estimada)'), 200);
      await tester.scrollUntilVisible(find.text('Caduca en 1 día'), 200);
      // Far from its date: nothing to point out.
      expect(find.textContaining('200 días'), findsNothing);
      await tester.scrollUntilVisible(find.text('150 g · opcional'), 200);
      await tester.scrollUntilVisible(find.text('2 g · básico de cocina'), 200);
      await tester.scrollUntilVisible(find.text('Saltéalos con aceite y sal.'), 200);
      expect(find.text('Corta el pollo y el calabacín.'), findsOneWidget);

      await tester.scrollUntilVisible(find.text('Crear otra'), -200);
      await tester.tap(find.text('Crear otra'));
      await tester.pumpAndSettle();
      expect(backend.count(_generate), 2);
    });

    testWidgets('shows what the AI may use and lets people pick up to three foods it must use', (tester) async {
      final backend = backendWith();
      await openRecipes(tester, backend);
      await tester.tap(find.text('Crear una receta con lo que tengo'));
      await tester.pumpAndSettle();

      expect(find.text('Lo que la IA puede usar'), findsOneWidget);
      expect(find.text('Calabacín · 2 uds · Caduca en 1 día'), findsOneWidget);
      expect(find.text('Arroz · 1000 g'), findsOneWidget);
      FilterChip chip(String label) => tester.widget<FilterChip>(find.widgetWithText(FilterChip, label));
      // Food that is not a catalog food is given to the AI but cannot be required.
      expect(chip('Salsa de la abuela · 1 ud · Caduca en 4 días').onSelected, isNull);

      for (final label in [
        'Calabacín · 2 uds · Caduca en 1 día',
        'Pechuga de pollo · 400 g · Caduca en 3 días',
        'Arroz · 1000 g',
      ]) {
        await tester.tap(find.widgetWithText(FilterChip, label));
        await tester.pumpAndSettle();
      }
      // Three at most.
      expect(chip('Huevos · 6 uds').onSelected, isNull);
      await tester.tap(find.widgetWithText(FilterChip, 'Arroz · 1000 g'));
      await tester.pumpAndSettle();
      expect(chip('Huevos · 6 uds').onSelected, isNotNull);

      await tester.tap(find.text('Crear receta'));
      await tester.pumpAndSettle();
      expect(backend.last(_generate).body, {
        'servings': 2,
        'use': ['f-zucchini', 'f-chicken'],
      });
    });

    testWidgets('says why there is no recipe', (tester) async {
      await openRecipes(tester, backendWith({_generate: (_) => FakeResponse.problem(429, 'AI_LIMIT_REACHED')}));
      await tester.tap(find.text('Crear una receta con lo que tengo'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Crear receta'));
      await tester.pumpAndSettle();

      expect(find.text('Has usado todas las peticiones de IA de hoy. Vuelve a intentarlo mañana.'), findsOneWidget);
      expect(find.textContaining('Receta escrita por IA'), findsNothing);
    });
  });
}
