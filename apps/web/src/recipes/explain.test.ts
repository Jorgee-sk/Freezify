import { describe, expect, it } from 'vitest'
import type { Availability, MatchedIngredient, Recommendation } from '../api/recipes'
import i18n from '../i18n'
import { reasons } from './explain'

function ingredient(
  name: string,
  availability: Availability,
  overrides: Partial<MatchedIngredient> = {},
): MatchedIngredient {
  return {
    foodId: name,
    name,
    amount: 1,
    unit: 'UNIT',
    staple: availability === 'ASSUMED',
    availability,
    expirationDate: null,
    estimated: false,
    daysUntilExpiration: null,
    priority: null,
    ...overrides,
  }
}

function expiring(name: string, days: number, estimated = false): MatchedIngredient {
  return ingredient(name, 'ENOUGH', { expirationDate: '2026-10-04', daysUntilExpiration: days, estimated })
}

function recommendation(
  ingredients: MatchedIngredient[],
  overrides: Partial<Recommendation> = {},
): Recommendation {
  return {
    recipe: {
      id: 'r1',
      name: 'Pasta con verduras',
      description: '',
      servings: 2,
      prepMinutes: 10,
      cookMinutes: 15,
      totalMinutes: 25,
      difficulty: 'EASY',
      course: 'MAIN',
      contains: [],
    },
    score: 0.8,
    factors: { ingredientMatch: 0.75, expiryUrgency: 0.5, convenience: 0.9, novelty: 1 },
    ingredients,
    daysSinceCooked: null,
    ...overrides,
  }
}

describe('reasons', () => {
  it('explains a recommendation with the food that is really at home', () => {
    const lines = reasons(
      recommendation([
        ingredient('Pasta', 'ENOUGH'),
        expiring('Calabacín', 2),
        ingredient('Tomate', 'ENOUGH'),
        ingredient('Mozzarella', 'MISSING'),
        ingredient('Sal', 'ASSUMED'),
      ]),
    )

    expect(lines).toEqual([
      'Tienes calabacín con caducidad en 2 días.',
      'Tienes 3 de 4 ingredientes.',
      'Solo te falta: mozzarella.',
      'Tiempo aproximado: 25 min.',
    ])
  })

  it('puts what expires first at the top and words estimates as estimates', () => {
    const lines = reasons(
      recommendation([expiring('Tomate', 4), expiring('Merluza', 1, true), expiring('Leche', 0), expiring('Yogur', 0, true)]),
    )

    expect(lines.slice(0, 4)).toEqual([
      'Tienes leche con caducidad hoy.',
      'Tienes yogur con caducidad estimada hoy.',
      'Tienes merluza con caducidad estimada en 1 día.',
      'Tienes tomate con caducidad en 4 días.',
    ])
  })

  it('does not mention dates that are far away or unknown', () => {
    const lines = reasons(recommendation([expiring('Pasta', 200), ingredient('Arroz', 'ENOUGH')]))

    expect(lines).toEqual(['Tienes todos los ingredientes.', 'Tiempo aproximado: 25 min.'])
  })

  it('names a few missing ingredients and counts many', () => {
    const few = reasons(
      recommendation([ingredient('Pasta', 'ENOUGH'), ingredient('Atún en lata', 'MISSING'), ingredient('Huevos', 'MISSING')]),
    )
    expect(few).toContain('Tienes 1 de 3 ingredientes.')
    expect(few).toContain('Te faltan: atún en lata, huevos.')

    const many = reasons(
      recommendation([
        ingredient('Calabacín', 'ENOUGH'),
        ...['Berenjena', 'Pimiento rojo', 'Pimiento verde', 'Cebolla'].map((name) => ingredient(name, 'MISSING')),
      ]),
    )
    expect(many).toContain('Te faltan 4 ingredientes.')
  })

  it('never says a missing ingredient is about to expire', () => {
    const lines = reasons(
      recommendation([ingredient('Pasta', 'ENOUGH'), ingredient('Leche', 'MISSING', { daysUntilExpiration: 1 })]),
    )

    expect(lines.join(' ')).not.toContain('caducidad')
  })

  it('says when there is less than needed or when amounts cannot be compared', () => {
    const lines = reasons(recommendation([ingredient('Pasta', 'PARTIAL'), ingredient('Tomate', 'UNKNOWN_QUANTITY')]))

    expect(lines).toContain('Tienes menos pasta de lo que pide la receta.')
    expect(lines).toContain('Comprueba la cantidad de tomate: no podemos compararla con la de la receta.')
    // Both count as being at home.
    expect(lines).toContain('Tienes todos los ingredientes.')
  })

  it('says when the household cooked it lately', () => {
    const pasta = [ingredient('Pasta', 'ENOUGH')]

    expect(reasons(recommendation(pasta, { daysSinceCooked: 0 }))).toContain('La has cocinado hoy.')
    expect(reasons(recommendation(pasta, { daysSinceCooked: 1 }))).toContain('La cocinaste hace 1 día.')
    expect(reasons(recommendation(pasta, { daysSinceCooked: 3 }))).toContain('La cocinaste hace 3 días.')
  })

  it('follows the language of the app', async () => {
    await i18n.changeLanguage('en')

    expect(reasons(recommendation([expiring('Zucchini', 1, true), ingredient('Mozzarella', 'MISSING')]))).toEqual([
      'You have zucchini estimated to expire in about 1 day.',
      'You have 1 of 2 ingredients.',
      'You only need: mozzarella.',
      'About 25 min.',
    ])
  })
})
