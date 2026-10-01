import { api } from './client'
import type { Locale } from './endpoints'

export type Unit = 'GRAM' | 'KILOGRAM' | 'MILLILITER' | 'LITER' | 'UNIT'
export type StorageLocation = 'REFRIGERATOR' | 'FREEZER' | 'PANTRY' | 'OTHER'
export type ItemStatus = 'AVAILABLE' | 'OPENED' | 'EXPIRED' | 'CONSUMED' | 'DISCARDED'
export type ItemState = 'ACTIVE' | 'FINISHED' | 'ALL'
export type WasteReason = 'EXPIRED' | 'SPOILED' | 'LEFTOVER' | 'OTHER'
export type FoodCategory =
  | 'VEGETABLES'
  | 'FRUITS'
  | 'MEAT'
  | 'FISH'
  | 'DAIRY'
  | 'EGGS'
  | 'BAKERY'
  | 'PANTRY'
  | 'FROZEN'
  | 'BEVERAGES'
  | 'PREPARED'
  | 'OTHER'

export const UNITS: Unit[] = ['UNIT', 'GRAM', 'KILOGRAM', 'MILLILITER', 'LITER']
export const STORAGE_LOCATIONS: StorageLocation[] = ['REFRIGERATOR', 'FREEZER', 'PANTRY', 'OTHER']
export const WASTE_REASONS: WasteReason[] = ['EXPIRED', 'SPOILED', 'LEFTOVER', 'OTHER']
export const FOOD_CATEGORIES: FoodCategory[] = [
  'VEGETABLES',
  'FRUITS',
  'MEAT',
  'FISH',
  'DAIRY',
  'EGGS',
  'BAKERY',
  'PANTRY',
  'FROZEN',
  'BEVERAGES',
  'PREPARED',
  'OTHER',
]

export interface Quantity {
  amount: number
  unit: Unit
}

/** A canonical food of the catalog, as offered by the autocomplete. */
export interface Food {
  id: string
  name: string
  category: FoodCategory
  defaultUnit: Unit
  defaultStorage: StorageLocation
}

export interface InventoryItem {
  id: string
  householdId: string
  foodId: string | null
  name: string
  category: FoodCategory
  quantity: Quantity
  storageLocation: StorageLocation
  status: ItemStatus
  purchaseDate: string
  expirationDate: string | null
  /** Whether the date was typed by the user or estimated by the app. Estimates must be labelled as such. */
  expirationSource: 'USER' | 'ESTIMATED' | null
  openedDate: string | null
  barcode: string | null
  brand: string | null
  estimatedPrice: number | null
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** What the user can set on an item. The backend replaces the whole item with it. */
export interface ItemInput {
  foodId: string | null
  name: string
  category: FoodCategory | null
  quantity: Quantity
  storageLocation: StorageLocation
  purchaseDate: string | null
  expirationDate: string | null
  openedDate: string | null
  barcode: string | null
  brand: string | null
  estimatedPrice: number | null
  notes: string | null
}

export interface RecentFood {
  name: string
  foodId: string | null
  category: FoodCategory
  quantity: Quantity
  storageLocation: StorageLocation
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface InventoryFilter {
  state: ItemState
  location: StorageLocation | null
  category: FoodCategory | null
  text: string
  page: number
}

export const INVENTORY_PAGE_SIZE = 50

export const foodsApi = {
  search: (text: string, lang: Locale) => api<Food[]>(`/foods?${new URLSearchParams({ q: text, lang })}`),
}

export const inventoryApi = {
  list: (householdId: string, filter: InventoryFilter) => {
    const params = new URLSearchParams({
      state: filter.state,
      page: String(filter.page),
      size: String(INVENTORY_PAGE_SIZE),
    })
    if (filter.location) params.set('location', filter.location)
    if (filter.category) params.set('category', filter.category)
    if (filter.text.trim()) params.set('q', filter.text.trim())
    return api<Page<InventoryItem>>(`/households/${householdId}/inventory?${params}`)
  },
  recent: (householdId: string) => api<RecentFood[]>(`/households/${householdId}/inventory/recent`),
  create: (householdId: string, input: ItemInput) =>
    api<InventoryItem>(`/households/${householdId}/inventory`, { method: 'POST', body: input }),
  update: (householdId: string, itemId: string, input: ItemInput) =>
    api<InventoryItem>(`/households/${householdId}/inventory/${itemId}`, { method: 'PUT', body: input }),
  remove: (householdId: string, itemId: string) =>
    api<void>(`/households/${householdId}/inventory/${itemId}`, { method: 'DELETE' }),
  open: (householdId: string, itemId: string) =>
    api<InventoryItem>(`/households/${householdId}/inventory/${itemId}/open`, { method: 'POST' }),
  consume: (householdId: string, itemId: string, quantity: Quantity) =>
    api<InventoryItem>(`/households/${householdId}/inventory/${itemId}/consume`, {
      method: 'POST',
      body: { quantity },
    }),
  discard: (householdId: string, itemId: string, quantity: Quantity, reason: WasteReason) =>
    api<InventoryItem>(`/households/${householdId}/inventory/${itemId}/discard`, {
      method: 'POST',
      body: { quantity, reason },
    }),
}
