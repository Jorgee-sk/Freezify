import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { MatchedIngredient, Recommendation, RecipeDetail, RecipeSummary } from '../api/recipes'
import { fakeApi, problem } from '../test/fakeApi'
import { ANA, CASA, renderApp, signedIn } from '../test/renderApp'

const CATALOG = 'GET /recipes'
const RECOMMENDATIONS = 'GET /households/h1/recipes/recommendations'

function recipe(id: string, name: string, overrides: Partial<RecipeSummary> = {}): RecipeSummary {
  return {
    id,
    name,
    description: `Descripción de ${name}`,
    servings: 2,
    prepMinutes: 10,
    cookMinutes: 15,
    totalMinutes: 25,
    difficulty: 'EASY',
    course: 'MAIN',
    contains: [],
    ...overrides,
  }
}

function matched(name: string, overrides: Partial<MatchedIngredient> = {}): MatchedIngredient {
  return {
    foodId: `food-${name}`,
    name,
    amount: 1,
    unit: 'UNIT',
    staple: false,
    availability: 'ENOUGH',
    expirationDate: null,
    estimated: false,
    daysUntilExpiration: null,
    priority: null,
    ...overrides,
  }
}

const PASTA = recipe('r1', 'Pasta con calabacín y tomate')
const TORTILLA = recipe('r2', 'Tortilla de patatas', { totalMinutes: 40, difficulty: 'MEDIUM', servings: 4 })

const PASTA_RECOMMENDATION: Recommendation = {
  recipe: PASTA,
  score: 0.763,
  factors: { ingredientMatch: 0.75, expiryUrgency: 0.5, convenience: 0.9, novelty: 1 },
  ingredients: [
    matched('Pasta', { amount: 200, unit: 'GRAM' }),
    matched('Calabacín', { expirationDate: '2026-10-04', daysUntilExpiration: 2, priority: 'URGENT' }),
    matched('Mozzarella', { amount: 100, unit: 'GRAM', availability: 'MISSING' }),
    matched('Sal', { amount: 3, unit: 'GRAM', staple: true, availability: 'ASSUMED' }),
  ],
  daysSinceCooked: null,
}

const PASTA_DETAIL: RecipeDetail = {
  recipe: PASTA,
  ingredients: PASTA_RECOMMENDATION.ingredients.map(({ foodId, name, amount, unit, staple }) => ({
    foodId,
    name,
    amount,
    unit,
    staple,
  })),
  steps: ['Cuece la pasta en agua con sal.', 'Saltea el calabacín.'],
}

function page(items: RecipeSummary[], totalPages = items.length ? 1 : 0) {
  return { body: { items, page: 0, size: 20, totalItems: items.length, totalPages } }
}

function server(routes: Parameters<typeof fakeApi>[0] = {}) {
  signedIn()
  return fakeApi({
    'GET /users/me': () => ({ body: ANA }),
    'GET /households/h1': () => ({ body: CASA }),
    [CATALOG]: () => page([PASTA, TORTILLA]),
    'GET /households/h1/diet': () => ({ body: { type: 'NONE', avoided: [] } }),
    [RECOMMENDATIONS]: () => ({ body: [PASTA_RECOMMENDATION] }),
    ...routes,
  })
}

afterEach(() => vi.unstubAllGlobals())

