import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { MAX_MUST_USE, SERVINGS, recipesApi } from '../api/recipes'
import type { AvailableFood, GeneratedIngredient, GeneratedRecipe } from '../api/recipes'
import { queryKeys } from '../api/queryKeys'
import { ErrorMessage } from '../components/ErrorMessage'
import { currentLocale } from '../i18n'
import { formatQuantity } from '../inventory/format'

/** Food this close to its date is pointed out: it is why the recipe uses it. */
const PRESSING_DAYS = 5

/** "Create a recipe with what I have": a language model writes one from the household's inventory. */
export function GeneratedRecipePage() {
  const { householdId = '' } = useParams()
  const { t } = useTranslation()
  const lang = currentLocale()
  const [servings, setServings] = useState(2)
  const [mustUse, setMustUse] = useState<string[]>([])

  // What the model will be given: shown, so that nothing about it is a mystery.
  const available = useQuery({
    queryKey: queryKeys.generatedIngredients(householdId, lang),
    queryFn: () => recipesApi.available(householdId, lang),
    staleTime: 0,
  })
  const generate = useMutation({ mutationFn: () => recipesApi.generate(householdId, lang, servings, mustUse) })

  function toggle(foodId: string) {
    setMustUse((current) => (current.includes(foodId) ? current.filter((id) => id !== foodId) : [...current, foodId]))
  }
  const recipe = generate.data

  return (
    <>
      <Link to={`/households/${householdId}/recipes`} className="back-link">
        ← {t('recipes.backToRecipes')}
      </Link>
      <h1>{t('generated.title')}</h1>
      <p className="muted">{t('generated.intro')}</p>

      {available.data && (
        <section aria-labelledby="generated-available">
          <h2 id="generated-available">{t('generated.available')}</h2>
          {available.data.length === 0 ? (
            <p className="muted">{t('errors.NOTHING_TO_COOK_WITH')}</p>
          ) : (
            <>
              <p className="muted small">{t('generated.mustUseHelp', { count: MAX_MUST_USE })}</p>
              <ul className="chips" aria-labelledby="generated-available">
                {available.data.map((food) => (
                  <li key={food.foodId ?? food.name}>
                    <AvailableChip
                      food={food}
                      chosen={food.foodId !== null && mustUse.includes(food.foodId)}
                      disabled={
                        food.foodId === null || (mustUse.length >= MAX_MUST_USE && !mustUse.includes(food.foodId))
                      }
                      onToggle={() => food.foodId && toggle(food.foodId)}
                    />
                  </li>
                ))}
              </ul>
            </>
          )}
        </section>
      )}
      <ErrorMessage error={available.error} />

      <div className="button-row">
        <label htmlFor="generated-servings">{t('generated.servings')}</label>
        <select id="generated-servings" value={servings} onChange={(event) => setServings(Number(event.target.value))}>
            {SERVINGS.map((count) => (
              <option key={count} value={count}>
                {count}
              </option>
            ))}
        </select>
        <button type="button" className="button primary" disabled={generate.isPending} onClick={() => generate.mutate()}>
          {recipe ? t('generated.again') : t('generated.create')}
        </button>
      </div>
      {generate.isPending && (
        <p role="status" className="muted">
          {t('generated.writing')}
        </p>
      )}
      <ErrorMessage error={generate.error} />

      {recipe && !generate.isPending && <WrittenRecipe recipe={recipe} />}
    </>
  )
}

function WrittenRecipe({ recipe }: { recipe: GeneratedRecipe }) {
  const { t } = useTranslation()
  return (
    <article aria-labelledby="generated-title">
      <h2 id="generated-title">{recipe.title}</h2>
      <p>{recipe.summary}</p>
      <p className="muted small">
        {t('recipes.minutes', { count: recipe.minutes })} · {t(`recipes.difficulty.${recipe.difficulty}`)} ·{' '}
        {t('recipes.servings', { count: recipe.servings })}
      </p>
      {/* Written by a model: the person cooking has the last word on amounts, times and doneness. */}
      <p role="note" className="warning">
        {t('generated.notice')}
      </p>

      <section className="card recipe-section" aria-labelledby="generated-ingredients">
        <h3 id="generated-ingredients">{t('recipes.ingredients')}</h3>
        <ul className="ingredient-list" aria-labelledby="generated-ingredients">
          {recipe.ingredients.map((ingredient) => (
            <li key={ingredient.name}>
              <span>
                {ingredient.name}{' '}
                <span className="muted small">
                  {formatQuantity({ amount: ingredient.amount, unit: ingredient.unit })}
                  {ingredient.optional && ` · ${t('generated.optional')}`}
                  {ingredient.staple && ` · ${t('recipes.staple')}`}
                </span>
              </span>
              <ExpiryBadge ingredient={ingredient} />
            </li>
          ))}
        </ul>
      </section>

      <section className="card recipe-section" aria-labelledby="generated-steps">
        <h3 id="generated-steps">{t('recipes.steps')}</h3>
        <ol className="step-list">
          {recipe.steps.map((step, index) => (
            <li key={index}>{step}</li>
          ))}
        </ol>
      </section>
    </article>
  )
}

/** A food at home, which can be required (pressed) when it is a catalog food. */
function AvailableChip({
  food,
  chosen,
  disabled,
  onToggle,
}: {
  food: AvailableFood
  chosen: boolean
  disabled: boolean
  onToggle: () => void
}) {
  const { t } = useTranslation()
  const days = food.daysLeft
  const expiry =
    days === null || days > PRESSING_DAYS
      ? null
      : days === 0
        ? t(food.estimated ? 'recipes.badge.todayEstimated' : 'recipes.badge.today')
        : t(food.estimated ? 'recipes.badge.daysEstimated' : 'recipes.badge.days', { count: days })
  return (
    <button type="button" className="chip" aria-pressed={chosen} disabled={disabled} onClick={onToggle}>
      {food.name} · {formatQuantity({ amount: food.amount, unit: food.unit })}
      {expiry && ` · ${expiry}`}
    </button>
  )
}

/** Why the recipe uses this food: the date of what is at home, from the inventory, not from the model. */
function ExpiryBadge({ ingredient }: { ingredient: GeneratedIngredient }) {
  const { t } = useTranslation()
  const days = ingredient.daysLeft
  if (ingredient.staple || days === null || days > PRESSING_DAYS) return null
  return (
    <span className="item-badges">
      <span className="badge priority priority-urgent">
        {days === 0
          ? t(ingredient.estimated ? 'recipes.badge.todayEstimated' : 'recipes.badge.today')
          : t(ingredient.estimated ? 'recipes.badge.daysEstimated' : 'recipes.badge.days', { count: days })}
      </span>
    </span>
  )
}
