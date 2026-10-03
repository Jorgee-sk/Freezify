import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { MEAL_SLOTS, mealPlanApi } from '../api/mealPlan'
import type { Generated, MealPlan, MealSlot, PlannedMeal } from '../api/mealPlan'
import { queryKeys } from '../api/queryKeys'
import { ALL_RECIPES, dietApi, recipesApi } from '../api/recipes'
import type { Diet } from '../api/recipes'
import { ErrorMessage } from '../components/ErrorMessage'
import { currentLocale } from '../i18n'
import { useDebounced } from '../inventory/useDebounced'
import { addDays, mealNotes, shortDay, unusedLine, weekDays, weekdayAndDay } from '../plan/wording'
import { useMealPlanEvents } from '../realtime/useMealPlanEvents'
import { conflicts, traitList } from '../recipes/diet'

/** A meal of the week: a day and whether it is lunch or dinner. */
interface Place {
  date: string
  slot: MealSlot
}

const samePlace = (one: Place | null, other: Place) => one?.date === other.date && one.slot === other.slot

/** What the household plans to eat this week: one recipe per day for lunch and for dinner. */
export function MealPlanPage() {
  const { householdId = '' } = useParams()
  const { t } = useTranslation()
  const lang = currentLocale()
  const queryClient = useQueryClient()
  // A link may ask for a week ("?week=2026-10-12"), as the recipe page does after adding a meal.
  const [searchParams] = useSearchParams()
  /** Any day of the week on screen; null for the week of today. */
  const [week, setWeek] = useState<string | null>(searchParams.get('week'))
  const [picking, setPicking] = useState<Place | null>(null)
  const [moving, setMoving] = useState<Place | null>(null)
  const [generated, setGenerated] = useState<Generated | null>(null)

  const plan = useQuery({
    queryKey: queryKeys.mealPlanWeek(householdId, week, lang),
    queryFn: () => mealPlanApi.week(householdId, week, lang),
    // What each meal will find at home depends on the inventory, which changes elsewhere.
    staleTime: 0,
  })
  useMealPlanEvents(householdId, plan.isSuccess)
  const diet = useQuery({ queryKey: queryKeys.diet(householdId), queryFn: () => dietApi.get(householdId) })

  const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.mealPlan(householdId) })
  const choose = useMutation({
    mutationFn: ({ place, recipeId }: { place: Place; recipeId: string }) =>
      mealPlanApi.choose(householdId, place.date, place.slot, recipeId),
    onSuccess: () => {
      setPicking(null)
      return refresh()
    },
  })
  const remove = useMutation({
    mutationFn: (place: Place) => mealPlanApi.remove(householdId, place.date, place.slot),
    onSuccess: refresh,
  })
  const move = useMutation({
    mutationFn: ({ from, to }: { from: Place; to: Place }) => mealPlanApi.move(householdId, from.date, from.slot, to),
    onSuccess: () => {
      setMoving(null)
      return refresh()
    },
  })
  const markCooked = useMutation({
    mutationFn: (recipeId: string) => recipesApi.markCooked(householdId, recipeId),
    onSuccess: async () => {
      await refresh()
      // Cooking something today makes it less of a novelty for the recommender.
      await queryClient.invalidateQueries({ queryKey: queryKeys.recipes })
    },
  })
  const generate = useMutation({
    mutationFn: ({ weekStart, replace }: { weekStart: string; replace: boolean }) =>
      mealPlanApi.generate(householdId, weekStart, replace),
    onSuccess: (result) => {
      setGenerated(result)
      return refresh()
    },
  })

  function show(day: string | null) {
    setWeek(day)
    setPicking(null)
    setMoving(null)
    setGenerated(null)
  }

  function regenerate(weekStart: string) {
    // It replaces meals that are on screen: never without asking.
    if (window.confirm(t('plan.regenerateConfirm'))) generate.mutate({ weekStart, replace: true })
  }

  return (
    <>
      <Link to={`/households/${householdId}`} className="back-link">
        ← {t('households.backToInventory')}
      </Link>
      <h1>{t('plan.title')}</h1>
      {plan.isPending && <p className="muted">{t('app.loading')}</p>}
      <ErrorMessage error={plan.error} />

      {plan.data && (
        <>
          <nav className="week-nav" aria-label={t('plan.week')}>
            <button type="button" className="button" onClick={() => show(addDays(plan.data.weekStart, -7))}>
              ← {t('plan.previousWeek')}
            </button>
            <strong>{t('plan.weekRange', { from: shortDay(plan.data.weekStart), to: shortDay(plan.data.weekEnd) })}</strong>
            <button type="button" className="button" onClick={() => show(addDays(plan.data.weekStart, 7))}>
              {t('plan.nextWeek')} →
            </button>
            {week !== null && (
              <button type="button" className="button ghost" onClick={() => show(null)}>
                {t('plan.thisWeek')}
              </button>
            )}
          </nav>

          {plan.data.weekEnd < plan.data.today ? (
            <p className="muted">{t('plan.pastWeek')}</p>
          ) : (
            <section className="card plan-generate">
              <p className="muted small">{t('plan.generateHelp')}</p>
              <div className="button-row">
                <button
                  type="button"
                  className="button primary"
                  disabled={generate.isPending}
                  onClick={() => generate.mutate({ weekStart: plan.data.weekStart, replace: false })}
                >
                  {t('plan.generate')}
                </button>
                {plan.data.meals.some((meal) => meal.origin === 'GENERATED' && meal.date >= plan.data.today) && (
                  <button
                    type="button"
                    className="button"
                    disabled={generate.isPending}
                    onClick={() => regenerate(plan.data.weekStart)}
                  >
                    {t('plan.regenerate')}
                  </button>
                )}
              </div>
              <ErrorMessage error={generate.error} />
              {generated && (
                <p role="status">
                  {generated.filled === 0 ? t('plan.nothingFilled') : t('plan.filled', { count: generated.filled })}
                  {generated.unfilled > 0 && ` ${t('plan.unfilled', { count: generated.unfilled })}`}
                </p>
              )}
            </section>
          )}

          {plan.data.unusedExpiring.length > 0 && (
            <section className="warning plan-unused" aria-labelledby="unused-title">
              <strong id="unused-title">{t('plan.unusedTitle')}</strong>
              <span>{t('plan.unusedHelp')}</span>
              <ul aria-labelledby="unused-title">
                {plan.data.unusedExpiring.map((food) => (
                  <li key={`${food.name}-${food.expirationDate}-${food.amount}`}>{unusedLine(food)}</li>
                ))}
              </ul>
            </section>
          )}

          <ErrorMessage error={choose.error ?? remove.error ?? move.error ?? markCooked.error} />
          <div className="plan-days">
            {weekDays(plan.data.weekStart).map((date) => (
              <section key={date} className="card plan-day" aria-label={weekdayAndDay(date)}>
                <h2>
                  {weekdayAndDay(date)}
                  {date === plan.data.today && <span className="badge badge-owner">{t('plan.today')}</span>}
                </h2>
                {MEAL_SLOTS.map((slot) => {
                  const place = { date, slot }
                  const meal = plan.data.meals.find((candidate) => samePlace(candidate, place))
                  return (
                    <div key={slot} className="plan-meal" role="group" aria-label={t(`plan.slot.${slot}`)}>
                      <span className="muted small">{t(`plan.slot.${slot}`)}</span>
                      {meal ? (
                        <PlannedMealView householdId={householdId} meal={meal} diet={diet.data} />
                      ) : (
                        <span className="muted">{t('plan.empty')}</span>
                      )}
                      <div className="button-row">
                        <button type="button" className="button small-button" onClick={() => setPicking(place)}>
                          {meal ? t('plan.change') : t('plan.choose')}
                        </button>
                        {/* What was cooked is said on the day: the server records it as cooked today. */}
                        {meal && meal.date === plan.data.today && !meal.cooked && (
                          <button
                            type="button"
                            className="button small-button"
                            disabled={markCooked.isPending}
                            onClick={() => markCooked.mutate(meal.recipe.id)}
                          >
                            {t('recipes.markCooked')}
                          </button>
                        )}
                        {meal && (
                          <>
                            <button type="button" className="button small-button" onClick={() => setMoving(place)}>
                              {t('plan.move')}
                            </button>
                            <button
                              type="button"
                              className="button small-button"
                              disabled={remove.isPending}
                              onClick={() => remove.mutate(place)}
                            >
                              {t('plan.remove')}
                            </button>
                          </>
                        )}
                      </div>
                      {samePlace(picking, place) && (
                        <RecipePicker
                          householdId={householdId}
                          busy={choose.isPending}
                          onPick={(recipeId) => choose.mutate({ place, recipeId })}
                          onCancel={() => setPicking(null)}
                        />
                      )}
                      {samePlace(moving, place) && (
                        <MoveTarget
                          plan={plan.data}
                          from={place}
                          busy={move.isPending}
                          onMove={(to) => move.mutate({ from: place, to })}
                          onCancel={() => setMoving(null)}
                        />
                      )}
                    </div>
                  )
                })}
              </section>
            ))}
          </div>
        </>
      )}
    </>
  )
}

