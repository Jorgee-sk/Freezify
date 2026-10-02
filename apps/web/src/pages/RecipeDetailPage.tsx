import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { recipesApi } from '../api/recipes'
import type { MatchedIngredient } from '../api/recipes'
import { queryKeys } from '../api/queryKeys'
import { ErrorMessage } from '../components/ErrorMessage'
import { currentLocale } from '../i18n'
import { formatQuantity } from '../inventory/format'
import { RecipeFacts } from '../recipes/RecipeFacts'

export function RecipeDetailPage() {
  const { householdId = '', recipeId = '' } = useParams()
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const lang = currentLocale()
  const [cooked, setCooked] = useState(false)

  const detail = useQuery({
    queryKey: queryKeys.recipe(recipeId, lang),
    queryFn: () => recipesApi.get(recipeId, lang),
  })
  // Tells, ingredient by ingredient, what this household has. A recipe that uses nothing at home is not in it.
  const recommendations = useQuery({
    queryKey: queryKeys.recommendations(householdId, lang),
    queryFn: () => recipesApi.recommendations(householdId, lang),
    staleTime: 0,
  })
  const markCooked = useMutation({
    mutationFn: () => recipesApi.markCooked(householdId, recipeId),
    onSuccess: async () => {
      setCooked(true)
      await queryClient.invalidateQueries({ queryKey: queryKeys.recommendations(householdId, lang) })
    },
  })

  const back = (
    <Link to={`/households/${householdId}/recipes`} className="back-link">
      ← {t('recipes.backToRecipes')}
    </Link>
  )
  if (detail.isPending) return <p className="muted">{t('app.loading')}</p>
  if (detail.isError) {
    return (
      <>
        <ErrorMessage error={detail.error} />
        {back}
      </>
    )
  }

  const { recipe, ingredients, steps } = detail.data
  const matched = new Map<string, MatchedIngredient>(
    recommendations.data
      ?.find((recommendation) => recommendation.recipe.id === recipeId)
      ?.ingredients.map((ingredient) => [ingredient.foodId, ingredient]),
  )

  return (
    <>
      {back}
      <h1>{recipe.name}</h1>
      <p>{recipe.description}</p>
      <p>
        <RecipeFacts recipe={recipe} />
      </p>

      <section className="card recipe-section" aria-labelledby="ingredients-title">
        <h2 id="ingredients-title">{t('recipes.ingredients')}</h2>
        <ul className="ingredient-list" aria-labelledby="ingredients-title">
          {ingredients.map((ingredient) => (
            <li key={ingredient.foodId}>
              <span>
                {ingredient.name}{' '}
                <span className="muted small">
                  {formatQuantity({ amount: ingredient.amount, unit: ingredient.unit })}
                  {/* A staple is listed but not looked for in the inventory. */}
                  {ingredient.staple && ` · ${t('recipes.staple')}`}
                </span>
              </span>
              <AvailabilityBadge ingredient={ingredient.staple ? undefined : matched.get(ingredient.foodId)} />
            </li>
          ))}
        </ul>
        {recommendations.isSuccess && matched.size === 0 && (
          <p className="muted small">{t('recipes.nothingAtHome')}</p>
        )}
      </section>

      <section className="card recipe-section" aria-labelledby="steps-title">
        <h2 id="steps-title">{t('recipes.steps')}</h2>
        <ol className="step-list">
          {steps.map((step) => (
            <li key={step}>{step}</li>
          ))}
        </ol>
      </section>

      <ErrorMessage error={markCooked.error} />
      <div className="button-row">
        <button type="button" className="button primary" disabled={markCooked.isPending} onClick={() => markCooked.mutate()}>
          {t('recipes.markCooked')}
        </button>
        {cooked && (
          <span role="status" className="muted">
            {t('recipes.cookedSaved')}
          </span>
        )}
      </div>
      <p className="muted small">{t('recipes.cookedHelp')}</p>
    </>
  )
}

/** What the household has of an ingredient, when that is known. */
function AvailabilityBadge({ ingredient }: { ingredient: MatchedIngredient | undefined }) {
  const { t } = useTranslation()
  if (!ingredient) return null
  const days = ingredient.daysUntilExpiration
  const expiring = ingredient.availability !== 'MISSING' && days !== null && days <= 5
  return (
    <span className="item-badges">
      {expiring && (
        <span className="badge priority priority-urgent">
          {days === 0
            ? t(ingredient.estimated ? 'recipes.badge.todayEstimated' : 'recipes.badge.today')
            : t(ingredient.estimated ? 'recipes.badge.daysEstimated' : 'recipes.badge.days', { count: days })}
        </span>
      )}
      <span className={`badge availability-${ingredient.availability.toLowerCase()}`}>
        {t(`recipes.availability.${ingredient.availability}`)}
      </span>
    </span>
  )
}
