import type { MatchedIngredient, Recommendation } from '../api/recipes'
import i18n from '../i18n'

/** How many missing ingredients are named before they are only counted. */
const NAMED_MISSING = 3

function sentence(name: string): string {
  return name.charAt(0).toLocaleLowerCase() + name.slice(1)
}

/**
 * Why a recipe is recommended, in sentences. Every sentence comes from the data of the recommendation: the
 * food the household really has, its real dates, what is really missing. Nothing is made up, and a date that
 * is an estimate is said to be one.
 */
export function reasons(recommendation: Recommendation): string[] {
  const needed = recommendation.ingredients.filter((ingredient) => !ingredient.staple)
  const atHome = needed.filter((ingredient) => ingredient.availability !== 'MISSING')
  const missing = needed.filter((ingredient) => ingredient.availability === 'MISSING')
  const lines: string[] = []

  // What cannot wait comes first: it is the reason the recipe is here.
  const expiring = atHome
    .filter((ingredient) => ingredient.daysUntilExpiration !== null && ingredient.daysUntilExpiration <= 5)
    .sort((one, other) => one.daysUntilExpiration! - other.daysUntilExpiration!)
  for (const ingredient of expiring) lines.push(expiringSentence(ingredient))

  if (missing.length === 0) {
    lines.push(i18n.t('recipes.reason.haveAll'))
  } else {
    lines.push(i18n.t('recipes.reason.have', { have: atHome.length, count: needed.length }))
    lines.push(
      missing.length <= NAMED_MISSING
        ? i18n.t('recipes.reason.missing', {
            count: missing.length,
            foods: missing.map((ingredient) => sentence(ingredient.name)).join(', '),
          })
        : i18n.t('recipes.reason.missingMany', { count: missing.length }),
    )
  }

  for (const ingredient of atHome) {
    if (ingredient.availability === 'PARTIAL') {
      lines.push(i18n.t('recipes.reason.partial', { food: sentence(ingredient.name) }))
    } else if (ingredient.availability === 'UNKNOWN_QUANTITY') {
      lines.push(i18n.t('recipes.reason.unknownQuantity', { food: sentence(ingredient.name) }))
    }
  }

  lines.push(i18n.t('recipes.reason.time', { minutes: recommendation.recipe.totalMinutes }))
  if (recommendation.daysSinceCooked !== null) {
    lines.push(
      recommendation.daysSinceCooked === 0
        ? i18n.t('recipes.reason.cookedToday')
        : i18n.t('recipes.reason.cooked', { count: recommendation.daysSinceCooked }),
    )
  }
  return lines
}

function expiringSentence(ingredient: MatchedIngredient): string {
  const days = ingredient.daysUntilExpiration!
  const key = `recipes.reason.${days === 0 ? 'expiresToday' : 'expires'}${ingredient.estimated ? 'Estimated' : ''}`
  return i18n.t(key, { food: sentence(ingredient.name), count: days })
}
