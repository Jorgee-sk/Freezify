import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:freezify/features/plan/plan_models.dart';
import 'package:freezify/features/plan/plan_wording.dart';
import 'package:freezify/l10n/app_localizations.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'support/app_harness.dart';
import 'support/fake_backend.dart';

const _ana = {'id': 'u1', 'email': 'ana@example.com', 'displayName': 'Ana', 'locale': 'es'};
const _casa = {'id': 'h1', 'name': 'Casa', 'role': 'OWNER', 'memberCount': 2};

const _plan = 'GET /households/h1/meal-plan';
const _generate = 'POST /households/h1/meal-plan/generate';
const _catalog = 'GET /recipes';
const _mondayLunch = '/households/h1/meal-plan/2026-10-05/LUNCH';

const _pastaName = 'Pasta con calabacín y tomate';
const _chickenName = 'Pollo a la plancha con brócoli';

Map<String, Object?> _recipe(String id, String name) => {
  'id': id,
  'name': name,
  'description': '',
  'servings': 2,
  'prepMinutes': 10,
  'cookMinutes': 15,
  'totalMinutes': 25,
  'difficulty': 'EASY',
  'course': 'MAIN',
  'contains': <String>[],
};

Map<String, Object?> _ingredient(
  String name, {
  String availability = 'ENOUGH',
  bool staple = false,
  String? expires,
  bool estimated = false,
}) => {
  'foodId': 'food-$name',
  'name': name,
  'amount': 1,
  'unit': 'UNIT',
  'staple': staple,
  'availability': availability,
  'expirationDate': expires,
  'estimated': estimated,
};

Map<String, Object?> _meal(
  String date,
  String slot,
  String recipeId,
  String recipeName, {
  String origin = 'MANUAL',
  List<Map<String, Object?>> ingredients = const [],
}) => {
  'id': 'meal-$date-$slot',
  'date': date,
  'slot': slot,
  'origin': origin,
  'recipe': {
    'id': recipeId,
    'name': recipeName,
    'servings': 2,
    'totalMinutes': 25,
    'difficulty': 'EASY',
    'contains': <String>[],
  },
  'ingredients': ingredients,
};

/// Monday's lunch, chosen by a member: the zucchini is at home with an estimated date, the mozzarella is not.
final _pasta = _meal(
  '2026-10-05',
  'LUNCH',
  'r1',
  _pastaName,
  ingredients: [
    _ingredient('Calabacín', expires: '2026-10-07', estimated: true),
    _ingredient('Mozzarella', availability: 'MISSING'),
    _ingredient('Sal', availability: 'ASSUMED', staple: true),
  ],
);

/// Tuesday's dinner, chosen by the generator.
final _chicken = _meal('2026-10-06', 'DINNER', 'r2', _chickenName, origin: 'GENERATED');

Map<String, Object?> _week({
  String weekStart = '2026-10-05',
  String weekEnd = '2026-10-11',
  List<Object?> meals = const [],
  List<Object?> unused = const [],
}) => {'weekStart': weekStart, 'weekEnd': weekEnd, 'today': '2026-10-05', 'meals': meals, 'unusedExpiring': unused};

const _emptyInventory = {'items': <Object>[], 'page': 0, 'size': 50, 'totalItems': 0, 'totalPages': 0};
const _nothingToConsumeFirst = {
  'counts': {'EXPIRED': 0, 'TODAY': 0, 'URGENT': 0, 'SOON': 0, 'UPCOMING': 0, 'OK': 0, 'NO_DATE': 0},
  'items': <Object>[],
};

