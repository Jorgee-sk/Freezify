import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../../core/errors.dart';
import '../../core/providers.dart';
import '../../l10n/app_localizations.dart';
import '../inventory/inventory_format.dart';
import '../inventory/inventory_models.dart';
import 'recipe_models.dart';
import 'recipe_wording.dart';

/// Food this close to its date is pointed out: it is why the recipe uses it.
const _pressingDays = 5;

/// An ingredient of a recipe written by AI, with the date of what the household has of it.
class GeneratedIngredient {
  const GeneratedIngredient({
    required this.name,
    required this.quantity,
    required this.optional,
    required this.staple,
    required this.daysLeft,
    required this.estimated,
  });

  factory GeneratedIngredient.fromJson(Map<String, dynamic> json) => GeneratedIngredient(
    name: json['name'] as String,
    quantity: Quantity((json['amount'] as num).toDouble(), Unit.parse(json['unit'] as String)),
    optional: json['optional'] as bool,
    staple: json['staple'] as bool,
    daysLeft: json['daysLeft'] as int?,
    estimated: json['estimated'] as bool,
  );

  final String name;
  final Quantity quantity;
  final bool optional;

  /// Salt, oil or water, assumed in any kitchen.
  final bool staple;

  /// Days until the earliest date of that food at home, when it has one.
  final int? daysLeft;
  final bool estimated;
}

/// A recipe a language model wrote with what the household has. Shown, not stored.
class GeneratedRecipe {
  const GeneratedRecipe({
    required this.title,
    required this.summary,
    required this.servings,
    required this.minutes,
    required this.difficulty,
    required this.ingredients,
    required this.steps,
  });

  factory GeneratedRecipe.fromJson(Map<String, dynamic> json) => GeneratedRecipe(
    title: json['title'] as String,
    summary: json['summary'] as String,
    servings: json['servings'] as int,
    minutes: json['minutes'] as int,
    difficulty: Difficulty.parse(json['difficulty'] as String),
    ingredients: [
      for (final item in json['ingredients'] as List<dynamic>)
        GeneratedIngredient.fromJson(item as Map<String, dynamic>),
    ],
    steps: [for (final step in json['steps'] as List<dynamic>) step as String],
  );

  final String title;
  final String summary;
  final int servings;
  final int minutes;
  final Difficulty difficulty;
  final List<GeneratedIngredient> ingredients;
  final List<String> steps;
}

class GeneratedRecipeRepository {
  const GeneratedRecipeRepository(this._api);

  final ApiClient _api;

  /// Whether the server has a language model: without one, nothing that needs it is offered.
  Future<bool> aiEnabled() async => (await _api.get('/ai') as Map<String, dynamic>)['enabled'] as bool;

  Future<GeneratedRecipe> generate(String householdId, String language, int servings) async => GeneratedRecipe.fromJson(
    await _api.post('/households/$householdId/recipes/generated?lang=$language', body: {'servings': servings})
        as Map<String, dynamic>,
  );
}

final generatedRecipeRepositoryProvider = Provider<GeneratedRecipeRepository>(
  (ref) => GeneratedRecipeRepository(ref.watch(apiClientProvider)),
);

/// It only changes with the server's configuration, so it is asked once.
final aiEnabledProvider = FutureProvider<bool>((ref) => ref.watch(generatedRecipeRepositoryProvider).aiEnabled());

/// "Create a recipe with what I have": a language model writes one from the household's inventory.
class GeneratedRecipeScreen extends ConsumerStatefulWidget {
  const GeneratedRecipeScreen({super.key, required this.householdId});

  final String householdId;

  @override
  ConsumerState<GeneratedRecipeScreen> createState() => _GeneratedRecipeScreenState();
}

class _GeneratedRecipeScreenState extends ConsumerState<GeneratedRecipeScreen> {
  int _servings = 2;
  GeneratedRecipe? _recipe;
  Object? _error;
  bool _busy = false;

