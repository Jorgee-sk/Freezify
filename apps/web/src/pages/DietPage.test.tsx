import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Diet, RecipeDetail } from '../api/recipes'
import { conflicts, dietSummary, traitList } from '../recipes/diet'
import { fakeApi, problem } from '../test/fakeApi'
import { ANA, CASA, renderApp, signedIn } from '../test/renderApp'

const DIET = 'GET /households/h1/diet'
const SAVE = 'PUT /households/h1/diet'
const CATALOG = 'GET /recipes'
const NONE: Diet = { type: 'NONE', avoided: [] }

const PASTA_DETAIL: RecipeDetail = {
  recipe: {
    id: 'r1',
    name: 'Pasta con calabacín y tomate',
    description: 'Un plato rápido.',
    servings: 2,
    prepMinutes: 10,
    cookMinutes: 15,
    totalMinutes: 25,
    difficulty: 'EASY',
    course: 'MAIN',
    contains: ['DAIRY', 'EGG', 'GLUTEN'],
  },
  ingredients: [{ foodId: 'f1', name: 'Pasta', amount: 200, unit: 'GRAM', staple: false }],
  steps: ['Cuece la pasta.'],
}

function server(routes: Parameters<typeof fakeApi>[0] = {}) {
  signedIn()
  return fakeApi({
    'GET /users/me': () => ({ body: ANA }),
    'GET /households/h1': () => ({ body: CASA }),
    [DIET]: () => ({ body: NONE }),
    [CATALOG]: () => ({ body: { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 } }),
    'GET /households/h1/recipes/recommendations': () => ({ body: [] }),
    'GET /recipes/r1': () => ({ body: PASTA_DETAIL }),
    ...routes,
  })
}

afterEach(() => vi.unstubAllGlobals())

describe('dietary restrictions', () => {
  it('says which restrictions the recipes were filtered with, and asks the server to apply them', async () => {
    const api = server({ [DIET]: () => ({ body: { type: 'VEGETARIAN', avoided: ['GLUTEN', 'SOY'] } }) })
    renderApp('/households/h1/recipes')

    expect(
      await screen.findByText('Recetas filtradas para este hogar: Dieta vegetariana · sin gluten, sin soja.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Cambiar' })).toHaveAttribute('href', '/households/h1/diet')
    // The filtering is the server's: the catalog is asked for on behalf of the household.
    await waitFor(() => expect(api.last(CATALOG)?.query.get('household')).toBe('h1'))
  })

  it('says so when the household has no restrictions', async () => {
    server()
    renderApp('/households/h1/recipes')

    expect(await screen.findByText('Este hogar no tiene restricciones alimentarias.')).toBeInTheDocument()
  })

  it('saves what the household does not eat', async () => {
    const api = server({ [SAVE]: (body) => ({ body }) })
    const user = userEvent.setup()
    renderApp('/households/h1/diet')

    expect(await screen.findByRole('radio', { name: 'Sin dieta' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Gluten' })).not.toBeChecked()

    await user.click(screen.getByRole('radio', { name: 'Vegana' }))
    await user.click(screen.getByRole('checkbox', { name: 'Gluten' }))
    await user.click(screen.getByRole('checkbox', { name: 'Frutos secos' }))
    await user.click(screen.getByRole('button', { name: 'Guardar' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Restricciones guardadas')
    expect(api.last(SAVE)?.body).toEqual({ type: 'VEGAN', avoided: ['GLUTEN', 'NUTS'] })

    // A further change is not saved until the user says so.
    await user.click(screen.getByRole('checkbox', { name: 'Gluten' }))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(api.count(SAVE)).toBe(1)
  })

  it('shows what was chosen before', async () => {
    server({ [DIET]: () => ({ body: { type: 'VEGETARIAN', avoided: ['EGG'] } }) })
    renderApp('/households/h1/diet')

    expect(await screen.findByRole('radio', { name: 'Vegetariana' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Huevo' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Gluten' })).not.toBeChecked()
  })

  it('warns that the filter is a help and not a guarantee, and that it is shared', async () => {
    server()
    renderApp('/households/h1/diet')

    expect(await screen.findByRole('note')).toHaveTextContent(/comprueba siempre la etiqueta/)
    expect(screen.getByText(/cualquier miembro las ve y puede cambiarlas/)).toBeInTheDocument()
  })

  it('shows a translated message for a household the user cannot access', async () => {
    server({ [DIET]: () => problem(404, 'HOUSEHOLD_NOT_FOUND') })
    renderApp('/households/h1/diet')

    expect(await screen.findByRole('alert')).toHaveTextContent('Este hogar no existe o ya no perteneces a él.')
  })

  it('says what a recipe contains', async () => {
    server()
    renderApp('/households/h1/recipes/r1')

    expect(await screen.findByText('Contiene: lácteos, huevo, gluten.')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('warns when a recipe opened directly contains what the household does not eat', async () => {
    server({ [DIET]: () => ({ body: { type: 'VEGAN', avoided: ['GLUTEN'] } }) })
    renderApp('/households/h1/recipes/r1')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Esta receta contiene algo que en este hogar no se come: lácteos, huevo, gluten.',
    )
  })
})

describe('diet helpers', () => {
  it('summarises the restrictions of a household', () => {
    expect(dietSummary(NONE)).toBeNull()
    expect(dietSummary({ type: 'VEGAN', avoided: [] })).toBe('Dieta vegana')
    expect(dietSummary({ type: 'NONE', avoided: ['NUTS'] })).toBe('sin frutos secos')
    expect(dietSummary({ type: 'VEGETARIAN', avoided: ['GLUTEN', 'SESAME'] })).toBe(
      'Dieta vegetariana · sin gluten, sin sésamo',
    )
  })

  it('finds what a recipe contains that the household does not eat', () => {
    expect(conflicts(['MEAT', 'DAIRY'], NONE)).toEqual([])
    expect(conflicts(['MEAT', 'PORK', 'DAIRY'], { type: 'VEGETARIAN', avoided: [] })).toEqual(['MEAT', 'PORK'])
    expect(conflicts(['DAIRY', 'EGG', 'GLUTEN'], { type: 'VEGAN', avoided: [] })).toEqual(['DAIRY', 'EGG'])
    expect(conflicts(['DAIRY', 'GLUTEN'], { type: 'NONE', avoided: ['GLUTEN'] })).toEqual(['GLUTEN'])
    expect(conflicts([], { type: 'VEGAN', avoided: ['GLUTEN'] })).toEqual([])
  })

  it('names what a recipe contains', () => {
    expect(traitList(['SHELLFISH', 'NUTS'])).toBe('marisco, frutos secos')
  })
})
