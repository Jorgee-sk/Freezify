import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../../core/providers.dart';
import '../auth/auth_controller.dart';
import 'inventory_models.dart';

class InventoryRepository {
  const InventoryRepository(this._api);

  static const pageSize = 50;

  final ApiClient _api;

  String _base(String householdId) => '/households/$householdId/inventory';

  Future<InventoryPage> list(InventoryQuery query) async {
    final text = query.text.trim();
    final parameters = Uri(
      queryParameters: {
        'state': query.state.wire,
        'page': '${query.page}',
        'size': '$pageSize',
        if (query.location != null) 'location': query.location!.wire,
        if (query.category != null) 'category': query.category!.wire,
        if (text.isNotEmpty) 'q': text,
      },
    ).query;
    final json = await _api.get('${_base(query.householdId)}?$parameters');
    return InventoryPage.fromJson(json as Map<String, dynamic>);
  }

  Future<ConsumeFirst> consumeFirst(String householdId) async {
    final json = await _api.get('${_base(householdId)}/consume-first');
    return ConsumeFirst.fromJson(json as Map<String, dynamic>);
  }

  Future<List<RecentFood>> recent(String householdId) async {
    final json = await _api.get('${_base(householdId)}/recent') as List<dynamic>;
    return [for (final item in json) RecentFood.fromJson(item as Map<String, dynamic>)];
  }

  Future<List<Food>> searchFoods(String text, String language) async {
    final parameters = Uri(queryParameters: {'q': text, 'lang': language}).query;
    final json = await _api.get('/foods?$parameters') as List<dynamic>;
    return [for (final item in json) Food.fromJson(item as Map<String, dynamic>)];
  }

  Future<void> create(String householdId, ItemInput input) => _api.post(_base(householdId), body: input.toJson());

  Future<void> update(String householdId, String itemId, ItemInput input) =>
      _api.put('${_base(householdId)}/$itemId', body: input.toJson());

  Future<void> delete(String householdId, String itemId) => _api.delete('${_base(householdId)}/$itemId');

  Future<void> open(String householdId, String itemId) => _api.post('${_base(householdId)}/$itemId/open');

  Future<void> consume(String householdId, String itemId, Quantity quantity) =>
      _api.post('${_base(householdId)}/$itemId/consume', body: {'quantity': quantity.toJson()});

  Future<void> discard(String householdId, String itemId, Quantity quantity, WasteReason reason) => _api.post(
    '${_base(householdId)}/$itemId/discard',
    body: {'quantity': quantity.toJson(), 'reason': reason.wire},
  );
}

final inventoryRepositoryProvider = Provider<InventoryRepository>(
  (ref) => InventoryRepository(ref.watch(apiClientProvider)),
);

// These depend on the signed-in user so that nothing cached survives a change of account.
final inventoryListProvider = FutureProvider.autoDispose.family<InventoryPage, InventoryQuery>((ref, query) {
  ref.watch(authControllerProvider.select((auth) => auth.value?.id));
  return ref.watch(inventoryRepositoryProvider).list(query);
});

final consumeFirstProvider = FutureProvider.autoDispose.family<ConsumeFirst, String>((ref, householdId) {
  ref.watch(authControllerProvider.select((auth) => auth.value?.id));
  return ref.watch(inventoryRepositoryProvider).consumeFirst(householdId);
});

final recentFoodsProvider = FutureProvider.autoDispose.family<List<RecentFood>, String>((ref, householdId) {
  ref.watch(authControllerProvider.select((auth) => auth.value?.id));
  return ref.watch(inventoryRepositoryProvider).recent(householdId);
});