  Future<void> _generate() async {
    final language = AppLocalizations.of(context).localeName;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final recipe = await ref
          .read(generatedRecipeRepositoryProvider)
          .generate(widget.householdId, language, _servings);
      if (mounted) {
        setState(() {
          _recipe = recipe;
          _busy = false;
        });
      }
    } catch (error) {
      if (mounted) {
        setState(() {
          _error = error;
          _busy = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final recipe = _recipe;
    final muted = theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.generatedTitle)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(l10n.generatedIntro, style: TextStyle(color: theme.colorScheme.onSurfaceVariant)),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: DropdownButtonFormField<int>(
                  initialValue: _servings,
                  decoration: InputDecoration(labelText: l10n.generatedServings),
                  items: [
                    for (var count = 1; count <= 8; count++) DropdownMenuItem(value: count, child: Text('$count')),
                  ],
                  onChanged: _busy ? null : (value) => setState(() => _servings = value ?? _servings),
                ),
              ),
              const SizedBox(width: 12),
              FilledButton(
                style: FilledButton.styleFrom(minimumSize: const Size(0, 48)),
                onPressed: _busy ? null : _generate,
                child: Text(recipe == null ? l10n.generatedCreate : l10n.generatedAgain),
              ),
            ],
          ),
          if (_busy) ...[
            const SizedBox(height: 16),
            const LinearProgressIndicator(),
            const SizedBox(height: 8),
            Text(l10n.generatedWriting, style: muted),
          ],
          if (_error != null) ...[
            const SizedBox(height: 16),
            Semantics(
              liveRegion: true,
              child: Text(errorMessage(l10n, _error!), style: TextStyle(color: theme.colorScheme.error)),
            ),
          ],
          if (recipe != null && !_busy) ...[
            const SizedBox(height: 24),
            Text(recipe.title, style: theme.textTheme.headlineSmall),
            const SizedBox(height: 4),
            Text(recipe.summary),
            const SizedBox(height: 4),
            Text(
              [
                l10n.recipeMinutes(recipe.minutes),
                difficultyName(l10n, recipe.difficulty),
                l10n.recipeServings(recipe.servings),
              ].join(' · '),
              style: muted,
            ),
            const SizedBox(height: 12),
            // Written by a model: the person cooking has the last word on amounts, times and doneness.
            Card(
              color: theme.colorScheme.tertiaryContainer,
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Text(l10n.generatedNotice, style: TextStyle(color: theme.colorScheme.onTertiaryContainer)),
              ),
            ),
            const SizedBox(height: 16),
            Text(l10n.ingredientsTitle, style: theme.textTheme.titleMedium),
            for (final ingredient in recipe.ingredients) _IngredientTile(ingredient: ingredient),
            const SizedBox(height: 16),
            Text(l10n.stepsTitle, style: theme.textTheme.titleMedium),
            for (final (index, step) in recipe.steps.indexed)
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: CircleAvatar(radius: 14, child: Text('${index + 1}')),
                title: Text(step),
              ),
          ],
        ],
      ),
    );
  }
}

class _IngredientTile extends StatelessWidget {
  const _IngredientTile({required this.ingredient});

  final GeneratedIngredient ingredient;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final days = ingredient.daysLeft;
    final pressing = !ingredient.staple && days != null && days <= _pressingDays;
    final details = [
      formatQuantity(l10n, ingredient.quantity),
      if (ingredient.optional) l10n.generatedOptional,
      if (ingredient.staple) l10n.stapleLabel,
    ].join(' · ');
    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(ingredient.name),
      subtitle: Text(details),
      // Why the recipe uses it: the date of what is at home, from the inventory, not from the model.
      trailing: pressing
          ? Text(
              days == 0
                  ? (ingredient.estimated ? l10n.badgeTodayEstimated : l10n.badgeToday)
                  : (ingredient.estimated ? l10n.badgeDaysEstimated(days) : l10n.badgeDays(days)),
              style: TextStyle(color: theme.colorScheme.error),
            )
          : null,
    );
  }
}
