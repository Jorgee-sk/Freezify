import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AvailableFood, GeneratedIngredient, GeneratedRecipe } from '../api/recipes'
import { fakeApi, problem } from '../test/fakeApi'
import { ANA, CASA, renderApp, signedIn } from '../test/renderApp'

const GENERATE = 'POST /households/h1/recipes/generated'
const AVAILABLE = 'GET /households/h1/recipes/generated/ingredients'

const AT_HOME: AvailableFood[] = [
  { foodId: 'f-zucchini', name: 'Calabacín', amount: 2, unit: 'UNIT', daysLeft: 1, estimated: false },
  { foodId: 'f-chicken', name: 'Pechuga de pollo', amount: 400, unit: 'GRAM', daysLeft: 3, estimated: true },
  { foodId: 'f-rice', name: 'Arroz', amount: 1000, unit: 'GRAM', daysLeft: 200, estimated: false },
  { foodId: 'f-eggs', name: 'Huevos', amount: 6, unit: 'UNIT', daysLeft: 10, estimated: false },
  { foodId: null, name: 'Salsa de la abuela', amount: 1, unit: 'UNIT', daysLeft: 4, estimated: false },
]

function ingredient(name: string, overrides: Partial<GeneratedIngredient> = {}): GeneratedIngredient {
  return {
    foodId: `food-${name}`,
    name,
    amount: 1,
    unit: 'UNIT',
    optional: false,
    staple: false,
    daysLeft: null,
    priority: null,
    estimated: false,
    ...overrides,
  }
}

const RECIPE: GeneratedRecipe = {
  title: 'Pollo salteado con calabacín',
  summary: 'Un salteado rápido.',
  servings: 3,
  minutes: 20,
  difficulty: 'EASY',
  ingredients: [
    ingredient('Pechuga de pollo', { amount: 400, unit: 'GRAM', daysLeft: 3, priority: 'SOON', estimated: true }),
    ingredient('Calabacín', { amount: 2, daysLeft: 1, priority: 'URGENT' }),
    ingredient('Arroz', { amount: 150, unit: 'GRAM', optional: true, daysLeft: 200, priority: 'OK' }),
    ingredient('Sal', { amount: 2, unit: 'GRAM', staple: true }),
  ],
  steps: ['Corta el pollo y el calabacín.', 'Saltéalos con aceite y sal.'],
}

function server(routes: Parameters<typeof fakeApi>[0] = {}) {
  signedIn()
  return fakeApi({
    'GET /users/me': () => ({ body: ANA }),
    'GET /households/h1': () => ({ body: CASA }),
    'GET /households/h1/diet': () => ({ body: { type: 'NONE', avoided: [] } }),
    'GET /households/h1/recipes/recommendations': () => ({ body: [] }),
    'GET /recipes': () => ({ body: { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 } }),
    'GET /ai': () => ({ body: { enabled: true } }),
    [GENERATE]: () => ({ body: RECIPE }),
    [AVAILABLE]: () => ({ body: AT_HOME }),
    ...routes,
  })
}

afterEach(() => vi.unstubAllGlobals())

describe('recipe written by AI', () => {
  it('is offered from the recipes only where the server has a model', async () => {
    server()
    renderApp('/households/h1/recipes')

    const link = await screen.findByRole('link', { name: /Crear una receta con lo que tengo/ })
    expect(link).toHaveAttribute('href', '/households/h1/recipes/generate')
  })

  it('is not offered without a model', async () => {
    server({ 'GET /ai': () => ({ body: { enabled: false } }) })
    renderApp('/households/h1/recipes')

    await screen.findByText('Qué cocinar con lo que tienes')
    expect(await screen.findByText(/Ninguna receta usa lo que hay/)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Crear una receta con lo que tengo/ })).not.toBeInTheDocument()
  })

  it('writes a recipe with what is at home and says why it uses each food', async () => {
    const api = server()
    renderApp('/households/h1/recipes/generate')

    await userEvent.selectOptions(await screen.findByLabelText('Raciones'), '3')
    await userEvent.click(screen.getByRole('button', { name: 'Crear receta' }))

    const recipe = within(await screen.findByRole('article', { name: 'Pollo salteado con calabacín' }))
    expect(api.last(GENERATE)?.body).toEqual({ servings: 3, use: [] })
    expect(api.last(GENERATE)?.query.get('lang')).toBe('es')
    expect(recipe.getByText('Un salteado rápido.')).toBeInTheDocument()
    expect(recipe.getByText('20 min · Fácil · 3 raciones')).toBeInTheDocument()
    expect(recipe.getByRole('note')).toHaveTextContent('Receta escrita por IA')

    const ingredients = within(recipe.getByRole('list', { name: 'Ingredientes' }))
    const items = ingredients.getAllByRole('listitem')
    expect(items[0]).toHaveTextContent('Pechuga de pollo')
    expect(items[0]).toHaveTextContent('Caduca en unos 3 días (estimada)')
    expect(items[1]).toHaveTextContent('Caduca en 1 día')
    expect(items[2]).toHaveTextContent('opcional')
    expect(items[2]).not.toHaveTextContent('Caduca')
    expect(items[3]).toHaveTextContent('básico de cocina')
    expect(recipe.getAllByRole('listitem').map((item) => item.textContent)).toContain('Corta el pollo y el calabacín.')

    await userEvent.click(screen.getByRole('button', { name: 'Crear otra' }))
    expect(api.count(GENERATE)).toBe(2)
  })

  it('shows what the AI may use and lets people pick up to three foods it must use', async () => {
    const api = server()
    renderApp('/households/h1/recipes/generate')

    const atHome = within(await screen.findByRole('list', { name: 'Lo que la IA puede usar' }))
    const zucchini = atHome.getByRole('button', { name: /Calabacín · 2 uds · Caduca en 1 día/ })
    expect(
      atHome.getByRole('button', { name: 'Pechuga de pollo · 400 g · Caduca en unos 3 días (estimada)' }),
    ).toBeInTheDocument()
    // Food that is not a catalog food is given to the AI but cannot be required.
    expect(atHome.getByRole('button', { name: /Salsa de la abuela/ })).toBeDisabled()

    await userEvent.click(zucchini)
    await userEvent.click(atHome.getByRole('button', { name: /Pechuga de pollo/ }))
    await userEvent.click(atHome.getByRole('button', { name: /Arroz/ }))
    expect(zucchini).toHaveAttribute('aria-pressed', 'true')
    // Three at most.
    expect(atHome.getByRole('button', { name: /Huevos/ })).toBeDisabled()
    await userEvent.click(atHome.getByRole('button', { name: /Arroz/ }))
    expect(atHome.getByRole('button', { name: /Huevos/ })).toBeEnabled()

    await userEvent.click(screen.getByRole('button', { name: 'Crear receta' }))
    await screen.findByRole('article')
    expect(api.last(GENERATE)?.body).toEqual({ servings: 2, use: ['f-zucchini', 'f-chicken'] })
  })

  it('says why there is no recipe', async () => {
    server({ [GENERATE]: () => problem(429, 'AI_LIMIT_REACHED') })
    renderApp('/households/h1/recipes/generate')

    await userEvent.click(await screen.findByRole('button', { name: 'Crear receta' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Has usado todas las peticiones de IA de hoy')
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
  })
})
