import { describe, expect, it } from 'vitest'
import type { PlannedIngredient, PlannedMeal } from '../api/mealPlan'
import i18n from '../i18n'
import { addDays, daysBetween, mealNotes, shortDay, unusedLine, weekDays, weekdayAndDay } from './wording'

function ingredient(name: string, changes: Partial<PlannedIngredient> = {}): PlannedIngredient {
  return {
    foodId: name,
    name,
    amount: 1,
    unit: 'UNIT',
    staple: false,
    availability: 'ENOUGH',
    expirationDate: null,
    estimated: false,
    ...changes,
  }
}

function meal(ingredients: PlannedIngredient[]): PlannedMeal {
  return {
    id: 'm1',
    date: '2026-10-05',
    slot: 'LUNCH',
    origin: 'GENERATED',
    recipe: { id: 'r1', name: 'Pasta', servings: 2, totalMinutes: 25, difficulty: 'EASY', contains: [] },
    ingredients,
  }
}

describe('days of the plan', () => {
  it('moves across months and years without shifting days', () => {
    expect(addDays('2026-10-05', 7)).toBe('2026-10-12')
    expect(addDays('2026-10-29', 7)).toBe('2026-11-05')
    expect(addDays('2026-01-03', -7)).toBe('2025-12-27')
    // The night the clocks go back in Spain has 25 hours.
    expect(addDays('2026-10-24', 1)).toBe('2026-10-25')
    expect(addDays('2026-10-25', 1)).toBe('2026-10-26')
    expect(daysBetween('2026-10-24', '2026-10-27')).toBe(3)
    expect(daysBetween('2026-10-05', '2026-10-05')).toBe(0)
  })

  it('lists the seven days of a week', () => {
    expect(weekDays('2026-10-05')).toEqual([
      '2026-10-05',
      '2026-10-06',
      '2026-10-07',
      '2026-10-08',
      '2026-10-09',
      '2026-10-10',
      '2026-10-11',
    ])
  })

  it('names days in the language of the user', async () => {
    expect(shortDay('2026-10-05')).toBe('5 oct')
    expect(weekdayAndDay('2026-10-05')).toBe('lunes 5 oct')
    await i18n.changeLanguage('en')
    expect(shortDay('2026-10-05')).toBe('5 Oct')
    expect(weekdayAndDay('2026-10-05')).toBe('Monday 5 Oct')
  })
})

describe('notes of a planned meal', () => {
  it('says first what the meal saves from expiring, the soonest first', () => {
    expect(
      mealNotes(
        meal([
          ingredient('Tomate', { expirationDate: '2026-10-08' }),
          ingredient('Calabacín', { expirationDate: '2026-10-06' }),
          ingredient('Pasta', { expirationDate: '2027-03-01' }),
          ingredient('Sal', { staple: true, availability: 'ASSUMED' }),
        ]),
      ),
    ).toEqual([
      'Aprovecha calabacín, con caducidad el 6 oct.',
      'Aprovecha tomate, con caducidad el 8 oct.',
      'Habrá en casa todos los ingredientes.',
    ])
  })

  it('always says so when a date is an estimate', () => {
    expect(mealNotes(meal([ingredient('Champiñones', { expirationDate: '2026-10-06', estimated: true })]))[0]).toBe(
      'Aprovecha champiñones, con caducidad estimada el 6 oct.',
    )
  })

  it('names what has to be bought', () => {
    expect(
      mealNotes(
        meal([
          ingredient('Pasta'),
          ingredient('Mozzarella', { availability: 'MISSING' }),
          ingredient('Tomate', { availability: 'MISSING' }),
        ]),
      ),
    ).toEqual(['Falta por comprar: mozzarella, tomate.'])
  })

  it('does not claim there is enough when there is less, or when it cannot be compared', () => {
    expect(
      mealNotes(
        meal([
          ingredient('Pasta', { availability: 'PARTIAL' }),
          ingredient('Tomate', { availability: 'UNKNOWN_QUANTITY' }),
        ]),
      ),
    ).toEqual([
      'Habrá en casa todos los ingredientes.',
      'Habrá menos pasta de lo que pide la receta.',
      'Comprueba la cantidad de tomate: no podemos compararla con la de la receta.',
    ])
  })

  it('says nothing about a meal in the past', () => {
    expect(mealNotes(meal([]))).toEqual([])
  })

  it('is worded in English too', async () => {
    await i18n.changeLanguage('en')
    expect(
      mealNotes(
        meal([
          ingredient('Zucchini', { expirationDate: '2026-10-06', estimated: true }),
          ingredient('Pasta', { availability: 'MISSING' }),
        ]),
      ),
    ).toEqual(['Uses zucchini, estimated to expire on 6 Oct.', 'To buy: pasta.'])
  })
})

describe('food the plan lets expire', () => {
  it('says how much is left and when it expires', () => {
    expect(unusedLine({ name: 'Yogur', amount: 4, unit: 'UNIT', expirationDate: '2026-10-03', estimated: false })).toBe(
      'Yogur (4 uds), con caducidad el 3 oct',
    )
    expect(
      unusedLine({ name: 'Pechuga de pollo', amount: 0.3, unit: 'KILOGRAM', expirationDate: '2026-10-04', estimated: true }),
    ).toBe('Pechuga de pollo (0,3 kg), con caducidad estimada el 4 oct')
  })
})
