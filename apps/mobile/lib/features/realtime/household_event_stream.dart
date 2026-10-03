import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';

import '../../core/api_client.dart';

/// Listens to what happens in a household and reports its changes, reconnecting until disposed.
///
/// Events carry no data: the listener is expected to fetch again. After a reconnection [onInventoryChanged] is
/// called once, because anything may have been missed while disconnected.
class HouseholdEventStream {
  HouseholdEventStream(
    this._api,
    this._householdId, {
    required this.onInventoryChanged,
    this.onMealPlanChanged,
    this.onShoppingListChanged,
    this.retryDelay = const Duration(seconds: 1),
    this.maxRetryDelay = const Duration(seconds: 30),
    this.idleTimeout = const Duration(seconds: 60),
  });

  static const _inventoryChanged = 'inventory-changed';
  static const _mealPlanChanged = 'meal-plan-changed';
  static const _shoppingListChanged = 'shopping-list-changed';

  final ApiClient _api;
  final String _householdId;
  final void Function() onInventoryChanged;

  /// Only whoever shows the meal plan cares about it.
  final void Function()? onMealPlanChanged;

  /// Only whoever shows the shopping list cares about it.
  final void Function()? onShoppingListChanged;

  /// First wait before reconnecting; doubles after every failed attempt up to [maxRetryDelay].
  final Duration retryDelay;
  final Duration maxRetryDelay;

  /// The server sends a heartbeat every 25 s, so a longer silence means the connection is dead.
  final Duration idleTimeout;

  bool _disposed = false;
  CancelToken? _request;
  StreamSubscription<String>? _lines;
  Completer<void>? _connectionEnded;
  Timer? _retryTimer;
  Completer<void>? _retryWait;

  void start() => unawaited(_run());

  void dispose() {
    _disposed = true;
    _closeConnection();
    _retryTimer?.cancel();
    _complete(_retryWait);
  }

  Future<void> _run() async {
    var delay = retryDelay;
    var connectedBefore = false;
    while (!_disposed) {
      try {
        final request = _request = CancelToken();
        final bytes = await _api.openStream('/households/$_householdId/events', cancelToken: request);
        if (_disposed) {
          request.cancel();
          return;
        }
        if (connectedBefore) onInventoryChanged();
        connectedBefore = true;
        delay = retryDelay;
        await _listen(bytes);
      } on ApiException catch (error) {
        // No longer a member, or the household is gone: asking again will not change the answer.
        if (error.status == 403 || error.status == 404) return;
      } catch (_) {
        // Anything else is treated like a dropped connection.
      }
      if (_disposed) return;
      await _wait(delay);
      delay = delay * 2 > maxRetryDelay ? maxRetryDelay : delay * 2;
    }
  }

  /// Completes when the connection ends, for whatever reason.
  Future<void> _listen(Stream<List<int>> bytes) {
    final ended = _connectionEnded = Completer<void>();
    _lines = bytes
        .cast<List<int>>()
        .transform(utf8.decoder)
        .transform(const LineSplitter())
        .timeout(idleTimeout)
        .listen(
          (line) {
            if (_disposed || !line.startsWith('event:')) return;
            final event = line.substring(6).trim();
            if (event == _inventoryChanged) onInventoryChanged();
            if (event == _mealPlanChanged) onMealPlanChanged?.call();
            if (event == _shoppingListChanged) onShoppingListChanged?.call();
          },
          onError: (Object _) => _closeConnection(),
          onDone: _closeConnection,
        );
    return ended.future;
  }

  void _closeConnection() {
    unawaited(_lines?.cancel());
    _lines = null;
    _request?.cancel();
    _request = null;
    _complete(_connectionEnded);
  }

  Future<void> _wait(Duration duration) {
    final wait = _retryWait = Completer<void>();
    _retryTimer = Timer(duration, () => _complete(wait));
    return wait.future;
  }

  static void _complete(Completer<void>? completer) {
    if (completer != null && !completer.isCompleted) completer.complete();
  }
}
