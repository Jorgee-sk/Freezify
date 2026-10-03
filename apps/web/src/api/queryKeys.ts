import type { InventoryFilter } from './inventory'
import type { RecipeFilter } from './recipes'

export const queryKeys = {
  households: ['households'] as const,
  household: (id: string) => ['households', id] as const,
  members: (id: string) => ['households', id, 'members'] as const,
  invitations: (id: string) => ['households', id, 'invitations'] as const,
  inventory: (householdId: string) => ['inventory', householdId] as const,
  inventoryList: (householdId: string, filter: InventoryFilter) => ['inventory', householdId, 'list', filter] as const,
  recentFoods: (householdId: string) => ['inventory', householdId, 'recent'] as const,
  consumeFirst: (householdId: string) => ['inventory', householdId, 'consume-first'] as const,
  foodSearch: (text: string, lang: string) => ['foods', lang, text] as const,
  recipes: ['recipes'] as const,
  recipeList: (householdId: string, filter: RecipeFilter, lang: string) =>
    ['recipes', 'list', householdId, lang, filter] as const,
  ai: ['ai'] as const,
  generatedIngredients: (householdId: string, lang: string) =>
    ['recipes', 'generated-ingredients', householdId, lang] as const,
  diet: (householdId: string) => ['diet', householdId] as const,
  recipe: (id: string, lang: string) => ['recipes', 'detail', lang, id] as const,
  recommendations: (householdId: string, lang: string) => ['recipes', 'recommendations', householdId, lang] as const,
  mealPlan: (householdId: string) => ['meal-plan', householdId] as const,
  mealPlanWeek: (householdId: string, week: string | null, lang: string) =>
    ['meal-plan', householdId, lang, week] as const,
  shopping: (householdId: string) => ['shopping', householdId] as const,
  shoppingList: (householdId: string, lang: string) => ['shopping', householdId, lang] as const,
  notifications: ['notifications'] as const,
  notificationList: (page: number) => ['notifications', 'list', page] as const,
  unreadNotifications: ['notifications', 'unread'] as const,
  notificationPreferences: ['notifications', 'preferences'] as const,
}
