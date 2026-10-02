import { useTranslation } from 'react-i18next'
import type { RecipeSummary } from '../api/recipes'

/** "25 min · Fácil · 2 raciones". */
export function RecipeFacts({ recipe }: { recipe: RecipeSummary }) {
  const { t } = useTranslation()
  return (
    <span className="muted small">
      {t('recipes.minutes', { count: recipe.totalMinutes })} · {t(`recipes.difficulty.${recipe.difficulty}`)} ·{' '}
      {t('recipes.servings', { count: recipe.servings })}
    </span>
  )
}