describe('recipes', () => {
  it('recommends what to cook and says why, from what is at home', async () => {
    const api = server()
    renderApp('/households/h1/recipes')

    const recommended = within(await screen.findByRole('list', { name: 'Qué cocinar con lo que tienes' }))
    const card = within(await recommended.findByRole('link', { name: /Pasta con calabacín y tomate/ }))
    expect(card.getByText('Encaje 76 %')).toBeInTheDocument()
    expect(card.getByText('Tienes calabacín con caducidad en 2 días.')).toBeInTheDocument()
    expect(card.getByText('Tienes 2 de 3 ingredientes.')).toBeInTheDocument()
    expect(card.getByText('Solo te falta: mozzarella.')).toBeInTheDocument()
    expect(card.getByText('Tiempo aproximado: 25 min.')).toBeInTheDocument()
    expect(api.last(RECOMMENDATIONS)?.query.get('lang')).toBe('es')
    expect(screen.getByRole('link', { name: /Volver al inventario/ })).toHaveAttribute('href', '/households/h1')
  })

  it('says so when nothing at home can be cooked', async () => {
    server({ [RECOMMENDATIONS]: () => ({ body: [] }) })
    renderApp('/households/h1/recipes')

    expect(await screen.findByText(/Ninguna receta usa lo que hay en tu inventario/)).toBeInTheDocument()
    // The catalog is still there to browse.
    expect(await screen.findByRole('link', { name: /Tortilla de patatas/ })).toBeInTheDocument()
  })

  it('lists the catalog with time, difficulty and servings', async () => {
    server()
    renderApp('/households/h1/recipes')

    const catalog = within(await screen.findByRole('list', { name: 'Todas las recetas' }))
    const tortilla = within(await catalog.findByRole('link', { name: /Tortilla de patatas/ }))
    expect(tortilla.getByText('40 min · Media · 4 raciones')).toBeInTheDocument()
    expect(tortilla.getByText('Descripción de Tortilla de patatas')).toBeInTheDocument()
    expect(catalog.getByRole('link', { name: /Tortilla de patatas/ })).toHaveAttribute(
      'href',
      '/households/h1/recipes/r2',
    )
  })

  it('filters the catalog by text, time and course', async () => {
    const api = server()
    const user = userEvent.setup()
    renderApp('/households/h1/recipes')
    await screen.findByRole('list', { name: 'Todas las recetas' })

    await user.type(screen.getByRole('searchbox', { name: 'Buscar receta' }), 'tort')
    await waitFor(() => expect(api.last(CATALOG)?.query.get('q')).toBe('tort'))

    await user.selectOptions(screen.getByRole('combobox', { name: 'Tiempo' }), 'Hasta 30 minutos')
    await waitFor(() => expect(api.last(CATALOG)?.query.get('maxMinutes')).toBe('30'))

    await user.selectOptions(screen.getByRole('combobox', { name: 'Tipo' }), 'Postre')
    await waitFor(() => expect(api.last(CATALOG)?.query.get('course')).toBe('DESSERT'))
    expect(api.last(CATALOG)?.query.get('page')).toBe('0')
    expect(api.last(CATALOG)?.query.get('q')).toBe('tort')
  })

  it('says so when no recipe matches the filter', async () => {
    server({ [CATALOG]: () => page([]) })
    renderApp('/households/h1/recipes')

    expect(await screen.findByText('Ninguna receta coincide con el filtro.')).toBeInTheDocument()
  })

  it('is reachable from the inventory', async () => {
    server({
      'GET /households/h1/inventory': () => ({ body: { items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 } }),
      'GET /households/h1/inventory/consume-first': () => ({
        body: { counts: { EXPIRED: 0, TODAY: 0, URGENT: 0, SOON: 0, UPCOMING: 0, OK: 0, NO_DATE: 0 }, items: [] },
      }),
    })
    const user = userEvent.setup()
    renderApp('/households/h1')

    await user.click(await screen.findByRole('link', { name: 'Recetas' }))

    expect(await screen.findByRole('heading', { name: 'Qué cocinar con lo que tienes' })).toBeInTheDocument()
  })

  it('shows a translated message for a household the user cannot access', async () => {
    server({ [RECOMMENDATIONS]: () => problem(404, 'HOUSEHOLD_NOT_FOUND') })
    renderApp('/households/h1/recipes')

    expect(await screen.findByRole('alert')).toHaveTextContent('Este hogar no existe o ya no perteneces a él.')
  })
})

