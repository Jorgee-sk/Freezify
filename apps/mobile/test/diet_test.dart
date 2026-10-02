import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:freezify/features/recipes/diet_models.dart';
import 'package:freezify/features/recipes/recipe_wording.dart';
import 'package:freezify/l10n/app_localizations.dart';

import 'support/app_harness.dart';
import 'support/fake_backend.dart';

const _ana = {'id': 'u1', 'email': 'ana@example.com', 'displayName': 'Ana', 'locale': 'es'};
const _casa = {'id': 'h1', 'name': 'Casa', 'role': 'OWNER', 'memberCount': 2};

const _diet = 'GET /households/h1/diet';
const _save = 'PUT /households/h1/diet';
const _catalog = 'GET /recipes';
const _recommendations = 'GET /households/h1/recipes/recommendations';

const _noDiet = {'type': 'NONE', 'avoided': <String>[]};

const _pasta = {
  'id': 'r1',
  'name': 'Pasta con calabacín y tomate',
  'description': 'Un plato rápido.',
  'servings': 2,
  'prepMinutes': 10,
  'cookMinutes': 15,
  'totalMinutes': 25,
  'difficulty': 'EASY',
  'course': 'MAIN',
  'contains': ['DAIRY', 'EGG', 'GLUTEN'],
};

const _pastaDetail = {
  'recipe': _pasta,
  'ingredients': [
    {'foodId': 'f1', 'name': 'Pasta', 'amount': 200, 'unit': 'GRAM', 'staple': false},
  ],
  'steps': ['Cuece la pasta.'],
};

const _emptyInventory = {'items': <Object>[], 'page': 0, 'size': 50, 'totalItems': 0, 'totalPages': 0};
const _nothingToConsumeFirst = {
  'counts': {'EXPIRED': 0, 'TODAY': 0, 'URGENT': 0, 'SOON': 0, 'UPCOMING': 0, 'OK': 0, 'NO_DATE': 0},
  'items': <Object>[],
};

