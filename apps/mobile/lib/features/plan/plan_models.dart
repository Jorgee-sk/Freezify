import '../inventory/inventory_models.dart';
import '../recipes/diet_models.dart';
import '../recipes/recipe_models.dart';

enum MealSlot implements WireEnum {
  lunch('LUNCH'),
  dinner('DINNER');

  const MealSlot(this.wire);

  @override
  final String wire;

  static MealSlot parse(String wire) => values.firstWhere((value) => value.wire == wire);
}

/// Who chose the recipe of a meal: a member, or the generator.
enum MealOrigin implements WireEnum {
  manual('MANUAL'),
  generated('GENERATED');

  const MealOrigin(this.wire);

  @override
  final String wire;

  static MealOrigin parse(String wire) => values.firstWhere((value) => value.wire == wire);
}

/// A meal of a week: a day ("2026-10-05") and whether it is lunch or dinner.
typedef MealPlace = ({String date, MealSlot slot});

/// An ingredient of a planned meal with what the household would have of it on that day.
class PlannedIngredient {
  const PlannedIngredient({
    required this.name,
    required this.staple,
    required this.availability,
    required this.expirationDate,
    required this.estimated,
  });

  factory PlannedIngredient.fromJson(Map<String, dynamic> json) => PlannedIngredient(
    name: json['name'] as String,
    staple: json['staple'] as bool,
    availability: Availability.parse(json['availability'] as String),
    expirationDate: json['expirationDate'] as String?,
    estimated: json['estimated'] as bool,
  );

  final String name;
  final bool staple;
  final Availability availability;

  /// The soonest date of what the household would have of this food on the day of the meal.
  final String? expirationDate;

  /// Whether [expirationDate] is an estimate. Estimates must be worded as such.
  final bool estimated;
}

class PlannedMeal {
  const PlannedMeal({
    required this.date,
    required this.slot,
    required this.origin,
    required this.recipeId,
    required this.recipeName,
    required this.totalMinutes,
    required this.difficulty,
    required this.contains,
    required this.ingredients,
    required this.cooked,
  });

  factory PlannedMeal.fromJson(Map<String, dynamic> json) {
    final recipe = json['recipe'] as Map<String, dynamic>;
    return PlannedMeal(
      date: json['date'] as String,
      slot: MealSlot.parse(json['slot'] as String),
      origin: MealOrigin.parse(json['origin'] as String),
      recipeId: recipe['id'] as String,
      recipeName: recipe['name'] as String,
      totalMinutes: recipe['totalMinutes'] as int,
      difficulty: Difficulty.parse(recipe['difficulty'] as String),
      contains: [for (final trait in recipe['contains'] as List<dynamic>) FoodTrait.parse(trait as String)],
      cooked: json['cooked'] as bool,
      ingredients: [
        for (final item in json['ingredients'] as List<dynamic>)
          PlannedIngredient.fromJson(item as Map<String, dynamic>),
      ],
    );
  }

  final String date;
  final MealSlot slot;
  final MealOrigin origin;
  final String recipeId;
  final String recipeName;
  final int totalMinutes;
  final Difficulty difficulty;

  /// What the recipe contains that someone may not eat.
  final List<FoodTrait> contains;

  /// Empty for a meal in the past.
  final List<PlannedIngredient> ingredients;

  /// Whether the household said it cooked that recipe on that day.
  final bool cooked;

  MealPlace get place => (date: date, slot: slot);
}

/// Food at home that expires before the week is over and that the plan does not use, or does not use up.
class UnusedFood {
  const UnusedFood({required this.name, required this.left, required this.expirationDate, required this.estimated});

  factory UnusedFood.fromJson(Map<String, dynamic> json) => UnusedFood(
    name: json['name'] as String,
    left: Quantity((json['amount'] as num).toDouble(), Unit.parse(json['unit'] as String)),
    expirationDate: json['expirationDate'] as String,
    estimated: json['estimated'] as bool,
  );

  final String name;

  /// How much of it would be left on its date.
  final Quantity left;
  final String expirationDate;
  final bool estimated;
}

/// A week of a household, Monday to Sunday.
class MealPlan {
  const MealPlan({
    required this.weekStart,
    required this.weekEnd,
    required this.today,
    required this.meals,
    required this.unusedExpiring,
  });

  factory MealPlan.fromJson(Map<String, dynamic> json) => MealPlan(
    weekStart: json['weekStart'] as String,
    weekEnd: json['weekEnd'] as String,
    today: json['today'] as String,
    meals: [for (final item in json['meals'] as List<dynamic>) PlannedMeal.fromJson(item as Map<String, dynamic>)],
    unusedExpiring: [
      for (final item in json['unusedExpiring'] as List<dynamic>) UnusedFood.fromJson(item as Map<String, dynamic>),
    ],
  );

  final String weekStart;
  final String weekEnd;

  /// The day the plan was looked at: meals before it are history.
  final String today;
  final List<PlannedMeal> meals;
  final List<UnusedFood> unusedExpiring;

  /// Days are "YYYY-MM-DD", which sort as text.
  bool get isOver => weekEnd.compareTo(today) < 0;

  PlannedMeal? mealAt(MealPlace place) {
    for (final meal in meals) {
      if (meal.place == place) return meal;
    }
    return null;
  }

  /// Whether the generator chose any meal that is still to come: those are the ones generating again replaces.
  bool get hasSuggestionsAhead =>
      meals.any((meal) => meal.origin == MealOrigin.generated && meal.date.compareTo(today) >= 0);
}

class Generated {
  const Generated({required this.filled, required this.unfilled});

  factory Generated.fromJson(Map<String, dynamic> json) =>
      Generated(filled: json['filled'] as int, unfilled: json['unfilled'] as int);

  final int filled;

  /// Meals left empty for want of recipes that were not already in the plan.
  final int unfilled;
}
