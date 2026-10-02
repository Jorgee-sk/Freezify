import '../inventory/inventory_models.dart';
import 'diet_models.dart';

enum Difficulty implements WireEnum {
  easy('EASY'),
  medium('MEDIUM'),
  hard('HARD');

  const Difficulty(this.wire);

  @override
  final String wire;

  static Difficulty parse(String wire) => values.firstWhere((value) => value.wire == wire);
}

enum Course implements WireEnum {
  main('MAIN'),
  breakfast('BREAKFAST'),
  dessert('DESSERT');

  const Course(this.wire);

  @override
  final String wire;

  static Course parse(String wire) => values.firstWhere((value) => value.wire == wire);
}

/// What the household has of an ingredient.
enum Availability implements WireEnum {
  enough('ENOUGH'),

  /// Some, but less than the recipe asks for.
  partial('PARTIAL'),

  /// Some, measured in a way that cannot be compared with the recipe (pieces against grams).
  unknownQuantity('UNKNOWN_QUANTITY'),
  missing('MISSING'),

  /// A staple such as salt: not looked for in the inventory at all.
  assumed('ASSUMED');

  const Availability(this.wire);

  @override
  final String wire;

  static Availability parse(String wire) => values.firstWhere((value) => value.wire == wire);
}

class RecipeSummary {
  const RecipeSummary({
    required this.id,
    required this.name,
    required this.description,
    required this.servings,
    required this.totalMinutes,
    required this.difficulty,
    required this.course,
    required this.contains,
  });

  factory RecipeSummary.fromJson(Map<String, dynamic> json) => RecipeSummary(
    id: json['id'] as String,
    name: json['name'] as String,
    description: json['description'] as String,
    servings: json['servings'] as int,
    totalMinutes: json['totalMinutes'] as int,
    difficulty: Difficulty.parse(json['difficulty'] as String),
    course: Course.parse(json['course'] as String),
    contains: [for (final trait in json['contains'] as List<dynamic>) FoodTrait.parse(trait as String)],
  );

  final String id;
  final String name;
  final String description;
  final int servings;
  final int totalMinutes;
  final Difficulty difficulty;
  final Course course;

  /// What it contains that someone may not eat, staples included.
  final List<FoodTrait> contains;
}

class RecipeIngredient {
  const RecipeIngredient({required this.foodId, required this.name, required this.quantity, required this.staple});

  factory RecipeIngredient.fromJson(Map<String, dynamic> json) => RecipeIngredient(
    foodId: json['foodId'] as String,
    name: json['name'] as String,
    quantity: Quantity((json['amount'] as num).toDouble(), Unit.parse(json['unit'] as String)),
    staple: json['staple'] as bool,
  );

  final String foodId;
  final String name;
  final Quantity quantity;

  /// Something a kitchen is assumed to have: listed, but not looked for in the inventory.
  final bool staple;
}

class RecipeDetail {
  const RecipeDetail({required this.recipe, required this.ingredients, required this.steps});

  factory RecipeDetail.fromJson(Map<String, dynamic> json) => RecipeDetail(
    recipe: RecipeSummary.fromJson(json['recipe'] as Map<String, dynamic>),
    ingredients: [
      for (final item in json['ingredients'] as List<dynamic>) RecipeIngredient.fromJson(item as Map<String, dynamic>),
    ],
    steps: [for (final step in json['steps'] as List<dynamic>) step as String],
  );

  final RecipeSummary recipe;
  final List<RecipeIngredient> ingredients;
  final List<String> steps;
}

/// An ingredient of a recommended recipe with what the household has of it.
class MatchedIngredient {
  const MatchedIngredient({
    required this.foodId,
    required this.name,
    required this.staple,
    required this.availability,
    required this.estimated,
    required this.daysUntilExpiration,
  });

  factory MatchedIngredient.fromJson(Map<String, dynamic> json) => MatchedIngredient(
    foodId: json['foodId'] as String,
    name: json['name'] as String,
    staple: json['staple'] as bool,
    availability: Availability.parse(json['availability'] as String),
    estimated: json['estimated'] as bool,
    daysUntilExpiration: json['daysUntilExpiration'] as int?,
  );

  final String foodId;
  final String name;
  final bool staple;
  final Availability availability;

  /// Whether the date behind [daysUntilExpiration] is an estimate. Estimates must be worded as such.
  final bool estimated;

  /// Of the soonest date of the household's stock of this food; `null` without a date.
  final int? daysUntilExpiration;

  /// At home and with five days or fewer left: worth saying so.
  bool get expiresSoon => availability != Availability.missing && (daysUntilExpiration ?? 99) <= 5;
}

class Recommendation {
  const Recommendation({
    required this.recipe,
    required this.score,
    required this.ingredients,
    required this.daysSinceCooked,
  });

  factory Recommendation.fromJson(Map<String, dynamic> json) => Recommendation(
    recipe: RecipeSummary.fromJson(json['recipe'] as Map<String, dynamic>),
    score: (json['score'] as num).toDouble(),
    ingredients: [
      for (final item in json['ingredients'] as List<dynamic>) MatchedIngredient.fromJson(item as Map<String, dynamic>),
    ],
    daysSinceCooked: json['daysSinceCooked'] as int?,
  );

  final RecipeSummary recipe;

  /// Between 0 and 1.
  final double score;
  final List<MatchedIngredient> ingredients;

  /// `null` when the household never cooked it.
  final int? daysSinceCooked;
}

/// Which recipes of the catalog a list shows, without what the household does not eat.
typedef RecipeQuery = ({String householdId, String language, String text, int? maxMinutes, Course? course});