void main() {
  late InMemoryTokenStorage storage;

  setUp(() => storage = InMemoryTokenStorage('refresh-1'));

  /// A backend with Ana signed in, an empty week and two recipes to choose from; [extra] adds or overrides
  /// routes.
  FakeBackend backendWith([Map<String, FakeHandler> extra = const {}]) => FakeBackend({
    'POST /auth/refresh': (_) => const FakeResponse.ok({'accessToken': 'access-2', 'refreshToken': 'refresh-2'}),
    'GET /users/me': (_) => const FakeResponse.ok(_ana),
    'GET /households': (_) => const FakeResponse.ok([_casa]),
    'GET /households/h1': (_) => const FakeResponse.ok(_casa),
    'GET /households/h1/inventory': (_) => const FakeResponse.ok(_emptyInventory),
    'GET /households/h1/inventory/consume-first': (_) => const FakeResponse.ok(_nothingToConsumeFirst),
    _plan: (_) => FakeResponse.ok(_week()),
    _catalog: (_) => FakeResponse.ok({
      'items': [_recipe('r1', _pastaName), _recipe('r3', 'Tortilla de patatas')],
      'page': 0,
      'size': 100,
      'totalItems': 2,
      'totalPages': 1,
    }),
    ...extra,
  });

  Future<void> openPlan(WidgetTester tester, FakeBackend backend) async {
    await pumpFreezify(tester, backend, storage);
    await tester.tap(find.text('Casa'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Plan de la semana'));
    await tester.pumpAndSettle();
  }

  Finder inMeal(String date, String slot, Finder matching) =>
      find.descendant(of: find.byKey(ValueKey('meal-$date-$slot')), matching: matching);

  Finder inDay(String date, Finder matching) =>
      find.descendant(of: find.byKey(ValueKey('day-$date')), matching: matching);

  /// Scrolls to a widget of the week and taps it.
  Future<void> tapInWeek(WidgetTester tester, Finder finder) async {
    await tester.ensureVisible(finder);
    await tester.pumpAndSettle();
    await tester.tap(finder);
    await tester.pumpAndSettle();
  }

  Future<void> openMealMenu(WidgetTester tester, String date, String slot, String action) async {
    await tapInWeek(tester, inMeal(date, slot, find.byTooltip('Opciones de la comida')));
    await tester.tap(find.text(action));
    await tester.pumpAndSettle();
  }

  group('meal plan', () {
    testWidgets('shows the week from Monday to Sunday with lunch and dinner every day', (tester) async {
      final backend = backendWith();
      await openPlan(tester, backend);

      expect(find.text('5 oct – 11 oct'), findsOneWidget);
      for (final day in [
        'lunes 5 oct',
        'martes 6 oct',
        'miércoles 7 oct',
        'jueves 8 oct',
        'viernes 9 oct',
        'sábado 10 oct',
        'domingo 11 oct',
      ]) {
        expect(find.text(day), findsOneWidget);
      }
      expect(find.text('Comida'), findsNWidgets(7));
      expect(find.text('Cena'), findsNWidgets(7));
      expect(find.text('Nada planificado'), findsNWidgets(14));
      expect(inDay('2026-10-05', find.text('Hoy')), findsOneWidget);
      expect(find.text('Hoy'), findsOneWidget);
      // Without a week asked for, the server decides which one is today's.
      expect(backend.last(_plan).query.containsKey('week'), isFalse);
      expect(backend.last(_plan).query['lang'], 'es');
    });

    testWidgets('says what each meal saves from expiring and what is missing, estimates worded as estimates', (
      tester,
    ) async {
      await openPlan(
        tester,
        backendWith({
          _plan: (_) => FakeResponse.ok(_week(meals: [_pasta, _chicken])),
        }),
      );

      expect(inMeal('2026-10-05', 'LUNCH', find.text(_pastaName)), findsOneWidget);
      expect(inMeal('2026-10-05', 'LUNCH', find.text('25 min · Fácil')), findsOneWidget);
      expect(
        inMeal('2026-10-05', 'LUNCH', find.text('• Aprovecha calabacín, con caducidad estimada el 7 oct.')),
        findsOneWidget,
      );
      expect(inMeal('2026-10-05', 'LUNCH', find.text('• Falta por comprar: mozzarella.')), findsOneWidget);
      // Only what the generator chose is marked as a suggestion.
      expect(inMeal('2026-10-05', 'LUNCH', find.text('Propuesta')), findsNothing);
      expect(inMeal('2026-10-06', 'DINNER', find.text('Propuesta')), findsOneWidget);
      expect(find.text('Nada planificado'), findsNWidgets(12));
    });

    testWidgets('points out the food the plan lets expire', (tester) async {
      await openPlan(
        tester,
        backendWith({
          _plan: (_) => FakeResponse.ok(
            _week(
              unused: [
                {'name': 'Yogur', 'amount': 4, 'unit': 'UNIT', 'expirationDate': '2026-10-06', 'estimated': false},
                {
                  'name': 'Pechuga de pollo',
                  'amount': 300,
                  'unit': 'GRAM',
                  'expirationDate': '2026-10-07',
                  'estimated': true,
                },
              ],
            ),
          ),
        }),
      );

      expect(find.text('El plan deja caducar'), findsOneWidget);
      expect(find.text('• Yogur (4 uds), con caducidad el 6 oct'), findsOneWidget);
      expect(find.text('• Pechuga de pollo (300 g), con caducidad estimada el 7 oct'), findsOneWidget);
    });

    testWidgets('does not show the warning when the plan lets nothing expire', (tester) async {
      await openPlan(tester, backendWith());

      expect(find.text('El plan deja caducar'), findsNothing);
    });

    testWidgets('chooses a recipe for an empty meal', (tester) async {
      var meals = <Object?>[];
      final backend = backendWith({
        _plan: (_) => FakeResponse.ok(_week(meals: meals)),
        'PUT $_mondayLunch': (_) {
          meals = [_pasta];
          return const FakeResponse(204);
        },
      });
      await openPlan(tester, backend);

      await tapInWeek(tester, inMeal('2026-10-05', 'LUNCH', find.text('Elegir receta')));
      expect(find.text('Elige una receta'), findsOneWidget);
      expect(find.text('Tortilla de patatas'), findsOneWidget);
      await tester.tap(find.text(_pastaName));
      await tester.pumpAndSettle();

      expect(backend.last('PUT $_mondayLunch').body, {'recipeId': 'r1'});
      // Only recipes the household eats are offered.
      expect(backend.last(_catalog).query['household'], 'h1');
      expect(find.text('Elige una receta'), findsNothing);
      expect(inMeal('2026-10-05', 'LUNCH', find.text(_pastaName)), findsOneWidget);
      expect(find.text('Nada planificado'), findsNWidgets(13));
    });

    testWidgets('replaces the recipe of a meal', (tester) async {
      final backend = backendWith({
        _plan: (_) => FakeResponse.ok(_week(meals: [_pasta])),
        'PUT $_mondayLunch': (_) => const FakeResponse(204),
      });
      await openPlan(tester, backend);

      await openMealMenu(tester, '2026-10-05', 'LUNCH', 'Cambiar');
      await tester.tap(find.text('Tortilla de patatas'));
      await tester.pumpAndSettle();

      expect(backend.last('PUT $_mondayLunch').body, {'recipeId': 'r3'});
    });

    testWidgets('removes a meal', (tester) async {
      var meals = <Object?>[_pasta];
      final backend = backendWith({
        _plan: (_) => FakeResponse.ok(_week(meals: meals)),
        'DELETE $_mondayLunch': (_) {
          meals = [];
          return const FakeResponse(204);
        },
      });
      await openPlan(tester, backend);

      await openMealMenu(tester, '2026-10-05', 'LUNCH', 'Quitar');

      expect(backend.count('DELETE $_mondayLunch'), 1);
      expect(find.text(_pastaName), findsNothing);
      expect(find.text('Nada planificado'), findsNWidgets(14));
    });

    testWidgets('moves a meal, saying which one it would swap with', (tester) async {
      final backend = backendWith({
        _plan: (_) => FakeResponse.ok(_week(meals: [_pasta, _chicken])),
        'POST $_mondayLunch/move': (_) => const FakeResponse(204),
      });
      await openPlan(tester, backend);

      await openMealMenu(tester, '2026-10-05', 'LUNCH', 'Mover');
      expect(find.text('Mover a'), findsOneWidget);
      // A meal cannot be moved to where it already is.
      expect(find.text('lunes 5 oct · Comida'), findsNothing);
      expect(find.text('lunes 5 oct · Cena'), findsOneWidget);
      expect(find.text('Se intercambia con $_chickenName'), findsOneWidget);
      await tester.tap(find.text('martes 6 oct · Cena'));
      await tester.pumpAndSettle();

      expect(backend.last('POST $_mondayLunch/move').body, {'date': '2026-10-06', 'slot': 'DINNER'});
      expect(find.text('Mover a'), findsNothing);
    });

    testWidgets('fills the empty meals and says how many it planned and how many stay empty', (tester) async {
      final backend = backendWith({
        _generate: (_) => const FakeResponse.ok({'filled': 4, 'unfilled': 10}),
      });
      await openPlan(tester, backend);
      final requestsBefore = backend.count(_plan);
      // Nothing was suggested before, so there is nothing to suggest again.
      expect(find.text('Rehacer la propuesta'), findsNothing);

      await tester.tap(find.widgetWithText(FilledButton, 'Rellenar los huecos'));
      await tester.pumpAndSettle();

      expect(
        find.text(
          'Se han planificado 4 comidas. '
          '10 comidas se quedan vacías: no hay más recetas que encajen sin repetir demasiado.',
        ),
        findsOneWidget,
      );
      expect(backend.last(_generate).body, {'week': '2026-10-05', 'replaceGenerated': false});
      // The plan is asked for again: it has changed.
      expect(backend.count(_plan), greaterThan(requestsBefore));
    });

    testWidgets('asks before replacing what was suggested', (tester) async {
      final backend = backendWith({
        _plan: (_) => FakeResponse.ok(_week(meals: [_pasta, _chicken])),
        _generate: (_) => const FakeResponse.ok({'filled': 1, 'unfilled': 0}),
      });
      await openPlan(tester, backend);

      await tester.tap(find.widgetWithText(OutlinedButton, 'Rehacer la propuesta'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Las que elegiste tú se mantienen'), findsOneWidget);
      await tester.tap(find.text('Cancelar'));
      await tester.pumpAndSettle();
      expect(backend.count(_generate), 0);

      await tester.tap(find.widgetWithText(OutlinedButton, 'Rehacer la propuesta'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(TextButton, 'Rehacer la propuesta'));
      await tester.pumpAndSettle();

      expect(find.text('Se ha planificado 1 comida.'), findsOneWidget);
      expect(backend.last(_generate).body, {'week': '2026-10-05', 'replaceGenerated': true});
    });

    testWidgets('moves between weeks and does not offer to plan one that is over', (tester) async {
      final backend = backendWith({
        _plan: (call) => FakeResponse.ok(
          call.query['week'] == '2026-09-28' ? _week(weekStart: '2026-09-28', weekEnd: '2026-10-04') : _week(),
        ),
      });
      await openPlan(tester, backend);
      expect(find.text('Esta semana'), findsNothing);

      await tester.tap(find.byTooltip('Semana anterior'));
      await tester.pumpAndSettle();

      expect(backend.last(_plan).query['week'], '2026-09-28');
      expect(find.text('Esta semana ya ha pasado.'), findsOneWidget);
      expect(find.text('Rellenar los huecos'), findsNothing);
      expect(find.text('5 oct – 11 oct'), findsNothing);

      await tester.tap(find.byTooltip('Semana siguiente'));
      await tester.pumpAndSettle();
      expect(backend.last(_plan).query['week'], '2026-10-05');
      expect(find.text('5 oct – 11 oct'), findsOneWidget);

      await tester.tap(find.text('Esta semana'));
      await tester.pumpAndSettle();
      expect(backend.last(_plan).query.containsKey('week'), isFalse);
      expect(find.text('Esta semana'), findsNothing);
    });

    testWidgets('shows what another member changes without reloading', (tester) async {
      // Both the inventory underneath and the plan listen to the household.
      final events = StreamController<Uint8List>.broadcast();
      addTearDown(events.close);
      var meals = <Object?>[];
      final backend = backendWith({
        _plan: (_) => FakeResponse.ok(_week(meals: meals)),
        'GET /households/h1/events': (_) => FakeResponse.stream(events.stream),
      });
      await openPlan(tester, backend);
      expect(find.text(_pastaName), findsNothing);

      meals = [_pasta];
      events.add(utf8.encode('event:meal-plan-changed\ndata:{}\n\n'));
      await tester.pumpAndSettle();
      expect(inMeal('2026-10-05', 'LUNCH', find.text(_pastaName)), findsOneWidget);

      // What each meal will find at home depends on the inventory too.
      final requestsBefore = backend.count(_plan);
      events.add(utf8.encode('event:inventory-changed\ndata:{}\n\n'));
      await tester.pumpAndSettle();
      expect(backend.count(_plan), requestsBefore + 1);
    });

    testWidgets('explains why something could not be done', (tester) async {
      final backend = backendWith({
        _plan: (_) => FakeResponse.ok(_week(meals: [_pasta])),
        'DELETE $_mondayLunch': (_) => FakeResponse.problem(404, 'MEAL_NOT_FOUND'),
        _generate: (_) => FakeResponse.problem(409, 'WEEK_IN_THE_PAST'),
      });
      await openPlan(tester, backend);

      await openMealMenu(tester, '2026-10-05', 'LUNCH', 'Quitar');
      expect(find.text('Esa comida ya no está en el plan.'), findsOneWidget);

      await tapInWeek(tester, find.widgetWithText(FilledButton, 'Rellenar los huecos'));
      expect(find.text('No se puede planificar una semana que ya ha pasado.'), findsOneWidget);
    });

    testWidgets('says so when the plan cannot be loaded', (tester) async {
      await openPlan(tester, backendWith({_plan: (_) => FakeResponse.problem(404, 'HOUSEHOLD_NOT_FOUND')}));

      expect(find.text('Este hogar no existe o ya no perteneces a él.'), findsOneWidget);
    });
  });

  group('plan helpers', () {
    final es = lookupAppLocalizations(const Locale('es'));
    final en = lookupAppLocalizations(const Locale('en'));

    // Inside the app the names of days and months are loaded with its localizations.
    setUpAll(initializeDateFormatting);

    PlannedMeal meal(List<Map<String, Object?>> ingredients) => PlannedMeal.fromJson(
      _meal('2026-10-05', 'LUNCH', 'r1', _pastaName, ingredients: ingredients).cast<String, dynamic>(),
    );

    test('moves across months and years without shifting days', () {
      expect(addDays('2026-10-05', 7), '2026-10-12');
      expect(addDays('2026-10-29', 7), '2026-11-05');
      expect(addDays('2026-01-03', -7), '2025-12-27');
      // The night the clocks go back in Spain has 25 hours.
      expect(addDays('2026-10-24', 1), '2026-10-25');
      expect(addDays('2026-10-25', 1), '2026-10-26');
      expect(daysBetween('2026-10-24', '2026-10-27'), 3);
      expect(daysBetween('2026-10-05', '2026-10-05'), 0);
      expect(weekDays('2026-10-05'), [
        '2026-10-05',
        '2026-10-06',
        '2026-10-07',
        '2026-10-08',
        '2026-10-09',
        '2026-10-10',
        '2026-10-11',
      ]);
    });

    test('names days in the language of the user', () {
      expect(shortDay(es, '2026-10-05'), '5 oct');
      expect(weekdayAndDay(es, '2026-10-05'), 'lunes 5 oct');
      expect(shortDay(en, '2026-10-05'), '5 Oct');
      expect(weekdayAndDay(en, '2026-10-05'), 'Monday 5 Oct');
    });

    test('says first what the meal saves from expiring, the soonest first', () {
      expect(
        mealNotes(
          es,
          meal([
            _ingredient('Tomate', expires: '2026-10-08'),
            _ingredient('Calabacín', expires: '2026-10-06'),
            _ingredient('Pasta', expires: '2027-03-01'),
            _ingredient('Sal', availability: 'ASSUMED', staple: true),
          ]),
        ),
        [
          'Aprovecha calabacín, con caducidad el 6 oct.',
          'Aprovecha tomate, con caducidad el 8 oct.',
          'Habrá en casa todos los ingredientes.',
        ],
      );
    });

    test('always says so when a date is an estimate', () {
      expect(
        mealNotes(es, meal([_ingredient('Champiñones', expires: '2026-10-06', estimated: true)])).first,
        'Aprovecha champiñones, con caducidad estimada el 6 oct.',
      );
      expect(
        mealNotes(en, meal([_ingredient('Zucchini', expires: '2026-10-06', estimated: true)])).first,
        'Uses zucchini, estimated to expire on 6 Oct.',
      );
    });

    test('names what has to be bought', () {
      expect(
        mealNotes(
          es,
          meal([
            _ingredient('Pasta'),
            _ingredient('Mozzarella', availability: 'MISSING'),
            _ingredient('Tomate', availability: 'MISSING'),
          ]),
        ),
        ['Falta por comprar: mozzarella, tomate.'],
      );
    });

    test('does not claim there is enough when there is less, or when it cannot be compared', () {
      expect(
        mealNotes(
          es,
          meal([
            _ingredient('Pasta', availability: 'PARTIAL'),
            _ingredient('Tomate', availability: 'UNKNOWN_QUANTITY'),
          ]),
        ),
        [
          'Habrá en casa todos los ingredientes.',
          'Habrá menos pasta de lo que pide la receta.',
          'Comprueba la cantidad de tomate: no podemos compararla con la de la receta.',
        ],
      );
    });

    test('says nothing about a meal in the past', () {
      expect(mealNotes(es, meal([])), isEmpty);
    });

    test('says how many meals were planned and how many stay empty', () {
      expect(generatedMessage(es, const Generated(filled: 14, unfilled: 0)), 'Se han planificado 14 comidas.');
      expect(
        generatedMessage(es, const Generated(filled: 0, unfilled: 0)),
        'No se ha planificado ninguna comida nueva.',
      );
      expect(
        generatedMessage(es, const Generated(filled: 1, unfilled: 1)),
        'Se ha planificado 1 comida. 1 comida se queda vacía: no hay más recetas que encajen sin repetir demasiado.',
      );
      expect(generatedMessage(en, const Generated(filled: 2, unfilled: 0)), '2 meals have been planned.');
    });

    test('knows when a week is over and whether there is something to suggest again', () {
      MealPlan plan(Map<String, Object?> json) => MealPlan.fromJson(json.cast<String, dynamic>());

      expect(plan(_week()).isOver, isFalse);
      expect(plan(_week(weekStart: '2026-09-28', weekEnd: '2026-10-04')).isOver, isTrue);
      expect(plan(_week(meals: [_pasta])).hasSuggestionsAhead, isFalse);
      expect(plan(_week(meals: [_pasta, _chicken])).hasSuggestionsAhead, isTrue);
      // A suggestion for a day that has passed is history: generating again would not replace it.
      expect(
        plan(
          _week(
            weekStart: '2026-09-28',
            weekEnd: '2026-10-04',
            meals: [_meal('2026-10-01', 'LUNCH', 'r2', _chickenName, origin: 'GENERATED')],
          ),
        ).hasSuggestionsAhead,
        isFalse,
      );
    });
  });
}
