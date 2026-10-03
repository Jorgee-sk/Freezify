import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { COURSES, TIME_LIMITS, dietApi, recipesApi } from '../api/recipes'
import type { Course, Recommendation } from '../api/recipes'
import { queryKeys } from '../api/queryKeys'
import { ErrorMessage } from '../components/ErrorMessage'
import { currentLocale } from '../i18n'
import { useDebounced } from '../inventory/useDebounced'
import { RecipeFacts } from '../recipes/RecipeFacts'
import { dietSummary } from '../recipes/diet'
import { reasons } from '../recipes/explain'
import { subscribeToHousehold } from '../realtime/householdEvents'

/** How many recommendations are shown before the catalog. */
const SHOWN_RECOMMENDATIONS = 5

export function RecipesPage() {
  const { householdId = '' } = useParams()
  const { t } = useTranslation()
  const lang = currentLocale()
  const [searchText, setSearchText] = useState('')
  const [maxMinutes, setMaxMinutes] = useState<number | null>(null)
  const [course, setCourse] = useState<Course | null>(null)
  const [page, setPage] = useState(0)
  const text = useDebounced(searchText, 300)

  const recommendations = useQuery({
    queryKey: queryKeys.recommendations(householdId, lang),
    queryFn: () => recipesApi.recommendations(householdId, lang),
    // They depend on the inventory, which changes elsewhere: ask again every time the page is opened.
    staleTime: 0,
  })
  const filter = { text, maxMinutes, course, page }
  const catalog = useQuery({
    queryKey: queryKeys.recipeList(householdId, filter, lang),
    queryFn: () => recipesApi.list(householdId, filter, lang),
    placeholderData: keepPreviousData,
  })

  const diet = useQuery({ queryKey: queryKeys.diet(householdId), queryFn: () => dietApi.get(householdId) })

  // Recommendations depend on the inventory: whatever another member changes shows up without reloading.
  const queryClient = useQueryClient()
  const listening = recommendations.isSuccess
  useEffect(() => {
    if (!listening) return
    return subscribeToHousehold(householdId, (event) => {
      if (event === 'meal-plan-changed') return
      void queryClient.invalidateQueries({ queryKey: queryKeys.recommendations(householdId, lang) })
    })
  }, [householdId, lang, listening, queryClient])
  const restrictions = diet.data ? dietSummary(diet.data) : null

  /** Any change of filter starts again from the first page. */
  function filtering<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value)
      setPage(0)
    }
  }

  const recipePath = (recipeId: string) => `/households/${householdId}/recipes/${recipeId}`

  return (
    <>
      <Link to={`/households/${householdId}`} className="back-link">
        ← {t('households.backToInventory')}
      </Link>
      <h1>{t('recipes.title')}</h1>
      {/* Says what is being left out, so that a short list is never a mystery. */}
      {diet.data && (
        <p className="diet-line">
          <span>{restrictions ? t('diet.applied', { restrictions }) : t('diet.none')}</span>
          <Link to={`/households/${householdId}/diet`}>{t('diet.change')}</Link>
        </p>
      )}

      <section aria-labelledby="recommended-title">
        <h2 id="recommended-title">{t('recipes.recommendedTitle')}</h2>
        {recommendations.isPending && <p className="muted">{t('app.loading')}</p>}
        <ErrorMessage error={recommendations.error} />
        {recommendations.data?.length === 0 && <p className="card muted">{t('recipes.noRecommendations')}</p>}
        <ul className="recipe-list" aria-labelledby="recommended-title">
          {recommendations.data?.slice(0, SHOWN_RECOMMENDATIONS).map((recommendation) => (
            <li key={recommendation.recipe.id}>
              <RecommendationCard recommendation={recommendation} to={recipePath(recommendation.recipe.id)} />
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="catalog-title">
        <h2 id="catalog-title">{t('recipes.catalogTitle')}</h2>
        <div className="filters" role="search">
          <input
            type="search"
            aria-label={t('recipes.search')}
            placeholder={t('recipes.searchPlaceholder')}
            value={searchText}
            onChange={(event) => filtering(setSearchText)(event.target.value)}
          />
          <select
            aria-label={t('recipes.time')}
            value={maxMinutes ?? ''}
            onChange={(event) => filtering(setMaxMinutes)(event.target.value ? Number(event.target.value) : null)}
          >
            <option value="">{t('recipes.anyTime')}</option>
            {TIME_LIMITS.map((minutes) => (
              <option key={minutes} value={minutes}>
                {t('recipes.upTo', { count: minutes })}
              </option>
            ))}
          </select>
          <select
            aria-label={t('recipes.course')}
            value={course ?? ''}
            onChange={(event) => filtering(setCourse)((event.target.value || null) as Course | null)}
          >
            <option value="">{t('recipes.anyCourse')}</option>
            {COURSES.map((option) => (
              <option key={option} value={option}>
                {t(`recipes.courseOption.${option}`)}
              </option>
            ))}
          </select>
        </div>

        {catalog.isPending && <p className="muted">{t('app.loading')}</p>}
        <ErrorMessage error={catalog.error} />
        {catalog.data?.items.length === 0 && <p className="card muted">{t('recipes.emptyFiltered')}</p>}
        <ul className="recipe-list" aria-labelledby="catalog-title">
          {catalog.data?.items.map((recipe) => (
            <li key={recipe.id}>
              <Link to={recipePath(recipe.id)} className="card recipe-card">
                <strong>{recipe.name}</strong>
                <span className="muted">{recipe.description}</span>
                <RecipeFacts recipe={recipe} />
              </Link>
            </li>
          ))}
        </ul>

        {catalog.data && catalog.data.totalPages > 1 && (
          <nav className="pagination">
            <button type="button" className="button" disabled={page === 0} onClick={() => setPage(page - 1)}>
              {t('inventory.previous')}
            </button>
            <span className="muted">{t('inventory.pageOf', { page: page + 1, total: catalog.data.totalPages })}</span>
            <button
              type="button"
              className="button"
              disabled={page + 1 >= catalog.data.totalPages}
              onClick={() => setPage(page + 1)}
            >
              {t('inventory.next')}
            </button>
          </nav>
        )}
      </section>
    </>
  )
}

/** A recommended recipe with the reasons for recommending it, all of them taken from the inventory. */
function RecommendationCard({ recommendation, to }: { recommendation: Recommendation; to: string }) {
  const { t } = useTranslation()
  const { recipe } = recommendation
  return (
    <Link to={to} className="card recipe-card recommendation">
      <span className="recipe-heading">
        <strong>{recipe.name}</strong>
        <span className="badge badge-owner">{t('recipes.fit', { percent: Math.round(recommendation.score * 100) })}</span>
      </span>
      <span className="muted small">{t('recipes.because')}</span>
      <ul>
        {reasons(recommendation).map((reason) => (
          <li key={reason}>{reason}</li>
        ))}
      </ul>
    </Link>
  )
}