void main() {
  late InMemoryTokenStorage storage;

  setUp(() => storage = InMemoryTokenStorage('refresh-1'));

  /// A backend with Ana signed in and one recipe in the catalog; [extra] adds or overrides routes.
  FakeBackend backendWith([Map<String, FakeHandler> extra = const {}]) => FakeBackend({
    'POST /auth/refresh': (_) => const FakeResponse.ok({'accessToken': 'access-2', 'refreshToken': 'refresh-2'}),
    'GET /users/me': (_) => const FakeResponse.ok(_ana),
    'GET /households': (_) => const FakeResponse.ok([_casa]),
    'GET /households/h1': (_) => const FakeResponse.ok(_casa),
    'GET /households/h1/inventory': (_) => const FakeResponse.ok(_emptyInventory),
    'GET /households/h1/inventory/consume-first': (_) => const FakeResponse.ok(_nothingToConsumeFirst),
    _catalog: (_) => const FakeResponse.ok({
      'items': [_pasta],
      'page': 0,
      'size': 100,
      'totalItems': 1,
      'totalPages': 1,
    }),
    _recommendations: (_) => const FakeResponse.ok(<Object>[]),
    'GET /recipes/r1': (_) => const FakeResponse.ok(_pastaDetail),
    _diet: (_) => const FakeResponse.ok(_noDiet),
    ...extra,
  });

  Future<void> openRecipes(WidgetTester tester, FakeBackend backend) async {
    await pumpFreezify(tester, backend, storage);
    await tester.tap(find.text('Casa'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Recetas'));
    await tester.pumpAndSettle();
  }

  Future<void> openDiet(WidgetTester tester, FakeBackend backend) async {
    await openRecipes(tester, backend);
    await tester.tap(find.text('Cambiar'));
    await tester.pumpAndSettle();
  }

  Future<void> save(WidgetTester tester) async {
    final button = find.widgetWithText(FilledButton, 'Guardar');
    await tester.ensureVisible(button);
    await tester.pumpAndSettle();
    await tester.tap(button);
    await tester.pumpAndSettle();
  }

  group('dietary restrictions', () {
    testWidgets('says which restrictions the recipes were filtered with', (tester) async {
      final backend = backendWith({
        _diet: (_) => const FakeResponse.ok({
          'type': 'VEGETARIAN',
          'avoided': ['GLUTEN', 'SOY'],
        }),
      });
      await openRecipes(tester, backend);

      expect(find.text('Recetas filtradas para este hogar: Dieta vegetariana · sin gluten, sin soja.'), findsOneWidget);
      // The filtering is the server's: the catalog is asked for on behalf of the household.
      expect(backend.last(_catalog).query['household'], 'h1');
    });

    testWidgets('says so when the household has no restrictions', (tester) async {
      await openRecipes(tester, backendWith());

      expect(find.text('Este hogar no tiene restricciones alimentarias.'), findsOneWidget);
    });

    testWidgets('saves what the household does not eat and asks for the recipes again', (tester) async {
      Object? stored = _noDiet;
      final backend = backendWith({
        _diet: (_) => FakeResponse.ok(stored),
        _save: (call) {
          stored = call.body;
          return FakeResponse.ok(call.body);
        },
      });
      await openDiet(tester, backend);
      final catalogRequests = backend.count(_catalog);

      expect(tester.widget<ChoiceChip>(find.widgetWithText(ChoiceChip, 'Sin dieta')).selected, isTrue);
      await tester.tap(find.widgetWithText(ChoiceChip, 'Vegana'));
      await tester.pumpAndSettle();
      for (final trait in ['Frutos secos', 'Gluten']) {
        final chip = find.widgetWithText(FilterChip, trait);
        await tester.ensureVisible(chip);
        await tester.pumpAndSettle();
        await tester.tap(chip);
        await tester.pumpAndSettle();
      }
      await save(tester);

      expect(find.text('Restricciones guardadas'), findsOneWidget);
      expect(backend.last(_save).body, {
        'type': 'VEGAN',
        'avoided': ['GLUTEN', 'NUTS'],
      });
      // The list of recipes was filtered with the old restrictions: back on it, it is asked for again.
      await tester.tap(find.byType(BackButton));
      await tester.pumpAndSettle();
      expect(backend.count(_catalog), greaterThan(catalogRequests));
      expect(
        find.text('Recetas filtradas para este hogar: Dieta vegana · sin gluten, sin frutos secos.'),
        findsOneWidget,
      );
    });

    testWidgets('shows what was chosen before', (tester) async {
      await openDiet(
        tester,
        backendWith({
          _diet: (_) => const FakeResponse.ok({
            'type': 'VEGETARIAN',
            'avoided': ['EGG'],
          }),
        }),
      );

      expect(tester.widget<ChoiceChip>(find.widgetWithText(ChoiceChip, 'Vegetariana')).selected, isTrue);
      expect(tester.widget<FilterChip>(find.widgetWithText(FilterChip, 'Huevo')).selected, isTrue);
      expect(tester.widget<FilterChip>(find.widgetWithText(FilterChip, 'Gluten')).selected, isFalse);
    });

    testWidgets('warns that the filter is a help and not a guarantee, and that it is shared', (tester) async {
      await openDiet(tester, backendWith());

      expect(find.textContaining('comprueba siempre la etiqueta'), findsOneWidget);
      expect(find.textContaining('cualquier miembro las ve y puede cambiarlas'), findsOneWidget);
    });

    testWidgets('explains why saving failed', (tester) async {
      await openDiet(tester, backendWith({_save: (_) => FakeResponse.problem(404, 'HOUSEHOLD_NOT_FOUND')}));

      await save(tester);

      expect(find.text('Este hogar no existe o ya no perteneces a él.'), findsOneWidget);
      expect(find.text('Restricciones guardadas'), findsNothing);
    });

    testWidgets('says what a recipe contains', (tester) async {
      await openRecipes(tester, backendWith());
      await tester.tap(find.text('Pasta con calabacín y tomate'));
      await tester.pumpAndSettle();

      expect(find.text('Contiene: lácteos, huevo, gluten.'), findsOneWidget);
      expect(find.textContaining('en este hogar no se come'), findsNothing);
    });

    testWidgets('warns when a recipe contains what the household does not eat', (tester) async {
      await openRecipes(
        tester,
        backendWith({
          _diet: (_) => const FakeResponse.ok({
            'type': 'VEGAN',
            'avoided': ['GLUTEN'],
          }),
        }),
      );
      await tester.tap(find.text('Pasta con calabacín y tomate'));
      await tester.pumpAndSettle();

      expect(
        find.text('Esta receta contiene algo que en este hogar no se come: lácteos, huevo, gluten.'),
        findsOneWidget,
      );
    });
  });

  group('diet helpers', () {
    final es = lookupAppLocalizations(const Locale('es'));
    final en = lookupAppLocalizations(const Locale('en'));

    test('summarises the restrictions of a household', () {
      expect(dietSummary(es, const Diet(type: DietType.none, avoided: {})), isNull);
      expect(dietSummary(es, const Diet(type: DietType.vegan, avoided: {})), 'Dieta vegana');
      expect(dietSummary(es, const Diet(type: DietType.none, avoided: {FoodTrait.nuts})), 'sin frutos secos');
      expect(
        dietSummary(es, const Diet(type: DietType.vegetarian, avoided: {FoodTrait.sesame, FoodTrait.gluten})),
        'Dieta vegetariana · sin gluten, sin sésamo',
      );
      expect(
        dietSummary(en, const Diet(type: DietType.vegetarian, avoided: {FoodTrait.gluten})),
        'Vegetarian diet · no gluten',
      );
    });

    test('finds what a recipe contains that the household does not eat', () {
      const none = Diet(type: DietType.none, avoided: {});
      const vegetarian = Diet(type: DietType.vegetarian, avoided: {});
      const vegan = Diet(type: DietType.vegan, avoided: {});
      const noGluten = Diet(type: DietType.none, avoided: {FoodTrait.gluten});

      expect(none.conflictsWith([FoodTrait.meat, FoodTrait.dairy]), isEmpty);
      expect(vegetarian.conflictsWith([FoodTrait.meat, FoodTrait.pork, FoodTrait.dairy]), [
        FoodTrait.meat,
        FoodTrait.pork,
      ]);
      expect(vegan.conflictsWith([FoodTrait.dairy, FoodTrait.egg, FoodTrait.gluten]), [FoodTrait.dairy, FoodTrait.egg]);
      expect(noGluten.conflictsWith([FoodTrait.dairy, FoodTrait.gluten]), [FoodTrait.gluten]);
      expect(vegan.conflictsWith(const []), isEmpty);
    });

    test('sends the avoided traits in a stable order', () {
      expect(const Diet(type: DietType.vegan, avoided: {FoodTrait.soy, FoodTrait.gluten}).toJson(), {
        'type': 'VEGAN',
        'avoided': ['GLUTEN', 'SOY'],
      });
    });
  });
}
