import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { SERVINGS, recipesApi } from '../api/recipes'
import type { GeneratedIngredient, GeneratedRecipe } from '../api/recipes'
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

  const generate = useMutation({ mutationFn: () => recipesApi.generate(householdId, lang, servings) })
  const recipe = generate.data

  return (
    <>
      <Link to={`/households/${householdId}/recipes`} className="back-link">
        ← {t('recipes.backToRecipes')}
      </Link>
      <h1>{t('generated.title')}</h1>
      <p className="muted">{t('generated.intro')}</p>

      <div className="filters">
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
