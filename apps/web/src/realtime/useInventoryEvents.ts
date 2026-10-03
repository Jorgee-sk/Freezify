import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { queryKeys } from '../api/queryKeys'
import { subscribeToHousehold } from './householdEvents'

/** Keeps the inventory of a household fresh while it is on screen: any change by any member refetches it. */
export function useInventoryEvents(householdId: string, enabled: boolean) {
  const queryClient = useQueryClient()
  useEffect(() => {
    if (!enabled) return
    return subscribeToHousehold(householdId, (event) => {
      // Neither the meal plan nor the shopping list touch the inventory.
      if (event === 'meal-plan-changed' || event === 'shopping-list-changed') return
      void queryClient.invalidateQueries({ queryKey: queryKeys.inventory(householdId) })
    })
  }, [householdId, enabled, queryClient])
}
