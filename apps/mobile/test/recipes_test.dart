import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:freezify/features/recipes/recipe_models.dart';
import 'package:freezify/features/recipes/recipe_wording.dart';
import 'package:freezify/l10n/app_localizations.dart';

import 'support/app_harness.dart';
import 'support/fake_backend.dart';

const _ana = {'id': 'u1', 'email': 'ana@example.com', 'displayName': 'Ana', 'locale': 'es'};
const _casa = {'id': 'h1', 'name': 'Casa', 'role': 'OWNER', 'memberCount': 2};

const _catalog = 'GET /recipes';
const _recommendations = 'GET /households/h1/recipes/recommendations';
const _detail = 'GET /recipes/r1';
const _cooked = 'POST /households/h1/recipes/r1/cooked';

const _pastaName = 'Pasta con calabacín y tomate';

Map<String, Object?> _recipe(
  String id,
  String name, {
  int minutes = 25,
  String difficulty = 'EASY',
  int servings = 2,
}) => {
  'id': id,
  'name': name,
  'description': 'Descripción de $name',
  'servings': servings,
  'prepMinutes': 10,
  'cookMinutes': minutes - 10,
  'totalMinutes': minutes,
  'difficulty': difficulty,
  'course': 'MAIN',
  'contains': <String>[],
};

Map<String, Object?> _matched(
  String name, {
  num amount = 1,
  String unit = 'UNIT',
  String availability = 'ENOUGH',
  bool staple = false,
  int? days,
  bool estimated = false,
}) => {
  'foodId': 'food-$name',
  'name': name,
  'amount': amount,
  'unit': unit,
  'staple': staple,
  'availability': availability,
  'expirationDate': days == null ? null : '2026-10-04',
  'estimated': estimated,
  'daysUntilExpiration': days,
  'priority': days == null ? null : 'URGENT',
};

final _pasta = _recipe('r1', _pastaName);
final _tortilla = _recipe('r2', 'Tortilla de patatas', minutes: 40, difficulty: 'MEDIUM', servings: 4);

final _pastaIngredients = [
  _matched('Pasta', amount: 200, unit: 'GRAM'),
  _matched('Calabacín', days: 2),
  _matched('Mozzarella', amount: 100, unit: 'GRAM', availability: 'MISSING'),
  _matched('Sal', amount: 3, unit: 'GRAM', staple: true, availability: 'ASSUMED'),
];

Map<String, Object?> _recommendation(List<Map<String, Object?>> ingredients, {int? daysSinceCooked}) => {
  'recipe': _pasta,
  'score': 0.763,
  'factors': {'ingredientMatch': 0.75, 'expiryUrgency': 0.5, 'convenience': 0.9, 'novelty': 1.0},
  'ingredients': ingredients,
  'daysSinceCooked': daysSinceCooked,
};

final _pastaDetail = {
  'recipe': _pasta,
  'ingredients': [
    for (final ingredient in _pastaIngredients)
      {
        'foodId': ingredient['foodId'],
        'name': ingredient['name'],
        'amount': ingredient['amount'],
        'unit': ingredient['unit'],
        'staple': ingredient['staple'],
      },
  ],
  'steps': ['Cuece la pasta en agua con sal.', 'Saltea el calabacín.'],
};

FakeResponse _page(List<Map<String, Object?>> items) =>
    FakeResponse.ok({'items': items, 'page': 0, 'size': 100, 'totalItems': items.length, 'totalPages': 1});

const _emptyInventory = {'items': <Object>[], 'page': 0, 'size': 50, 'totalItems': 0, 'totalPages': 0};
const _nothingToConsumeFirst = {
  'counts': {'EXPIRED': 0, 'TODAY': 0, 'URGENT': 0, 'SOON': 0, 'UPCOMING': 0, 'OK': 0, 'NO_DATE': 0},
  'items': <Object>[],
};

