import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/app_localizations.dart';
import 'inventory_format.dart';
import 'inventory_repository.dart';
import 'priority_label.dart';

/// "What should I eat first?", answered before anything else on the screen and whatever filter is active
/// below. It takes no space when nothing needs attention.
class ConsumeFirstCard extends ConsumerWidget {
  const ConsumeFirstCard({super.key, required this.householdId});

  static const _visibleItems = 5;

  final String householdId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final consumeFirst = ref.watch(consumeFirstProvider(householdId)).value;
    if (consumeFirst == null || consumeFirst.total == 0) return const SizedBox.shrink();

    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final visible = consumeFirst.items.take(_visibleItems).toList();
    final hidden = consumeFirst.total - visible.length;
    final muted = theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant);

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(Icons.local_fire_department_outlined, color: theme.colorScheme.error),
                  const SizedBox(width: 8),
                  Text(l10n.consumeFirstTitle, style: theme.textTheme.titleMedium),
                ],
              ),
              Text(l10n.consumeFirstCount(consumeFirst.total), style: muted),
              const SizedBox(height: 8),
              for (final item in visible)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 2),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text(
                          '${item.name} · ${formatQuantity(l10n, item.quantity)}',
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      const SizedBox(width: 8),
                      PriorityLabel(item: item),
                    ],
                  ),
                ),
              if (hidden > 0) Padding(padding: const EdgeInsets.only(top: 4), child: Text(l10n.andMore(hidden), style: muted)),
            ],
          ),
        ),
      ),
    );
  }
}
