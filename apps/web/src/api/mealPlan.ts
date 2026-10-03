import { api } from './client'
import type { Locale } from './endpoints'
import type { Unit } from './inventory'
import type { Availability, Difficulty, FoodTrait } from './recipes'

export type MealSlot = 'LUNCH' | 'DINNER'
/** Who chose the recipe of a meal: a member, or the generator. */
export type MealOrigin = 'MANUAL' | 'GENERATED'

export const MEAL_SLOTS: MealSlot[] = ['LUNCH', 'DINNER']

/** An ingredient of a planned meal with what the household would have of it on that day. */
export interface PlannedIngredient {
  foodId: string
  name: string
  amount: number
  unit: Unit
  staple: boolean
  availability: Availability
  /** The soonest date of what the household would have of this food on the day of the meal. */
  expirationDate: string | null
  /** Estimated dates must be worded as estimates. */
  estimated: boolean
}

export interface PlannedMeal {
  id: string
  date: string
  slot: MealSlot
  origin: MealOrigin
  recipe: {
    id: string
    name: string
    servings: number
    totalMinutes: number
    difficulty: Difficulty
    contains: FoodTrait[]
  }
  /** Empty for a meal in the past. */
  ingredients: PlannedIngredient[]
  /** Whether the household said it cooked that recipe on that day. */
  cooked: boolean
}

/** Food at home that expires before the week is over and that the plan does not use, or does not use up. */
export interface UnusedFood {
  name: string
  amount: number
  unit: Unit
  expirationDate: string
  estimated: boolean
}

/** A week of a household, Monday to Sunday. */
export interface MealPlan {
  weekStart: string
  weekEnd: string
  today: string
  meals: PlannedMeal[]
  unusedExpiring: UnusedFood[]
}

export interface Generated {
  filled: number
  /** Meals left empty for want of recipes that were not already in the plan. */
  unfilled: number
}

export const mealPlanApi = {
  /** The week that contains `week`; this week when it is null. */
  week: (householdId: string, week: string | null, lang: Locale) => {
    const params = new URLSearchParams({ lang })
    if (week) params.set('week', week)
    return api<MealPlan>(`/households/${householdId}/meal-plan?${params}`)
  },
  choose: (householdId: string, date: string, slot: MealSlot, recipeId: string) =>
    api<void>(`/households/${householdId}/meal-plan/${date}/${slot}`, { method: 'PUT', body: { recipeId } }),
  remove: (householdId: string, date: string, slot: MealSlot) =>
    api<void>(`/households/${householdId}/meal-plan/${date}/${slot}`, { method: 'DELETE' }),
  /** If something is planned at the destination, the two meals swap places. */
  move: (householdId: string, date: string, slot: MealSlot, to: { date: string; slot: MealSlot }) =>
    api<void>(`/households/${householdId}/meal-plan/${date}/${slot}/move`, { method: 'POST', body: to }),
  generate: (householdId: string, week: string, replaceGenerated: boolean) =>
    api<Generated>(`/households/${householdId}/meal-plan/generate`, {
      method: 'POST',
      body: { week, replaceGenerated },
    }),
}
