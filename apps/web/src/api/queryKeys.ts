import type { InventoryFilter } from './inventory'

export const queryKeys = {
  households: ['households'] as const,
  household: (id: string) => ['households', id] as const,
  members: (id: string) => ['households', id, 'members'] as const,
  inventory: (householdId: string) => ['inventory', householdId] as const,
  inventoryList: (householdId: string, filter: InventoryFilter) => ['inventory', householdId, 'list', filter] as const,
  recentFoods: (householdId: string) => ['inventory', householdId, 'recent'] as const,
  foodSearch: (text: string, lang: string) => ['foods', lang, text] as const,
}
