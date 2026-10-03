import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../../core/providers.dart';
import '../auth/auth_controller.dart';
import 'shopping_models.dart';

class ShoppingRepository {
  const ShoppingRepository(this._api);

  final ApiClient _api;

  /// Every line, aisle by aisle (fresh food first) and by name within each aisle.
  Future<List<ShoppingItem>> list(String householdId, String language) async {
    final json = await _api.get('/households/$householdId/shopping-list?lang=$language') as Map<String, dynamic>;
    return [for (final item in json['items'] as List<dynamic>) ShoppingItem.fromJson(item as Map<String, dynamic>)];
  }

  Future<void> add(String householdId, ShoppingItemInput input, String language) =>
      _api.post('/households/$householdId/shopping-list/items?lang=$language', body: input.toJson());

  /// From then on the line belongs to people: the plan no longer changes it.
  Future<void> edit(String householdId, String itemId, ShoppingItemInput input, String language) =>
      _api.put('/households/$householdId/shopping-list/items/$itemId?lang=$language', body: input.toJson());

  Future<void> check(String householdId, String itemId, {required bool checked}) =>
      _api.put('/households/$householdId/shopping-list/items/$itemId/checked', body: {'checked': checked});

  Future<void> remove(String householdId, String itemId) =>
      _api.delete('/households/$householdId/shopping-list/items/$itemId');

  Future<void> removeChecked(String householdId) => _api.delete('/households/$householdId/shopping-list/items/checked');

  /// Puts on the list what the meals of the week that contains [week] lack, from today on.
  ///
  /// Returns how many lines are on the list for that week's meals.
  Future<int> fillFromPlan(String householdId, String week) async {
    final json =
        await _api.post('/households/$householdId/shopping-list/from-plan', body: {'week': week})
            as Map<String, dynamic>;
    return json['lines'] as int;
  }
}

final shoppingRepositoryProvider = Provider<ShoppingRepository>(
  (ref) => ShoppingRepository(ref.watch(apiClientProvider)),
);

// Depends on the signed-in user so that nothing cached survives a change of account.
final shoppingListProvider = FutureProvider.autoDispose
    .family<List<ShoppingItem>, ({String householdId, String language})>((ref, key) {
      ref.watch(authControllerProvider.select((auth) => auth.value?.id));
      return ref.watch(shoppingRepositoryProvider).list(key.householdId, key.language);
    });