function PlannedMealView({ householdId, meal, diet }: { householdId: string; meal: PlannedMeal; diet?: Diet }) {
  const { t } = useTranslation()
  const notes = mealNotes(meal)
  // Generated meals never contain it; a recipe chosen by hand, or before the restrictions changed, might.
  const notEaten = diet ? conflicts(meal.recipe.contains, diet) : []
  return (
    <>
      <span className="recipe-heading">
        <Link to={`/households/${householdId}/recipes/${meal.recipe.id}`}>
          <strong>{meal.recipe.name}</strong>
        </Link>
        {meal.cooked && <span className="badge badge-owner">{t('plan.cooked')}</span>}
        {meal.origin === 'GENERATED' && <span className="badge">{t('plan.generatedBadge')}</span>}
      </span>
      {notEaten.length > 0 && (
        <span role="alert" className="warning">
          {t('diet.conflict', { traits: traitList(notEaten) })}
        </span>
      )}
      <span className="muted small">
        {t('recipes.minutes', { count: meal.recipe.totalMinutes })} · {t(`recipes.difficulty.${meal.recipe.difficulty}`)}
      </span>
      {notes.length > 0 && (
        <ul className="plan-notes">
          {notes.map((note) => (
            <li key={note}>{note}</li>
          ))}
        </ul>
      )}
    </>
  )
}

