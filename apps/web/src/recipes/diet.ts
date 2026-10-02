import type { Diet, FoodTrait } from '../api/recipes'
import i18n from '../i18n'

const EXCLUDED_BY_DIET: Record<Diet['type'], FoodTrait[]> = {
  NONE: [],
  VEGETARIAN: ['MEAT', 'PORK', 'FISH', 'SHELLFISH'],
  VEGAN: ['MEAT', 'PORK', 'FISH', 'SHELLFISH', 'DAIRY', 'EGG'],
}

/** "Vegetariana · sin gluten, sin soja", or null when the household has no restrictions. */
export function dietSummary(diet: Diet): string | null {
  const parts: string[] = []
  if (diet.type !== 'NONE') parts.push(i18n.t(`diet.type.${diet.type}`))
  if (diet.avoided.length > 0) {
    parts.push(diet.avoided.map((trait) => i18n.t('diet.without', { trait: i18n.t(`diet.trait.${trait}`) })).join(', '))
  }
  return parts.length > 0 ? parts.join(' · ') : null
}

/** "gluten, lácteos, huevo": what a recipe contains that someone may not eat. */
export function traitList(traits: FoodTrait[]): string {
  return traits.map((trait) => i18n.t(`diet.trait.${trait}`)).join(', ')
}

/**
 * What a recipe contains that the household does not eat. The server already leaves such recipes out of every
 * list; this is for a recipe opened directly, so that it never looks fine when it is not.
 */
export function conflicts(contains: FoodTrait[], diet: Diet): FoodTrait[] {
  const excluded = new Set<FoodTrait>([...EXCLUDED_BY_DIET[diet.type], ...diet.avoided])
  return contains.filter((trait) => excluded.has(trait))
}
