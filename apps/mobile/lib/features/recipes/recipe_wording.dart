import '../../l10n/app_localizations.dart';
import 'recipe_models.dart';

/// How many missing ingredients are named before they are only counted.
const _namedMissing = 3;

String _inSentence(String name) => name.isEmpty ? name : name[0].toLowerCase() + name.substring(1);

/// "25 min · Fácil · 2 raciones".
String recipeFacts(AppLocalizations l10n, RecipeSummary recipe) => [
  l10n.recipeMinutes(recipe.totalMinutes),
  difficultyName(l10n, recipe.difficulty),
  l10n.recipeServings(recipe.servings),
].join(' · ');

String difficultyName(AppLocalizations l10n, Difficulty difficulty) => switch (difficulty) {
  Difficulty.easy => l10n.difficultyEasy,
  Difficulty.medium => l10n.difficultyMedium,
  Difficulty.hard => l10n.difficultyHard,
};

String courseName(AppLocalizations l10n, Course course) => switch (course) {
  Course.main => l10n.courseMain,
  Course.breakfast => l10n.courseBreakfast,
  Course.dessert => l10n.courseDessert,
};

/// Why a recipe is recommended, in sentences. Every sentence comes from the data of the recommendation: the
/// food the household really has, its real dates, what is really missing. Nothing is made up, and a date that
/// is an estimate is said to be one.
List<String> recommendationReasons(AppLocalizations l10n, Recommendation recommendation) {
  final needed = [
    for (final ingredient in recommendation.ingredients)
      if (!ingredient.staple) ingredient,
  ];
  final atHome = [
    for (final ingredient in needed)
      if (ingredient.availability != Availability.missing) ingredient,
  ];
  final missing = [
    for (final ingredient in needed)
      if (ingredient.availability == Availability.missing) ingredient,
  ];
  final lines = <String>[];

  // What cannot wait comes first: it is the reason the recipe is here. The sort is stable by hand, so that
  // foods with the same days left keep the order of the recipe.
  final expiring = [
    for (final ingredient in atHome)
      if (ingredient.expiresSoon) ingredient,
  ];
  for (var days = 0; days <= 5; days++) {
    for (final ingredient in expiring.where((ingredient) => ingredient.daysUntilExpiration == days)) {
      lines.add(_expiringSentence(l10n, ingredient));
    }
  }

  if (missing.isEmpty) {
    lines.add(l10n.reasonHaveAll);
  } else {
    lines.add(l10n.reasonHave(atHome.length, needed.length));
    lines.add(
      missing.length <= _namedMissing
          ? l10n.reasonMissing(missing.length, missing.map((ingredient) => _inSentence(ingredient.name)).join(', '))
          : l10n.reasonMissingMany(missing.length),
    );
  }

  for (final ingredient in atHome) {
    if (ingredient.availability == Availability.partial) {
      lines.add(l10n.reasonPartial(_inSentence(ingredient.name)));
    } else if (ingredient.availability == Availability.unknownQuantity) {
      lines.add(l10n.reasonUnknownQuantity(_inSentence(ingredient.name)));
    }
  }

  lines.add(l10n.reasonTime(recommendation.recipe.totalMinutes));
  final cooked = recommendation.daysSinceCooked;
  if (cooked != null) lines.add(cooked == 0 ? l10n.reasonCookedToday : l10n.reasonCooked(cooked));
  return lines;
}

String _expiringSentence(AppLocalizations l10n, MatchedIngredient ingredient) {
  final food = _inSentence(ingredient.name);
  final days = ingredient.daysUntilExpiration!;
  if (days == 0) return ingredient.estimated ? l10n.reasonExpiresTodayEstimated(food) : l10n.reasonExpiresToday(food);
  return ingredient.estimated ? l10n.reasonExpiresEstimated(food, days) : l10n.reasonExpires(food, days);
}

/// "Caduca en 2 días", or `null` when the ingredient is not at home or has time to spare.
String? expiryBadge(AppLocalizations l10n, MatchedIngredient ingredient) {
  if (!ingredient.expiresSoon) return null;
  final days = ingredient.daysUntilExpiration!;
  if (days == 0) return ingredient.estimated ? l10n.badgeTodayEstimated : l10n.badgeToday;
  return ingredient.estimated ? l10n.badgeDaysEstimated(days) : l10n.badgeDays(days);
}

/// What the household has of an ingredient; `null` for staples, about which nothing is claimed.
String? availabilityName(AppLocalizations l10n, Availability availability) => switch (availability) {
  Availability.enough => l10n.availabilityEnough,
  Availability.partial => l10n.availabilityPartial,
  Availability.unknownQuantity => l10n.availabilityUnknown,
  Availability.missing => l10n.availabilityMissing,
  Availability.assumed => null,
};
