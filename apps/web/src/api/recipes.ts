import { api } from './client'
import type { Locale } from './endpoints'
import type { ExpirationPriority, Page, Unit } from './inventory'

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'
export type Course = 'BREAKFAST' | 'MAIN' | 'DESSERT'
/**
 * What the household has of an ingredient. `UNKNOWN_QUANTITY`: it has some, measured in a way that cannot be
 * compared with the recipe (pieces against grams). `ASSUMED`: a staple such as salt, not looked for at all.
 */
export type Availability = 'ENOUGH' | 'PARTIAL' | 'UNKNOWN_QUANTITY' | 'MISSING' | 'ASSUMED'

/** Something a food contains that some people do not eat. */
export type FoodTrait =
  | 'MEAT'
  | 'PORK'
  | 'FISH'
  | 'SHELLFISH'
  | 'DAIRY'
  | 'EGG'
  | 'GLUTEN'
  | 'NUTS'
  | 'SOY'
  | 'SESAME'
  | 'ALCOHOL'
export type DietType = 'NONE' | 'VEGETARIAN' | 'VEGAN'

/** What is not cooked in a household: a diet plus anything else to avoid. A hard filter on recipes. */
export interface Diet {
  type: DietType
  avoided: FoodTrait[]
}

export const DIET_TYPES: DietType[] = ['NONE', 'VEGETARIAN', 'VEGAN']
export const AVOIDABLE_TRAITS: FoodTrait[] = [
  'GLUTEN',
  'DAIRY',
  'EGG',
  'NUTS',
  'SOY',
  'SESAME',
  'FISH',
  'SHELLFISH',
  'MEAT',
  'PORK',
  'ALCOHOL',
]

export const COURSES: Course[] = ['MAIN', 'BREAKFAST', 'DESSERT']
/** The time limits offered as a filter, in minutes. */
export const TIME_LIMITS = [15, 30, 45]

export interface RecipeSummary {
  id: string
  name: string
  description: string
  servings: number
  prepMinutes: number
  cookMinutes: number
  totalMinutes: number
  difficulty: Difficulty
  course: Course
  /** What it contains that someone may not eat, staples included. */
  contains: FoodTrait[]
}

export interface RecipeIngredient {
  foodId: string
  name: string
  amount: number
  unit: Unit
  staple: boolean
}

export interface RecipeDetail {
  recipe: RecipeSummary
  ingredients: RecipeIngredient[]
  steps: string[]
}

/** An ingredient of a recommended recipe with what the household has of it. */
export interface MatchedIngredient extends RecipeIngredient {
  availability: Availability
  /** The soonest date of the household's stock of this food, when it has any with a date. */
  expirationDate: string | null
  /** Estimated dates must be worded as estimates. */
  estimated: boolean
  daysUntilExpiration: number | null
  priority: ExpirationPriority | null
}

export interface Recommendation {
  recipe: RecipeSummary
  /** Between 0 and 1. */
  score: number
  factors: { ingredientMatch: number; expiryUrgency: number; convenience: number; novelty: number }
  ingredients: MatchedIngredient[]
  /** Null when the household never cooked it. */
  daysSinceCooked: number | null
}

export interface RecipeFilter {
  text: string
  maxMinutes: number | null
  course: Course | null
  page: number
}

export const RECIPE_PAGE_SIZE = 20
/** Enough to hold every recipe that uses something at home, so that any recipe can be looked up in it. */
export const ALL_RECOMMENDATIONS = 50

export const recipesApi = {
  /** The catalog without what the household does not eat. */
  list: (householdId: string, filter: RecipeFilter, lang: Locale) => {
    const params = new URLSearchParams({
      household: householdId,
      lang,
      page: String(filter.page),
      size: String(RECIPE_PAGE_SIZE),
    })
    if (filter.text.trim()) params.set('q', filter.text.trim())
    if (filter.maxMinutes) params.set('maxMinutes', String(filter.maxMinutes))
    if (filter.course) params.set('course', filter.course)
    return api<Page<RecipeSummary>>(`/recipes?${params}`)
  },
  get: (id: string, lang: Locale) => api<RecipeDetail>(`/recipes/${id}?${new URLSearchParams({ lang })}`),
  recommendations: (householdId: string, lang: Locale) =>
    api<Recommendation[]>(
      `/households/${householdId}/recipes/recommendations?${new URLSearchParams({ lang, limit: String(ALL_RECOMMENDATIONS) })}`,
    ),
  markCooked: (householdId: string, recipeId: string) =>
    api<void>(`/households/${householdId}/recipes/${recipeId}/cooked`, { method: 'POST' }),
}

export const dietApi = {
  get: (householdId: string) => api<Diet>(`/households/${householdId}/diet`),
  update: (householdId: string, diet: Diet) => api<Diet>(`/households/${householdId}/diet`, { method: 'PUT', body: diet }),
}
