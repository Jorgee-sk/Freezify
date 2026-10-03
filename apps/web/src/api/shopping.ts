import { api } from './client'
import type { Locale } from './endpoints'
import type { FoodCategory, Quantity } from './inventory'

/** `PLAN`: what the meal plan lacks, recomputed when the list is filled from it again. `MANUAL`: put by a person. */
export type ShoppingOrigin = 'MANUAL' | 'PLAN'

export interface ShoppingItem {
  id: string
  /** The catalog food, when it is one. */
  foodId: string | null
  name: string
  category: FoodCategory
  /** Null when nobody said how much. */
  quantity: Quantity | null
  origin: ShoppingOrigin
  /** For lines from the plan, the day of the first meal that needs it. */
  neededOn: string | null
  checked: boolean
  checkedAt: string | null
}

/** Every line, aisle by aisle (fresh food first) and by name within each aisle. */
export interface ShoppingList {
  items: ShoppingItem[]
}

export interface ShoppingItemInput {
  foodId: string | null
  name: string | null
  category: FoodCategory | null
  quantity: Quantity | null
}

export const shoppingApi = {
  list: (householdId: string, lang: Locale) =>
    api<ShoppingList>(`/households/${householdId}/shopping-list?${new URLSearchParams({ lang })}`),
  add: (householdId: string, input: ShoppingItemInput, lang: Locale) =>
    api<ShoppingItem>(`/households/${householdId}/shopping-list/items?${new URLSearchParams({ lang })}`, {
      method: 'POST',
      body: input,
    }),
  /** From then on the line belongs to people: the plan no longer changes it. */
  edit: (householdId: string, itemId: string, input: ShoppingItemInput, lang: Locale) =>
    api<ShoppingItem>(`/households/${householdId}/shopping-list/items/${itemId}?${new URLSearchParams({ lang })}`, {
      method: 'PUT',
      body: input,
    }),
  check: (householdId: string, itemId: string, checked: boolean, lang: Locale) =>
    api<ShoppingItem>(
      `/households/${householdId}/shopping-list/items/${itemId}/checked?${new URLSearchParams({ lang })}`,
      { method: 'PUT', body: { checked } },
    ),
  remove: (householdId: string, itemId: string) =>
    api<void>(`/households/${householdId}/shopping-list/items/${itemId}`, { method: 'DELETE' }),
  /** What was bought goes into the inventory and leaves the list; lines without a quantity stay. */
  stockBought: (householdId: string, lang: Locale) =>
    api<{ stocked: number; left: string[] }>(
      `/households/${householdId}/shopping-list/items/checked/to-inventory?${new URLSearchParams({ lang })}`,
      { method: 'POST' },
    ),
  removeChecked: (householdId: string) =>
    api<{ removed: number }>(`/households/${householdId}/shopping-list/items/checked`, { method: 'DELETE' }),
  /** Puts on the list what the meals of the week that contains `week` lack, from today on. */
  fillFromPlan: (householdId: string, week: string) =>
    api<{ lines: number }>(`/households/${householdId}/shopping-list/from-plan`, { method: 'POST', body: { week } }),
}
