import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'features/auth/auth_controller.dart';
import 'features/auth/login_screen.dart';
import 'features/auth/register_screen.dart';
import 'features/auth/splash_screen.dart';
import 'features/households/household_detail_screen.dart';
import 'features/households/households_screen.dart';
import 'features/inventory/inventory_screen.dart';
import 'features/notifications/notification_preferences_screen.dart';
import 'features/notifications/notifications_screen.dart';
import 'features/notifications/push_controller.dart';
import 'features/recipes/recipe_detail_screen.dart';
import 'features/recipes/recipes_screen.dart';
import 'l10n/app_localizations.dart';

const _seedColor = Color(0xFF1F9D63);

final routerProvider = Provider<GoRouter>((ref) {
  final authChanges = ValueNotifier<int>(0);
  ref.listen(authControllerProvider, (_, _) => authChanges.value++);
  ref.onDispose(authChanges.dispose);

  final router = GoRouter(
    initialLocation: '/',
    refreshListenable: authChanges,
    redirect: (context, state) {
      final auth = ref.read(authControllerProvider);
      final path = state.matchedLocation;
      if (auth.isLoading || auth.hasError) return path == '/splash' ? null : '/splash';

      final signedIn = auth.value != null;
      final onAuthScreen = path == '/login' || path == '/register';
      if (!signedIn) return onAuthScreen ? null : '/login';
      return onAuthScreen || path == '/splash' ? '/' : null;
    },
    routes: [
      GoRoute(path: '/splash', builder: (_, _) => const SplashScreen()),
      GoRoute(path: '/login', builder: (_, _) => const LoginScreen()),
      GoRoute(path: '/register', builder: (_, _) => const RegisterScreen()),
      GoRoute(
        path: '/',
        builder: (_, _) => const HouseholdsScreen(),
        routes: [
          GoRoute(
            path: 'notifications',
            builder: (_, _) => const NotificationsScreen(),
            routes: [GoRoute(path: 'preferences', builder: (_, _) => const NotificationPreferencesScreen())],
          ),
          GoRoute(
            path: 'households/:id',
            builder: (_, state) => InventoryScreen(householdId: state.pathParameters['id']!),
            routes: [
              GoRoute(
                path: 'settings',
                builder: (_, state) => HouseholdDetailScreen(householdId: state.pathParameters['id']!),
              ),
              GoRoute(
                path: 'recipes',
                builder: (_, state) => RecipesScreen(householdId: state.pathParameters['id']!),
                routes: [
                  GoRoute(
                    path: ':recipeId',
                    builder: (_, state) => RecipeDetailScreen(
                      householdId: state.pathParameters['id']!,
                      recipeId: state.pathParameters['recipeId']!,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    ],
  );
  // Tapping a push notification leads to the notifications, whatever was on screen.
  final opens = ref.watch(pushControllerProvider).opens.listen((_) => router.go('/notifications'));
  ref.onDispose(opens.cancel);
  return router;
});

class FreezifyApp extends ConsumerWidget {
  const FreezifyApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // A signed-in user sees their saved language; otherwise the device language applies.
    final userLocale = ref.watch(authControllerProvider.select((auth) => auth.value?.locale));

    return MaterialApp.router(
      onGenerateTitle: (context) => AppLocalizations.of(context).appName,
      routerConfig: ref.watch(routerProvider),
      locale: userLocale == null ? null : Locale(userLocale),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      theme: _theme(Brightness.light),
      darkTheme: _theme(Brightness.dark),
    );
  }
}

ThemeData _theme(Brightness brightness) {
  final scheme = ColorScheme.fromSeed(seedColor: _seedColor, brightness: brightness);
  return ThemeData(
    colorScheme: scheme,
    cardTheme: CardThemeData(
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: scheme.outlineVariant),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(border: OutlineInputBorder(borderRadius: BorderRadius.circular(12))),
    filledButtonTheme: FilledButtonThemeData(style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(48))),
  );
}
