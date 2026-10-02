import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../../core/providers.dart';
import '../auth/auth_controller.dart';
import 'diet_models.dart';
import 'recipe_models.dart';

class RecipeRepository {
  const RecipeRepository(this._api);

  /// The catalog is small, so the app asks for all of it at once instead of paging.
  static const catalogSize = 100;

  /// Enough to hold every recipe that uses something at home, so that any recipe can be looked up in it.
  static const allRecommendations = 50;

  final ApiClient _api;

  Future<List<RecipeSummary>> list(RecipeQuery query) async {
    final text = query.text.trim();
    final parameters = Uri(
      queryParameters: {
        'household': query.householdId,
        'lang': query.language,
        'size': '$catalogSize',
        if (text.isNotEmpty) 'q': text,
        if (query.maxMinutes != null) 'maxMinutes': '${query.maxMinutes}',
        if (query.course != null) 'course': query.course!.wire,
      },
    ).query;
    final json = await _api.get('/recipes?$parameters') as Map<String, dynamic>;
    return [for (final item in json['items'] as List<dynamic>) RecipeSummary.fromJson(item as Map<String, dynamic>)];
  }

  Future<RecipeDetail> get(String id, String language) async =>
      RecipeDetail.fromJson(await _api.get('/recipes/$id?lang=$language') as Map<String, dynamic>);

  Future<List<Recommendation>> recommendations(String householdId, String language) async {
    final json =
        await _api.get('/households/$householdId/recipes/recommendations?lang=$language&limit=$allRecommendations')
            as List<dynamic>;
    return [for (final item in json) Recommendation.fromJson(item as Map<String, dynamic>)];
  }

  Future<Diet> diet(String householdId) async =>
      Diet.fromJson(await _api.get('/households/$householdId/diet') as Map<String, dynamic>);

  Future<void> updateDiet(String householdId, Diet diet) =>
      _api.put('/households/$householdId/diet', body: diet.toJson());

  Future<void> markCooked(String householdId, String recipeId) =>
      _api.post('/households/$householdId/recipes/$recipeId/cooked');
}

final recipeRepositoryProvider = Provider<RecipeRepository>((ref) => RecipeRepository(ref.watch(apiClientProvider)));

// These depend on the signed-in user so that nothing cached survives a change of account.
final recipeListProvider = FutureProvider.autoDispose.family<List<RecipeSummary>, RecipeQuery>((ref, query) {
  ref.watch(authControllerProvider.select((auth) => auth.value?.id));
  return ref.watch(recipeRepositoryProvider).list(query);
});

final recipeDetailProvider = FutureProvider.autoDispose.family<RecipeDetail, ({String id, String language})>((
  ref,
  key,
) {
  ref.watch(authControllerProvider.select((auth) => auth.value?.id));
  return ref.watch(recipeRepositoryProvider).get(key.id, key.language);
});

final recommendationsProvider = FutureProvider.autoDispose
    .family<List<Recommendation>, ({String householdId, String language})>((ref, key) {
      ref.watch(authControllerProvider.select((auth) => auth.value?.id));
      return ref.watch(recipeRepositoryProvider).recommendations(key.householdId, key.language);
    });

final dietProvider = FutureProvider.autoDispose.family<Diet, String>((ref, householdId) {
  ref.watch(authControllerProvider.select((auth) => auth.value?.id));
  return ref.watch(recipeRepositoryProvider).diet(householdId);
});