/** The recipes the household eats, to choose one for a meal. */
function RecipePicker({
  householdId,
  busy,
  onPick,
  onCancel,
}: {
  householdId: string
  busy: boolean
  onPick: (recipeId: string) => void
  onCancel: () => void
}) {
  const { t } = useTranslation()
  const lang = currentLocale()
  const [searchText, setSearchText] = useState('')
  const text = useDebounced(searchText, 300)
  const filter = { text, maxMinutes: null, course: null, page: 0 }
  const recipes = useQuery({
    queryKey: [...queryKeys.recipeList(householdId, filter, lang), ALL_RECIPES],
    queryFn: () => recipesApi.list(householdId, filter, lang, ALL_RECIPES),
  })

  return (
    <div className="plan-picker" role="dialog" aria-label={t('plan.pickerTitle')}>
      <strong>{t('plan.pickerTitle')}</strong>
      <input
        type="search"
        aria-label={t('recipes.search')}
        placeholder={t('recipes.searchPlaceholder')}
        value={searchText}
        onChange={(event) => setSearchText(event.target.value)}
      />
      {recipes.isPending && <p className="muted">{t('app.loading')}</p>}
      <ErrorMessage error={recipes.error} />
      {recipes.data?.items.length === 0 && <p className="muted">{t('recipes.emptyFiltered')}</p>}
      <ul>
        {recipes.data?.items.map((recipe) => (
          <li key={recipe.id}>
            <button type="button" className="button small-button" disabled={busy} onClick={() => onPick(recipe.id)}>
              {recipe.name}
            </button>
            <span className="muted small">{t('recipes.minutes', { count: recipe.totalMinutes })}</span>
          </li>
        ))}
      </ul>
      <button type="button" className="button ghost small-button" onClick={onCancel}>
        {t('inventory.cancel')}
      </button>
    </div>
  )
}

/** Where to move a meal to: any other meal of the week on screen. A taken one swaps with it. */
function MoveTarget({
  plan,
  from,
  busy,
  onMove,
  onCancel,
}: {
  plan: MealPlan
  from: Place
  busy: boolean
  onMove: (to: Place) => void
  onCancel: () => void
}) {
  const { t } = useTranslation()
  const places = weekDays(plan.weekStart)
    .flatMap((date) => MEAL_SLOTS.map((slot) => ({ date, slot })))
    .filter((place) => !samePlace(from, place))

  return (
    <div className="plan-picker" role="dialog" aria-label={t('plan.moveTitle')}>
      <label>
        <strong>{t('plan.moveTitle')}</strong>
        <select
          defaultValue=""
          disabled={busy}
          onChange={(event) => {
            const [date, slot] = event.target.value.split('|')
            if (date) onMove({ date, slot: slot as MealSlot })
          }}
        >
          <option value="" disabled>
            {t('plan.moveChoose')}
          </option>
          {places.map((place) => {
            const taken = plan.meals.find((meal) => samePlace(meal, place))
            const where = t('plan.movePlace', { day: weekdayAndDay(place.date), slot: t(`plan.slot.${place.slot}`) })
            return (
              <option key={`${place.date}|${place.slot}`} value={`${place.date}|${place.slot}`}>
                {taken ? t('plan.moveSwap', { place: where, recipe: taken.recipe.name }) : where}
              </option>
            )
          })}
        </select>
      </label>
      <button type="button" className="button ghost small-button" onClick={onCancel}>
        {t('inventory.cancel')}
      </button>
    </div>
  )
}
