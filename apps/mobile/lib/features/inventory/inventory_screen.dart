import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/errors.dart';
import '../../l10n/app_localizations.dart';
import '../households/dialogs.dart';
import '../households/households_repository.dart';
import 'inventory_format.dart';
import 'inventory_models.dart';
import 'inventory_repository.dart';
import 'item_form_screen.dart';
import 'take_out_sheet.dart';

enum _ItemAction { consume, discard, open, edit, delete }

/// The main screen of a household: what is in the house.
class InventoryScreen extends ConsumerStatefulWidget {
  const InventoryScreen({super.key, required this.householdId});

  final String householdId;

  @override
  ConsumerState<InventoryScreen> createState() => _InventoryScreenState();
}

class _InventoryScreenState extends ConsumerState<InventoryScreen> {
  static const _searchDelay = Duration(milliseconds: 300);

  ItemState _state = ItemState.active;
  StorageLocation? _location;
  FoodCategory? _category;
  String _text = '';
  int _page = 0;
  Timer? _searchTimer;

  String get _householdId => widget.householdId;
  InventoryRepository get _repository => ref.read(inventoryRepositoryProvider);

  InventoryQuery get _query => (
    householdId: _householdId,
    state: _state,
    location: _location,
    category: _category,
    text: _text,
    page: _page,
  );

  bool get _filtered => _state != ItemState.active || _location != null || _category != null || _text.isNotEmpty;

  @override
  void dispose() {
    _searchTimer?.cancel();
    super.dispose();
  }

  /// Any change of filter starts again from the first page.
  void _filter(VoidCallback change) {
    setState(() {
      change();
      _page = 0;
    });
  }

  void _search(String text) {
    // Waits for the typing to pause instead of asking the server on every key.
    _searchTimer?.cancel();
    _searchTimer = Timer(_searchDelay, () => _filter(() => _text = text.trim()));
  }

  void _reload() {
    ref.invalidate(inventoryListProvider);
    ref.invalidate(recentFoodsProvider(_householdId));
  }

  Future<void> _run(Future<void> Function() action) async {
    try {
      await action();
      _reload();
    } catch (error) {
      if (mounted) showError(context, error);
    }
  }

  Future<void> _openForm([InventoryItem? item]) async {
    final saved = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        fullscreenDialog: true,
        builder: (_) => ItemFormScreen(householdId: _householdId, item: item),
      ),
    );
    if (saved ?? false) _reload();
  }

  Future<void> _takeOut(InventoryItem item, {required bool discard}) async {
    final result = await showTakeOutSheet(context, item: item, discard: discard);
    if (result == null) return;
    await _run(
      () => discard
          ? _repository.discard(_householdId, item.id, result.quantity, result.reason)
          : _repository.consume(_householdId, item.id, result.quantity),
    );
  }

  Future<void> _delete(InventoryItem item) async {
    final l10n = AppLocalizations.of(context);
    final confirmed = await confirmDestructive(
      context,
      message: l10n.confirmDeleteItem(item.name),
      confirmLabel: l10n.delete,
    );
    if (confirmed) await _run(() => _repository.delete(_householdId, item.id));
  }

  void _onAction(InventoryItem item, _ItemAction action) {
    switch (action) {
      case _ItemAction.consume:
        _takeOut(item, discard: false);
      case _ItemAction.discard:
        _takeOut(item, discard: true);
      case _ItemAction.open:
        _run(() => _repository.open(_householdId, item.id));
      case _ItemAction.edit:
        _openForm(item);
      case _ItemAction.delete:
        _delete(item);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final household = ref.watch(householdProvider(_householdId));
    final items = ref.watch(inventoryListProvider(_query));

    return Scaffold(
      appBar: AppBar(
        title: Text(household.value?.name ?? ''),
        actions: [
          IconButton(
            tooltip: l10n.settings,
            icon: const Icon(Icons.group_outlined),
            onPressed: () => context.push('/households/$_householdId/settings'),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _openForm,
        icon: const Icon(Icons.add),
        label: Text(l10n.addFood),
      ),
      body: household.hasError
          ? Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Text(errorMessage(l10n, household.error!), textAlign: TextAlign.center),
              ),
            )
          : RefreshIndicator(
              onRefresh: () => ref.refresh(inventoryListProvider(_query).future),
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          decoration: InputDecoration(
                            hintText: l10n.searchInventory,
                            prefixIcon: const Icon(Icons.search),
                            isDense: true,
                          ),
                          textInputAction: TextInputAction.search,
                          onChanged: _search,
                        ),
                      ),
                      const SizedBox(width: 8),
                      IconButton.outlined(
                        tooltip: l10n.filters,
                        isSelected: _state != ItemState.active || _category != null,
                        icon: const Icon(Icons.tune),
                        onPressed: _chooseFilters,
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: Row(
                      children: [
                        for (final location in <StorageLocation?>[null, ...StorageLocation.values])
                          Padding(
                            padding: const EdgeInsets.only(right: 8),
                            child: ChoiceChip(
                              label: Text(location == null ? l10n.allLocations : locationName(l10n, location)),
                              selected: _location == location,
                              onSelected: (_) => _filter(() => _location = location),
                            ),
                          ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 8),
                  ...items.when(
                    skipLoadingOnReload: true,
                    loading: () => const [
                      Padding(padding: EdgeInsets.all(32), child: Center(child: CircularProgressIndicator())),
                    ],
                    error: (error, _) => [
                      Text(errorMessage(l10n, error), style: TextStyle(color: theme.colorScheme.error)),
                      Align(
                        alignment: Alignment.centerLeft,
                        child: TextButton(onPressed: _reload, child: Text(l10n.retry)),
                      ),
                    ],
                    data: (page) => [
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 4),
                        child: Text(
                          l10n.inventoryCount(page.totalItems),
                          style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                        ),
                      ),
                      if (page.items.isEmpty)
                        Card(
                          child: Padding(
                            padding: const EdgeInsets.all(16),
                            child: Text(_filtered ? l10n.inventoryEmptyFiltered : l10n.inventoryEmpty),
                          ),
                        ),
                      for (final item in page.items) ...[
                        _ItemCard(item: item, onAction: (action) => _onAction(item, action)),
                        const SizedBox(height: 8),
                      ],
                      if (page.totalPages > 1)
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            TextButton(
                              onPressed: _page == 0 ? null : () => setState(() => _page--),
                              child: Text(l10n.previousPage),
                            ),
                            Text(l10n.pageOf(_page + 1, page.totalPages)),
                            TextButton(
                              onPressed: _page + 1 >= page.totalPages ? null : () => setState(() => _page++),
                              child: Text(l10n.nextPage),
                            ),
                          ],
                        ),
                    ],
                  ),
                ],
              ),
            ),
    );
  }

  Future<void> _chooseFilters() async {
    final chosen = await showModalBottomSheet<({ItemState state, FoodCategory? category})>(
      context: context,
      builder: (_) => _FiltersSheet(state: _state, category: _category),
    );
    if (chosen == null) return;
    _filter(() {
      _state = chosen.state;
      _category = chosen.category;
    });
  }
}