describe('recipe detail', () => {
  it('shows ingredients with what the household has of each, and the steps', async () => {
    server({ 'GET /recipes/r1': () => ({ body: PASTA_DETAIL }) })
    renderApp('/households/h1/recipes/r1')

    expect(await screen.findByRole('heading', { name: 'Pasta con calabacín y tomate' })).toBeInTheDocument()
    expect(screen.getByText('25 min · Fácil · 2 raciones')).toBeInTheDocument()

    const ingredients = within(screen.getByRole('list', { name: 'Ingredientes' }))
    const row = (name: string) => within(ingredients.getByText(name).closest('li')!)
    expect(row('Pasta').getByText('200 g')).toBeInTheDocument()
    expect(await row('Pasta').findByText('Lo tienes')).toBeInTheDocument()
    expect(row('Calabacín').getByText('Caduca en 2 días')).toBeInTheDocument()
    expect(row('Mozzarella').getByText('Te falta')).toBeInTheDocument()
    // A staple is listed with its amount, and nothing is said about having it or not.
    expect(row('Sal').getByText(/3 g · básico de cocina/)).toBeInTheDocument()
    expect(row('Sal').queryByText('Lo tienes')).not.toBeInTheDocument()

    expect(screen.getByText('Cuece la pasta en agua con sal.')).toBeInTheDocument()
    expect(screen.getByText('Saltea el calabacín.')).toBeInTheDocument()
  })

  it('labels an estimated date as an estimate', async () => {
    server({
      'GET /recipes/r1': () => ({ body: PASTA_DETAIL }),
      [RECOMMENDATIONS]: () => ({
        body: [
          {
            ...PASTA_RECOMMENDATION,
            ingredients: [
              matched('Pasta'),
              matched('Calabacín', { expirationDate: '2026-10-02', daysUntilExpiration: 0, estimated: true }),
              matched('Mozzarella', { expirationDate: '2026-10-05', daysUntilExpiration: 3, estimated: true }),
            ],
          },
        ],
      }),
    })
    renderApp('/households/h1/recipes/r1')

    expect(await screen.findByText('Caduca hoy (estimada)')).toBeInTheDocument()
    expect(screen.getByText('Caduca en unos 3 días (estimada)')).toBeInTheDocument()
  })

  it('says when nothing of the recipe is at home', async () => {
    server({ 'GET /recipes/r1': () => ({ body: PASTA_DETAIL }), [RECOMMENDATIONS]: () => ({ body: [] }) })
    renderApp('/households/h1/recipes/r1')

    expect(await screen.findByText(/No tienes en el inventario ninguno de los ingredientes/)).toBeInTheDocument()
    expect(screen.queryByText('Lo tienes')).not.toBeInTheDocument()
  })

  it('records that the household cooked it, without touching the inventory', async () => {
    const api = server({
      'GET /recipes/r1': () => ({ body: PASTA_DETAIL }),
      'POST /households/h1/recipes/r1/cooked': () => ({ status: 204 }),
    })
    const user = userEvent.setup()
    renderApp('/households/h1/recipes/r1')

    await user.click(await screen.findByRole('button', { name: 'La he cocinado' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Anotado')
    expect(api.count('POST /households/h1/recipes/r1/cooked')).toBe(1)
    // The recommendations are asked for again: this recipe now ranks lower.
    await waitFor(() => expect(api.count(RECOMMENDATIONS)).toBeGreaterThanOrEqual(2))
    expect(api.calls.some((call) => call.route.includes('/inventory'))).toBe(false)
    expect(screen.getByText(/No cambia tu inventario/)).toBeInTheDocument()
  })

  it('shows a translated message for a recipe that does not exist', async () => {
    server({ 'GET /recipes/nope': () => problem(404, 'RECIPE_NOT_FOUND') })
    renderApp('/households/h1/recipes/nope')

    expect(await screen.findByRole('alert')).toHaveTextContent('Esta receta no existe.')
    expect(screen.getByRole('link', { name: /Volver a las recetas/ })).toHaveAttribute('href', '/households/h1/recipes')
  })
})
