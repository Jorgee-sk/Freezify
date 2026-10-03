import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { queryKeys } from '../api/queryKeys'
import { subscribeToHousehold } from './householdEvents'

/**
 * Keeps the meal plan of a household fresh while it is on screen. A change to the inventory matters as much as
 * a change to the plan: what each meal will find at home depends on it.
 */
export function useMealPlanEvents(householdId: string, enabled: boolean) {
  const queryClient = useQueryClient()
  useEffect(() => {
    if (!enabled) return
    return subscribeToHousehold(householdId, (event) => {
      // The shopping list says nothing about the plan.
      if (event === 'shopping-list-changed') return
      void queryClient.invalidateQueries({ queryKey: queryKeys.mealPlan(householdId) })
    })
  }, [householdId, enabled, queryClient])
}
