import type { Quantity, Unit } from '../api/inventory'
import i18n, { currentLocale } from '../i18n'

const SAME_DIMENSION: Record<Unit, Unit[]> = {
  GRAM: ['GRAM', 'KILOGRAM'],
  KILOGRAM: ['GRAM', 'KILOGRAM'],
  MILLILITER: ['MILLILITER', 'LITER'],
  LITER: ['MILLILITER', 'LITER'],
  UNIT: ['UNIT'],
}

/** Units a quantity in `unit` can be expressed in; grams can become kilograms but never liters. */
export function compatibleUnits(unit: Unit): Unit[] {
  return SAME_DIMENSION[unit]
}

function languageTag(): string {
  return currentLocale() === 'es' ? 'es-ES' : 'en-GB'
}

export function formatAmount(amount: number): string {
  return new Intl.NumberFormat(languageTag(), { maximumFractionDigits: 3 }).format(amount)
}

/** "500 g", "1,5 kg", "6 uds". */
export function formatQuantity(quantity: Quantity): string {
  return `${formatAmount(quantity.amount)} ${i18n.t(`units.${quantity.unit}`, { count: quantity.amount })}`
}

/** Formats a calendar day ("2026-10-05") as day/month/year, without shifting it across time zones. */
export function formatDay(isoDay: string): string {
  return new Intl.DateTimeFormat(languageTag(), {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(`${isoDay}T00:00:00Z`))
}

/** Today's calendar day where the user is, as "YYYY-MM-DD". */
export function todayIso(now: Date = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

/** Accepts what people type in Spain ("1,5") as well as "1.5". Returns null unless it is a positive number. */
export function parseAmount(text: string): number | null {
  const trimmed = text.trim()
  const value = Number(trimmed.replace(',', '.'))
  return trimmed !== '' && Number.isFinite(value) && value > 0 ? value : null
}
