import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/errors.dart';
import '../../l10n/app_localizations.dart';
import 'recipe_models.dart';
import 'recipe_repository.dart';
import 'recipe_wording.dart';

/// What to cook with what the household has, and every recipe of the catalog below.
class RecipesScreen extends ConsumerStatefulWidget {
  const RecipesScreen({super.key, required this.householdId});

  final String householdId;

  @override
  ConsumerState<RecipesScreen> createState() => _RecipesScreenState();
}

class _RecipesScreenState extends ConsumerState<RecipesScreen> {
  static const _searchDelay = Duration(milliseconds: 300);
  static const _shownRecommendations = 5;
  static const _timeLimits = [15, 30, 45];

  String _text = '';
  int? _maxMinutes;
  Course? _course;
  Timer? _searchTimer;

  @override
  void dispose() {
    _searchTimer?.cancel();
    super.dispose();
  }

  void _search(String text) {
    // Waits for the typing to pause instead of asking the server on every key.
    _searchTimer?.cancel();
    _searchTimer = Timer(_searchDelay, () => setState(() => _text = text.trim()));
  }

  void _open(String recipeId) => context.push('/households/${widget.householdId}/recipes/$recipeId');

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final language = l10n.localeName;
    final recommendationsKey = (householdId: widget.householdId, language: language);
    final recommendations = ref.watch(recommendationsProvider(recommendationsKey));
    final RecipeQuery query = (
      householdId: widget.householdId,
      language: language,
      text: _text,
      maxMinutes: _maxMinutes,
      course: _course,
    );
    final catalog = ref.watch(recipeListProvider(query));
    final diet = ref.watch(dietProvider(widget.householdId)).value;
    final restrictions = diet == null ? null : dietSummary(l10n, diet);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.recipesTitle)),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(recommendationsProvider(recommendationsKey).future),
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // Says what is being left out, so that a short list is never a mystery.
            if (diet != null)
              Row(
                children: [
                  Expanded(
                    child: Text(
                      restrictions == null ? l10n.dietNotSet : l10n.dietApplied(restrictions),
                      style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                    ),
                  ),
                  TextButton(
                    onPressed: () => context.push('/households/${widget.householdId}/diet'),
                    child: Text(l10n.dietChange),
                  ),
                ],
              ),
            Text(l10n.recommendedTitle, style: theme.textTheme.titleMedium),
            const SizedBox(height: 8),
            ...recommendations.when(
              loading: () => const [_Loading()],
              error: (error, _) => [_Failure(message: errorMessage(l10n, error))],
              data: (items) => items.isEmpty
                  ? [
                      Card(
                        child: Padding(padding: const EdgeInsets.all(16), child: Text(l10n.noRecommendations)),
                      ),
                    ]
                  : [
                      for (final recommendation in items.take(_shownRecommendations)) ...[
                        _RecommendationCard(
                          recommendation: recommendation,
                          onTap: () => _open(recommendation.recipe.id),
                        ),
                        const SizedBox(height: 8),
                      ],
                    ],
            ),
            const SizedBox(height: 16),
            Text(l10n.catalogTitle, style: theme.textTheme.titleMedium),
            const SizedBox(height: 8),
            TextField(
              decoration: InputDecoration(
                hintText: l10n.searchRecipes,
                prefixIcon: const Icon(Icons.search),
                isDense: true,
              ),
              textInputAction: TextInputAction.search,
              onChanged: _search,
            ),
            const SizedBox(height: 8),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  for (final course in <Course?>[null, ...Course.values])
                    Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: ChoiceChip(
                        label: Text(course == null ? l10n.anyCourse : courseName(l10n, course)),
                        selected: _course == course,
                        onSelected: (_) => setState(() => _course = course),
                      ),
                    ),
                  for (final minutes in _timeLimits)
                    Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: FilterChip(
                        label: Text(l10n.upToMinutes(minutes)),
                        selected: _maxMinutes == minutes,
                        onSelected: (selected) => setState(() => _maxMinutes = selected ? minutes : null),
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            ...catalog.when(
              loading: () => const [_Loading()],
              error: (error, _) => [_Failure(message: errorMessage(l10n, error))],
              data: (items) => items.isEmpty
                  ? [
                      Card(
                        child: Padding(padding: const EdgeInsets.all(16), child: Text(l10n.recipesEmptyFiltered)),
                      ),
                    ]
                  : [
                      for (final recipe in items) ...[
                        Card(
                          clipBehavior: Clip.antiAlias,
                          child: ListTile(
                            title: Text(recipe.name, style: const TextStyle(fontWeight: FontWeight.w600)),
                            subtitle: Text(recipeFacts(l10n, recipe)),
                            onTap: () => _open(recipe.id),
                          ),
                        ),
                        const SizedBox(height: 8),
                      ],
                    ],
            ),
          ],
        ),
      ),
    );
  }
}

class _Loading extends StatelessWidget {
  const _Loading();

  @override
  Widget build(BuildContext context) => const Center(
    child: Padding(padding: EdgeInsets.all(24), child: CircularProgressIndicator()),
  );
}

class _Failure extends StatelessWidget {
  const _Failure({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: Text(message, style: TextStyle(color: Theme.of(context).colorScheme.error)),
  );
}

/// A recommended recipe with the reasons for recommending it, all of them taken from the inventory.
class _RecommendationCard extends StatelessWidget {
  const _RecommendationCard({required this.recommendation, required this.onTap});

  final Recommendation recommendation;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant);

    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Text(
                      recommendation.recipe.name,
                      style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    l10n.recipeFit((recommendation.score * 100).round()),
                    style: theme.textTheme.labelMedium?.copyWith(color: theme.colorScheme.primary),
                  ),
                ],
              ),
              const SizedBox(height: 4),
              Text(l10n.recommendedBecause, style: muted),
              const SizedBox(height: 4),
              for (final reason in recommendationReasons(l10n, recommendation)) Text('• $reason'),
            ],
          ),
        ),
      ),
    );
  }
}
