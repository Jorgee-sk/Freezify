import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../../core/providers.dart';
import '../auth/auth_controller.dart';
import 'plan_models.dart';

class PlanRepository {
  const PlanRepository(this._api);

  final ApiClient _api;

  /// The week that contains [week]; this week when it is null.
  Future<MealPlan> week(String householdId, String? week, String language) async {
    final parameters = Uri(queryParameters: {'lang': language, 'week': ?week}).query;
    return MealPlan.fromJson(await _api.get('/households/$householdId/meal-plan?$parameters') as Map<String, dynamic>);
  }

  Future<void> choose(String householdId, MealPlace place, String recipeId) =>
      _api.put(_meal(householdId, place), body: {'recipeId': recipeId});

  Future<void> remove(String householdId, MealPlace place) => _api.delete(_meal(householdId, place));

  /// If something is planned at the destination, the two meals swap places.
  Future<void> move(String householdId, MealPlace from, MealPlace to) =>
      _api.post('${_meal(householdId, from)}/move', body: {'date': to.date, 'slot': to.slot.wire});

  Future<Generated> generate(String householdId, String week, {required bool replaceGenerated}) async =>
      Generated.fromJson(
        await _api.post(
              '/households/$householdId/meal-plan/generate',
              body: {'week': week, 'replaceGenerated': replaceGenerated},
            )
            as Map<String, dynamic>,
      );

  static String _meal(String householdId, MealPlace place) =>
      '/households/$householdId/meal-plan/${place.date}/${place.slot.wire}';
}

final planRepositoryProvider = Provider<PlanRepository>((ref) => PlanRepository(ref.watch(apiClientProvider)));

/// Which week of which household, in which language. A null week is the week of today.
typedef MealPlanQuery = ({String householdId, String? week, String language});

// Depends on the signed-in user so that nothing cached survives a change of account.
final mealPlanProvider = FutureProvider.autoDispose.family<MealPlan, MealPlanQuery>((ref, query) {
  ref.watch(authControllerProvider.select((auth) => auth.value?.id));
  return ref.watch(planRepositoryProvider).week(query.householdId, query.week, query.language);
});