class _ItemCard extends StatelessWidget {
  const _ItemCard({required this.item, required this.onAction});

  final InventoryItem item;
  final ValueChanged<_ItemAction> onAction;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return Card(
      clipBehavior: Clip.antiAlias,
      child: ListTile(
        title: Text(
          item.brand == null ? item.name : '${item.name} · ${item.brand}',
          style: const TextStyle(fontWeight: FontWeight.w600),
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(itemSummary(l10n, item)),
            if (item.status != ItemStatus.available)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(
                  statusName(l10n, item.status),
                  style: theme.textTheme.labelMedium?.copyWith(
                    color: item.status == ItemStatus.discarded || item.status == ItemStatus.expired
                        ? theme.colorScheme.error
                        : theme.colorScheme.primary,
                  ),
                ),
              ),
          ],
        ),
        onTap: item.status.isActive ? () => onAction(_ItemAction.edit) : null,
        // Food that already left the house can only be looked at.
        trailing: !item.status.isActive
            ? null
            : PopupMenuButton<_ItemAction>(
                tooltip: l10n.itemActions,
                onSelected: onAction,
                itemBuilder: (context) => [
                  PopupMenuItem(value: _ItemAction.consume, child: Text(l10n.consume)),
                  PopupMenuItem(value: _ItemAction.discard, child: Text(l10n.discard)),
                  if (item.status == ItemStatus.available)
                    PopupMenuItem(value: _ItemAction.open, child: Text(l10n.markOpened)),
                  PopupMenuItem(value: _ItemAction.edit, child: Text(l10n.edit)),
                  PopupMenuItem(value: _ItemAction.delete, child: Text(l10n.delete)),
                ],
              ),
      ),
    );
  }
}

class _FiltersSheet extends StatefulWidget {
  const _FiltersSheet({required this.state, required this.category});

  final ItemState state;
  final FoodCategory? category;

  @override
  State<_FiltersSheet> createState() => _FiltersSheetState();
}

class _FiltersSheetState extends State<_FiltersSheet> {
  late ItemState _state = widget.state;
  late FoodCategory? _category = widget.category;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            DropdownButtonFormField<ItemState>(
              initialValue: _state,
              decoration: InputDecoration(labelText: l10n.show),
              items: [
                for (final state in ItemState.values)
                  DropdownMenuItem(value: state, child: Text(stateName(l10n, state))),
              ],
              onChanged: (value) => setState(() => _state = value ?? ItemState.active),
            ),
            const SizedBox(height: 16),
            DropdownButtonFormField<FoodCategory?>(
              initialValue: _category,
              decoration: InputDecoration(labelText: l10n.category),
              items: [
                DropdownMenuItem(child: Text(l10n.allCategories)),
                for (final category in FoodCategory.values)
                  DropdownMenuItem(value: category, child: Text(categoryName(l10n, category))),
              ],
              onChanged: (value) => setState(() => _category = value),
            ),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: () => Navigator.of(context).pop((state: _state, category: _category)),
              child: Text(l10n.confirm),
            ),
          ],
        ),
      ),
    );
  }
}
