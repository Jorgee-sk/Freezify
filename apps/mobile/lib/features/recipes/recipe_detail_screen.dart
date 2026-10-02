import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/errors.dart';
import '../../l10n/app_localizations.dart';
import '../households/dialogs.dart';
import '../inventory/inventory_format.dart';
import 'recipe_models.dart';
import 'recipe_repository.dart';
import 'recipe_wording.dart';

class RecipeDetailScreen extends ConsumerStatefulWidget {
  const RecipeDetailScreen({super.key, required this.householdId, required this.recipeId});

  final String householdId;
  final String recipeId;

  @override
  ConsumerState<RecipeDetailScreen> createState() => _RecipeDetailScreenState();
}

class _RecipeDetailScreenState extends ConsumerState<RecipeDetailScreen> {
  bool _saving = false;

  Future<void> _markCooked(String language) async {
    final l10n = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);
    setState(() => _saving = true);
    try {
      await ref.read(recipeRepositoryProvider).markCooked(widget.householdId, widget.recipeId);
      // The recipe now ranks lower.
      ref.invalidate(recommendationsProvider((householdId: widget.householdId, language: language)));
      messenger.showSnackBar(SnackBar(content: Text(l10n.cookedSaved)));
    } catch (error) {
      if (mounted) showError(context, error);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final language = l10n.localeName;
    final detail = ref.watch(recipeDetailProvider((id: widget.recipeId, language: language)));
    // Tells, ingredient by ingredient, what this household has. A recipe that uses nothing at home is not in it.
    final recommendations = ref.watch(recommendationsProvider((householdId: widget.householdId, language: language)));
    final matched = {
      for (final recommendation in recommendations.value ?? const <Recommendation>[])
        if (recommendation.recipe.id == widget.recipeId)
          for (final ingredient in recommendation.ingredients) ingredient.foodId: ingredient,
    };
    final muted = theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant);

    return Scaffold(
      appBar: AppBar(title: Text(detail.value?.recipe.name ?? l10n.recipesTitle)),
      body: detail.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Text(errorMessage(l10n, error), textAlign: TextAlign.center),
          ),
        ),
        data: (recipe) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(recipe.recipe.description),
            const SizedBox(height: 4),
            Text(recipeFacts(l10n, recipe.recipe), style: muted),
            const SizedBox(height: 16),
            Text(l10n.ingredientsTitle, style: theme.textTheme.titleMedium),
            const SizedBox(height: 8),
            for (final ingredient in recipe.ingredients)
              _IngredientRow(ingredient: ingredient, matched: ingredient.staple ? null : matched[ingredient.foodId]),
            if (recommendations.hasValue && matched.isEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(l10n.nothingAtHome, style: muted),
              ),
            const SizedBox(height: 16),
            Text(l10n.stepsTitle, style: theme.textTheme.titleMedium),
            const SizedBox(height: 8),
            for (final (index, step) in recipe.steps.indexed)
              Padding(padding: const EdgeInsets.only(bottom: 8), child: Text('${index + 1}. $step')),
            const SizedBox(height: 16),
            FilledButton(onPressed: _saving ? null : () => _markCooked(language), child: Text(l10n.markCooked)),
            const SizedBox(height: 8),
            Text(l10n.cookedHelp, style: muted),
          ],
        ),
      ),
    );
  }
}

class _IngredientRow extends StatelessWidget {
  const _IngredientRow({required this.ingredient, required this.matched});

  final RecipeIngredient ingredient;

  /// What the household has of it, when that is known.
  final MatchedIngredient? matched;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final quantity = formatQuantity(l10n, ingredient.quantity);
    final availability = matched == null ? null : availabilityName(l10n, matched!.availability);
    final expiry = matched == null ? null : expiryBadge(l10n, matched!);
    final color = switch (matched?.availability) {
      Availability.missing => theme.colorScheme.error,
      Availability.enough => theme.colorScheme.primary,
      _ => theme.colorScheme.onSurfaceVariant,
    };

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Text(
              // A staple is listed with its amount, and nothing is said about having it or not.
              '${ingredient.name} · ${ingredient.staple ? '$quantity · ${l10n.stapleLabel}' : quantity}',
            ),
          ),
          if (availability != null) ...[
            const SizedBox(width: 8),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(
                  availability,
                  style: theme.textTheme.labelMedium?.copyWith(color: color, fontWeight: FontWeight.w600),
                ),
                if (expiry != null)
                  Text(expiry, style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.error)),
              ],
            ),
          ],
        ],
      ),
    );
  }
}
