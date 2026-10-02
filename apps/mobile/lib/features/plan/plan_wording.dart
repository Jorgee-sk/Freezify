import 'package:intl/intl.dart';

import '../../l10n/app_localizations.dart';
import '../inventory/inventory_format.dart';
import '../recipes/recipe_models.dart';
import 'plan_models.dart';

/// Food with this many days or fewer between the meal and its date is worth saying the meal makes use of.
const _worthMentioningDays = 5;

String _inSentence(String name) => name.isEmpty ? name : name[0].toLowerCase() + name.substring(1);

// Days are handled in UTC so that no day is ever 23 or 25 hours long.
DateTime _utc(String isoDay) {
  final parts = isoDay.split('-').map(int.parse).toList();
  return DateTime.utc(parts[0], parts[1], parts[2]);
}

/// The calendar day [days] after [isoDay].
String addDays(String isoDay, int days) => toIsoDay(_utc(isoDay).add(Duration(days: days)));

int daysBetween(String fromIsoDay, String toIsoDay) => _utc(toIsoDay).difference(_utc(fromIsoDay)).inDays;

/// The seven days of the week that starts on [weekStart].
List<String> weekDays(String weekStart) => [for (var day = 0; day < 7; day++) addDays(weekStart, day)];

/// "5 oct".
String shortDay(AppLocalizations l10n, String isoDay) => DateFormat('d MMM', l10n.localeName).format(_utc(isoDay));

/// "lunes 5 oct".
String weekdayAndDay(AppLocalizations l10n, String isoDay) =>
    DateFormat('EEEE d MMM', l10n.localeName).format(_utc(isoDay));

String slotName(AppLocalizations l10n, MealSlot slot) => switch (slot) {
  MealSlot.lunch => l10n.planLunch,
  MealSlot.dinner => l10n.planDinner,
};

/// What a planned meal means for the food at home, in sentences. Every sentence comes from the data of the plan:
/// what the household would really have on that day and what would be missing. A date that is an estimate is
/// said to be one. Nothing is said about a meal in the past.
List<String> mealNotes(AppLocalizations l10n, PlannedMeal meal) {
  final needed = [
    for (final ingredient in meal.ingredients)
      if (!ingredient.staple) ingredient,
  ];
  if (needed.isEmpty) return const [];
  final lines = <String>[];

  // What the meal saves from expiring comes first: it is the reason to cook it that day. The sort is stable by
  // hand, so that foods with the same date keep the order of the recipe.
  for (var days = 0; days <= _worthMentioningDays; days++) {
    for (final ingredient in needed) {
      final expires = ingredient.expirationDate;
      if (ingredient.availability == Availability.missing || expires == null) continue;
      if (daysBetween(meal.date, expires) != days) continue;
      final food = _inSentence(ingredient.name);
      final date = shortDay(l10n, expires);
      lines.add(ingredient.estimated ? l10n.planNoteUsesEstimated(food, date) : l10n.planNoteUses(food, date));
    }
  }

  final missing = [
    for (final ingredient in needed)
      if (ingredient.availability == Availability.missing) _inSentence(ingredient.name),
  ];
  lines.add(missing.isEmpty ? l10n.planNoteHaveAll : l10n.planNoteMissing(missing.join(', ')));
  for (final ingredient in needed) {
    if (ingredient.availability == Availability.partial) {
      lines.add(l10n.planNotePartial(_inSentence(ingredient.name)));
    } else if (ingredient.availability == Availability.unknownQuantity) {
      lines.add(l10n.reasonUnknownQuantity(_inSentence(ingredient.name)));
    }
  }
  return lines;
}

/// "Yogur (4 uds), con caducidad el 3 oct".
String unusedLine(AppLocalizations l10n, UnusedFood food) {
  final quantity = formatQuantity(l10n, food.left);
  final date = shortDay(l10n, food.expirationDate);
  return food.estimated
      ? l10n.planUnusedItemEstimated(food.name, quantity, date)
      : l10n.planUnusedItem(food.name, quantity, date);
}

/// "Se han planificado 4 comidas. 10 comidas se quedan vacías: …".
String generatedMessage(AppLocalizations l10n, Generated generated) => [
  generated.filled == 0 ? l10n.planNothingFilled : l10n.planFilled(generated.filled),
  if (generated.unfilled > 0) l10n.planUnfilled(generated.unfilled),
].join(' ');
