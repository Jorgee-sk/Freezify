import type { PlannedMeal, UnusedFood } from '../api/mealPlan'
import i18n, { currentLocale } from '../i18n'
import { formatQuantity } from '../inventory/format'

/** Food with this many days or fewer between the meal and its date is worth saying the meal makes use of. */
const WORTH_MENTIONING_DAYS = 5

function languageTag(): string {
  return currentLocale() === 'es' ? 'es-ES' : 'en-GB'
}

function utc(isoDay: string): Date {
  return new Date(`${isoDay}T00:00:00Z`)
}

function sentence(name: string): string {
  return name.charAt(0).toLocaleLowerCase() + name.slice(1)
}

/** The calendar day `days` after `isoDay`, without shifting it across time zones. */
export function addDays(isoDay: string, days: number): string {
  const date = utc(isoDay)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

export function daysBetween(fromIsoDay: string, toIsoDay: string): number {
  return Math.round((utc(toIsoDay).getTime() - utc(fromIsoDay).getTime()) / 86_400_000)
}

/** The seven days of the week that starts on `weekStart`. */
export function weekDays(weekStart: string): string[] {
  return Array.from({ length: 7 }, (_, index) => addDays(weekStart, index))
}

/** "5 oct". */
export function shortDay(isoDay: string): string {
  return new Intl.DateTimeFormat(languageTag(), { day: 'numeric', month: 'short', timeZone: 'UTC' }).format(utc(isoDay))
}

/** "lunes 5 oct". */
export function weekdayAndDay(isoDay: string): string {
  const weekday = new Intl.DateTimeFormat(languageTag(), { weekday: 'long', timeZone: 'UTC' }).format(utc(isoDay))
  return `${weekday} ${shortDay(isoDay)}`
}

/**
 * What a planned meal means for the food at home, in sentences. Every sentence comes from the data of the plan:
 * what the household would really have on that day and what would be missing. A date that is an estimate is
 * said to be one. Nothing is said about a meal in the past.
 */
export function mealNotes(meal: PlannedMeal): string[] {
  const needed = meal.ingredients.filter((ingredient) => !ingredient.staple)
  if (needed.length === 0) return []
  const lines: string[] = []

  // What the meal saves from expiring comes first: it is the reason to cook it that day.
  needed
    .filter((ingredient) => ingredient.availability !== 'MISSING' && ingredient.expirationDate !== null)
    .filter((ingredient) => daysBetween(meal.date, ingredient.expirationDate!) <= WORTH_MENTIONING_DAYS)
    .sort((one, other) => one.expirationDate!.localeCompare(other.expirationDate!))
    .forEach((ingredient) =>
      lines.push(
        i18n.t(ingredient.estimated ? 'plan.note.usesEstimated' : 'plan.note.uses', {
          food: sentence(ingredient.name),
          date: shortDay(ingredient.expirationDate!),
        }),
      ),
    )

  const missing = needed.filter((ingredient) => ingredient.availability === 'MISSING')
  lines.push(
    missing.length === 0
      ? i18n.t('plan.note.haveAll')
      : i18n.t('plan.note.missing', { foods: missing.map((ingredient) => sentence(ingredient.name)).join(', ') }),
  )
  for (const ingredient of needed) {
    if (ingredient.availability === 'PARTIAL') {
      lines.push(i18n.t('plan.note.partial', { food: sentence(ingredient.name) }))
    } else if (ingredient.availability === 'UNKNOWN_QUANTITY') {
      lines.push(i18n.t('recipes.reason.unknownQuantity', { food: sentence(ingredient.name) }))
    }
  }
  return lines
}

/** "Yogur (4 uds), con caducidad el 3 oct". */
export function unusedLine(food: UnusedFood): string {
  return i18n.t(food.estimated ? 'plan.unusedItemEstimated' : 'plan.unusedItem', {
    food: food.name,
    quantity: formatQuantity({ amount: food.amount, unit: food.unit }),
    date: shortDay(food.expirationDate),
  })
}