void main() {
  late InMemoryTokenStorage storage;

  setUp(() => storage = InMemoryTokenStorage('refresh-1'));

  /// A backend with Ana signed in and the pasta recommended to her household; [extra] adds or overrides routes.
  FakeBackend backendWith([Map<String, FakeHandler> extra = const {}]) => FakeBackend({
    'POST /auth/refresh': (_) => const FakeResponse.ok({'accessToken': 'access-2', 'refreshToken': 'refresh-2'}),
    'GET /users/me': (_) => const FakeResponse.ok(_ana),
    'GET /households': (_) => const FakeResponse.ok([_casa]),
    'GET /households/h1': (_) => const FakeResponse.ok(_casa),
    'GET /households/h1/inventory': (_) => const FakeResponse.ok(_emptyInventory),
    'GET /households/h1/inventory/consume-first': (_) => const FakeResponse.ok(_nothingToConsumeFirst),
    _catalog: (_) => _page([_tortilla]),
    'GET /households/h1/diet': (_) => const FakeResponse.ok({'type': 'NONE', 'avoided': <String>[]}),
    _recommendations: (_) => FakeResponse.ok([_recommendation(_pastaIngredients)]),
    _detail: (_) => FakeResponse.ok(_pastaDetail),
    ...extra,
  });

  Future<void> openRecipes(WidgetTester tester, FakeBackend backend) async {
    await pumpFreezify(tester, backend, storage);
    await tester.tap(find.text('Casa'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Recetas'));
    await tester.pumpAndSettle();
  }

  Future<void> openPasta(WidgetTester tester, FakeBackend backend) async {
    await openRecipes(tester, backend);
    await tester.tap(find.text(_pastaName));
    await tester.pumpAndSettle();
  }

  group('recipes', () {
    testWidgets('recommends what to cook and says why, from what is at home', (tester) async {
      final backend = backendWith();
      await openRecipes(tester, backend);

      expect(find.text('Qué cocinar con lo que tienes'), findsOneWidget);
      expect(find.text(_pastaName), findsOneWidget);
      expect(find.text('Encaje 76 %'), findsOneWidget);
      expect(find.text('• Tienes calabacín con caducidad en 2 días.'), findsOneWidget);
      expect(find.text('• Tienes 2 de 3 ingredientes.'), findsOneWidget);
      expect(find.text('• Solo te falta: mozzarella.'), findsOneWidget);
      expect(find.text('• Tiempo aproximado: 25 min.'), findsOneWidget);
      expect(backend.last(_recommendations).query, {'lang': 'es', 'limit': '50'});
    });

    testWidgets('says so when nothing at home can be cooked, and still lists the catalog', (tester) async {
      await openRecipes(tester, backendWith({_recommendations: (_) => const FakeResponse.ok(<Object>[])}));

      expect(find.textContaining('Ninguna receta usa lo que hay en tu inventario'), findsOneWidget);
      expect(find.text('Tortilla de patatas'), findsOneWidget);
      expect(find.text('40 min · Media · 4 raciones'), findsOneWidget);
    });

    testWidgets('filters the catalog by text, course and time', (tester) async {
      final backend = backendWith({_recommendations: (_) => const FakeResponse.ok(<Object>[])});
      await openRecipes(tester, backend);
      expect(backend.last(_catalog).query, {'household': 'h1', 'lang': 'es', 'size': '100'});

      await tester.enterText(find.byType(TextField), 'tort');
      await tester.pump(const Duration(milliseconds: 400));
      await tester.pumpAndSettle();
      expect(backend.last(_catalog).query['q'], 'tort');

      await tester.tap(find.widgetWithText(ChoiceChip, 'Postre'));
      await tester.pumpAndSettle();
      expect(backend.last(_catalog).query['course'], 'DESSERT');

      final halfAnHour = find.widgetWithText(FilterChip, 'Hasta 30 min');
      await tester.ensureVisible(halfAnHour);
      await tester.pumpAndSettle();
      await tester.tap(halfAnHour);
      await tester.pumpAndSettle();
      expect(backend.last(_catalog).query, {
        'household': 'h1',
        'lang': 'es',
        'size': '100',
        'q': 'tort',
        'course': 'DESSERT',
        'maxMinutes': '30',
      });
    });

    testWidgets('says so when no recipe matches the filter', (tester) async {
      await openRecipes(
        tester,
        backendWith({_recommendations: (_) => const FakeResponse.ok(<Object>[]), _catalog: (_) => _page([])}),
      );

      expect(find.text('Ninguna receta coincide con el filtro.'), findsOneWidget);
    });

    testWidgets('explains a household the user cannot access', (tester) async {
      await openRecipes(
        tester,
        backendWith({_recommendations: (_) => FakeResponse.problem(404, 'HOUSEHOLD_NOT_FOUND')}),
      );

      expect(find.text('Este hogar no existe o ya no perteneces a él.'), findsOneWidget);
    });
  });

  group('recipe detail', () {
    testWidgets('shows ingredients with what the household has of each, and the steps', (tester) async {
      await openPasta(tester, backendWith());

      expect(find.text('25 min · Fácil · 2 raciones'), findsOneWidget);
      expect(find.text('Pasta · 200 g'), findsOneWidget);
      expect(find.text('Calabacín · 1 ud'), findsOneWidget);
      expect(find.text('Caduca en 2 días'), findsOneWidget);
      expect(find.text('Mozzarella · 100 g'), findsOneWidget);
      expect(find.text('Te falta'), findsOneWidget);
      expect(find.text('Lo tienes'), findsNWidgets(2));
      // A staple is listed with its amount, and nothing is said about having it or not.
      expect(find.text('Sal · 3 g · básico de cocina'), findsOneWidget);
      expect(find.text('1. Cuece la pasta en agua con sal.'), findsOneWidget);
      expect(find.text('2. Saltea el calabacín.'), findsOneWidget);
    });

    testWidgets('labels an estimated date as an estimate', (tester) async {
      await openPasta(
        tester,
        backendWith({
          _recommendations: (_) => FakeResponse.ok([
            _recommendation([
              _matched('Pasta', amount: 200, unit: 'GRAM', days: 0, estimated: true),
              _matched('Calabacín', days: 3, estimated: true),
            ]),
          ]),
        }),
      );

      expect(find.text('Caduca hoy (estimada)'), findsOneWidget);
      expect(find.text('Caduca en unos 3 días (estimada)'), findsOneWidget);
    });

    testWidgets('records that the household cooked it, without touching the inventory', (tester) async {
      final backend = backendWith({_cooked: (_) => const FakeResponse(204)});
      await openPasta(tester, backend);
      final asked = backend.count(_recommendations);

      final button = find.widgetWithText(FilledButton, 'La he cocinado');
      await tester.ensureVisible(button);
      await tester.pumpAndSettle();
      await tester.tap(button);
      await tester.pumpAndSettle();

      expect(find.text('Anotado'), findsOneWidget);
      expect(backend.count(_cooked), 1);
      // The recommendations are asked for again: this recipe now ranks lower.
      expect(backend.count(_recommendations), greaterThan(asked));
      expect(
        backend.calls.where((call) => call.route.startsWith('POST') && call.route.contains('/inventory')),
        isEmpty,
      );
      expect(find.textContaining('No cambia tu inventario'), findsOneWidget);
    });

    testWidgets('explains why it could not be recorded', (tester) async {
      await openPasta(tester, backendWith({_cooked: (_) => FakeResponse.problem(404, 'RECIPE_NOT_FOUND')}));

      final button = find.widgetWithText(FilledButton, 'La he cocinado');
      await tester.ensureVisible(button);
      await tester.pumpAndSettle();
      await tester.tap(button);
      await tester.pumpAndSettle();

      expect(find.text('Esta receta no existe.'), findsOneWidget);
      expect(find.text('Anotado'), findsNothing);
    });

    testWidgets('explains a recipe that does not exist', (tester) async {
      await openPasta(tester, backendWith({_detail: (_) => FakeResponse.problem(404, 'RECIPE_NOT_FOUND')}));

      expect(find.text('Esta receta no existe.'), findsOneWidget);
    });
  });

  group('wording', () {
    final es = lookupAppLocalizations(const Locale('es'));
    final en = lookupAppLocalizations(const Locale('en'));

    MatchedIngredient ingredient(
      String name, {
      Availability availability = Availability.enough,
      int? days,
      bool estimated = false,
    }) => MatchedIngredient(
      foodId: name,
      name: name,
      staple: availability == Availability.assumed,
      availability: availability,
      estimated: estimated,
      daysUntilExpiration: days,
    );

    Recommendation recommendation(List<MatchedIngredient> ingredients, {int? daysSinceCooked}) => Recommendation(
      recipe: const RecipeSummary(
        id: 'r1',
        name: 'Pasta',
        description: '',
        servings: 2,
        totalMinutes: 25,
        difficulty: Difficulty.easy,
        course: Course.main,
        contains: [],
      ),
      score: 0.8,
      ingredients: ingredients,
      daysSinceCooked: daysSinceCooked,
    );

    test('explains a recommendation with the food that is really at home', () {
      final lines = recommendationReasons(
        es,
        recommendation([
          ingredient('Pasta'),
          ingredient('Calabacín', days: 2),
          ingredient('Tomate', days: 30),
          ingredient('Mozzarella', availability: Availability.missing),
          ingredient('Sal', availability: Availability.assumed),
        ]),
      );

      expect(lines, [
        'Tienes calabacín con caducidad en 2 días.',
        'Tienes 3 de 4 ingredientes.',
        'Solo te falta: mozzarella.',
        'Tiempo aproximado: 25 min.',
      ]);
    });

    test('puts what expires first at the top and words estimates as estimates', () {
      final lines = recommendationReasons(
        es,
        recommendation([
          ingredient('Tomate', days: 4),
          ingredient('Merluza', days: 1, estimated: true),
          ingredient('Leche', days: 0),
          ingredient('Yogur', days: 0, estimated: true),
        ]),
      );

      expect(lines.take(5), [
        'Tienes leche con caducidad hoy.',
        'Tienes yogur con caducidad estimada hoy.',
        'Tienes merluza con caducidad estimada en 1 día.',
        'Tienes tomate con caducidad en 4 días.',
        'Tienes todos los ingredientes.',
      ]);
    });

    test('names a few missing ingredients and counts many', () {
      final few = recommendationReasons(
        es,
        recommendation([
          ingredient('Pasta'),
          ingredient('Atún en lata', availability: Availability.missing),
          ingredient('Huevos', availability: Availability.missing),
        ]),
      );
      expect(few, contains('Tienes 1 de 3 ingredientes.'));
      expect(few, contains('Te faltan: atún en lata, huevos.'));

      final many = recommendationReasons(
        es,
        recommendation([
          ingredient('Calabacín'),
          for (final name in ['Berenjena', 'Pimiento rojo', 'Pimiento verde', 'Cebolla'])
            ingredient(name, availability: Availability.missing),
        ]),
      );
      expect(many, contains('Te faltan 4 ingredientes.'));
    });

    test('says when there is less than needed, when amounts cannot be compared and when it was cooked', () {
      final lines = recommendationReasons(
        es,
        recommendation([
          ingredient('Pasta', availability: Availability.partial),
          ingredient('Tomate', availability: Availability.unknownQuantity),
        ], daysSinceCooked: 3),
      );

      expect(lines, contains('Tienes menos pasta de lo que pide la receta.'));
      expect(lines, contains('Comprueba la cantidad de tomate: no podemos compararla con la de la receta.'));
      expect(lines, contains('La cocinaste hace 3 días.'));
      expect(
        recommendationReasons(es, recommendation([ingredient('Pasta')], daysSinceCooked: 0)),
        contains('La has cocinado hoy.'),
      );
    });

    test('never says a missing ingredient is about to expire', () {
      final missing = ingredient('Leche', availability: Availability.missing, days: 1);

      expect(missing.expiresSoon, isFalse);
      expect(expiryBadge(es, missing), isNull);
      expect(
        recommendationReasons(es, recommendation([ingredient('Pasta'), missing])).join(' '),
        isNot(contains('caducidad')),
      );
    });

    test('follows the language of the app', () {
      expect(
        recommendationReasons(
          en,
          recommendation([
            ingredient('Zucchini', days: 1, estimated: true),
            ingredient('Mozzarella', availability: Availability.missing),
          ]),
        ),
        [
          'You have zucchini estimated to expire in about 1 day.',
          'You have 1 of 2 ingredients.',
          'You only need: mozzarella.',
          'About 25 min.',
        ],
      );
    });
  });
}
