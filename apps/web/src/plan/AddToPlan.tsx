import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { MEAL_SLOTS, mealPlanApi } from '../api/mealPlan'
import type { MealSlot } from '../api/mealPlan'
import { queryKeys } from '../api/queryKeys'
import { ErrorMessage } from '../components/ErrorMessage'
import { currentLocale } from '../i18n'
import { todayIso } from '../inventory/format'
import { addDays, weekdayAndDay } from './wording'

/** How far ahead a recipe can be added from its page; further days are planned from the plan itself. */
const DAYS_AHEAD = 14

/** Puts the recipe on screen in a meal of the plan, saying first what it would replace. */
export function AddToPlan({ householdId, recipeId }: { householdId: string; recipeId: string }) {
  const { t } = useTranslation()
  const lang = currentLocale()
  const queryClient = useQueryClient()
  const today = todayIso()
  const days = Array.from({ length: DAYS_AHEAD }, (_, index) => addDays(today, index))
  const [date, setDate] = useState(today)
  const [slot, setSlot] = useState<MealSlot>('DINNER')
  const [added, setAdded] = useState<{ date: string; slot: MealSlot } | null>(null)

  // The week of the chosen day, to tell whether something is already planned there.
  const week = useQuery({
    queryKey: queryKeys.mealPlanWeek(householdId, date, lang),
    queryFn: () => mealPlanApi.week(householdId, date, lang),
  })
  const taken = week.data?.meals.find((meal) => meal.date === date && meal.slot === slot)

  const add = useMutation({
    mutationFn: () => mealPlanApi.choose(householdId, date, slot, recipeId),
    onSuccess: async () => {
      setAdded({ date, slot })
      await queryClient.invalidateQueries({ queryKey: queryKeys.mealPlan(householdId) })
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    add.mutate()
  }

  function change(changeIt: () => void) {
    changeIt()
    setAdded(null)
  }

  return (
    <form className="card plan-add" aria-labelledby="add-to-plan-title" onSubmit={submit}>
      <h2 id="add-to-plan-title">{t('plan.addTitle')}</h2>
      <div className="field-row">
        <label>
          {t('plan.addDay')}
          <select value={date} onChange={(event) => change(() => setDate(event.target.value))}>
            {days.map((day) => (
              <option key={day} value={day}>
                {weekdayAndDay(day)}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t('plan.addSlot')}
          <select value={slot} onChange={(event) => change(() => setSlot(event.target.value as MealSlot))}>
            {MEAL_SLOTS.map((option) => (
              <option key={option} value={option}>
                {t(`plan.slot.${option}`)}
              </option>
            ))}
          </select>
        </label>
      </div>
      {/* Adding replaces what is there: never without saying so. */}
      {taken && taken.recipe.id !== recipeId && !added && (
        <p className="warning">{t('plan.addReplaces', { recipe: taken.recipe.name })}</p>
      )}
      <ErrorMessage error={add.error} />
      <div className="button-row">
        <button type="submit" className="button" disabled={add.isPending}>
          {t('plan.add')}
        </button>
        {added && (
          <span role="status" className="muted">
            {t('plan.added', {
              place: t('plan.movePlace', { day: weekdayAndDay(added.date), slot: t(`plan.slot.${added.slot}`) }),
            })}{' '}
            <Link to={`/households/${householdId}/plan?week=${added.date}`}>{t('plan.view')}</Link>
          </span>
        )}
      </div>
    </form>
  )
}
