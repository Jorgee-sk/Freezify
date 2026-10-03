import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { MealPlan, PlannedMeal } from '../api/mealPlan'
import type { RecipeSummary } from '../api/recipes'
import { eventStream, fakeApi, problem } from '../test/fakeApi'
import { ANA, CASA, renderApp, signedIn } from '../test/renderApp'

const PLAN = 'GET /households/h1/meal-plan'
const GENERATE = 'POST /households/h1/meal-plan/generate'
const CATALOG = 'GET /recipes'
const MONDAY_LUNCH = '/households/h1/meal-plan/2026-10-05/LUNCH'

function recipe(id: string, name: string): RecipeSummary {
  return {
    id,
    name,
    description: '',
    servings: 2,
    prepMinutes: 10,
    cookMinutes: 15,
    totalMinutes: 25,
    difficulty: 'EASY',
    course: 'MAIN',
    contains: [],
  }
}

const PASTA: PlannedMeal = {
  id: 'm1',
  date: '2026-10-05',
  slot: 'LUNCH',
  origin: 'MANUAL',
  recipe: { id: 'r1', name: 'Pasta con calabacín y tomate', servings: 2, totalMinutes: 25, difficulty: 'EASY', contains: [] },
  ingredients: [
    {
      foodId: 'f1',
      name: 'Calabacín',
      amount: 1,
      unit: 'UNIT',
      staple: false,
      availability: 'ENOUGH',
      expirationDate: '2026-10-07',
      estimated: true,
    },
    {
      foodId: 'f2',
      name: 'Mozzarella',
      amount: 100,
      unit: 'GRAM',
      staple: false,
      availability: 'MISSING',
      expirationDate: null,
      estimated: false,
    },
  ],
  cooked: false,
}
const CHICKEN: PlannedMeal = {
  id: 'm2',
  date: '2026-10-06',
  slot: 'DINNER',
  origin: 'GENERATED',
  recipe: { id: 'r2', name: 'Pollo a la plancha con brócoli', servings: 2, totalMinutes: 25, difficulty: 'EASY', contains: ['MEAT'] },
  ingredients: [],
  cooked: false,
}

function week(changes: Partial<MealPlan> = {}): MealPlan {
  return {
    weekStart: '2026-10-05',
    weekEnd: '2026-10-11',
    today: '2026-10-05',
    meals: [],
    unusedExpiring: [],
    ...changes,
  }
}

function server(routes: Parameters<typeof fakeApi>[0] = {}) {
  signedIn()
  return fakeApi({
    'GET /users/me': () => ({ body: ANA }),
    'GET /households/h1': () => ({ body: CASA }),
    [PLAN]: () => ({ body: week() }),
    'GET /households/h1/diet': () => ({ body: { type: 'NONE', avoided: [] } }),
    [CATALOG]: () => ({
      body: {
        items: [recipe('r1', 'Pasta con calabacín y tomate'), recipe('r3', 'Tortilla de patatas')],
        page: 0,
        size: 20,
        totalItems: 2,
        totalPages: 1,
      },
    }),
    ...routes,
  })
}

/** Opens the plan and waits for the week to be on screen. */
async function openPlan() {
  renderApp('/households/h1/plan')
  await screen.findByText('5 oct – 11 oct')
}

function day(name: string) {
  return within(screen.getByRole('region', { name }))
}

function meal(dayName: string, slot: string) {
  return within(day(dayName).getByRole('group', { name: slot }))
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('meal plan', () => {
  it('shows the week from Monday to Sunday with lunch and dinner every day', async () => {
    const api = server()
    await openPlan()

    expect(await screen.findByText('5 oct – 11 oct')).toBeInTheDocument()
    expect(screen.getAllByRole('region').map((region) => region.getAttribute('aria-label'))).toEqual([
      'lunes 5 oct',
      'martes 6 oct',
      'miércoles 7 oct',
      'jueves 8 oct',
      'viernes 9 oct',
      'sábado 10 oct',
      'domingo 11 oct',
    ])
    expect(meal('lunes 5 oct', 'Comida').getByText('Nada planificado')).toBeInTheDocument()
    expect(meal('lunes 5 oct', 'Cena').getByRole('button', { name: 'Elegir receta' })).toBeInTheDocument()
    expect(day('lunes 5 oct').getByText('Hoy')).toBeInTheDocument()
    // Without a week asked for, the server decides which one is today's.
    expect(api.last(PLAN)?.query.get('week')).toBeNull()
    expect(api.last(PLAN)?.query.get('lang')).toBe('es')
  })

  it('says what each meal saves from expiring and what is missing, estimates worded as estimates', async () => {
    server({ [PLAN]: () => ({ body: week({ meals: [PASTA, CHICKEN] }) }) })
    await openPlan()

    const lunch = meal('lunes 5 oct', 'Comida')
    expect(await lunch.findByRole('link', { name: 'Pasta con calabacín y tomate' })).toHaveAttribute(
      'href',
      '/households/h1/recipes/r1',
    )
    expect(lunch.getByText('Aprovecha calabacín, con caducidad estimada el 7 oct.')).toBeInTheDocument()
    expect(lunch.getByText('Falta por comprar: mozzarella.')).toBeInTheDocument()
    // Only what the generator chose is marked as a suggestion.
    expect(lunch.queryByText('Propuesta')).not.toBeInTheDocument()
    expect(meal('martes 6 oct', 'Cena').getByText('Propuesta')).toBeInTheDocument()
  })

  it('points out the food the plan lets expire', async () => {
    server({
      [PLAN]: () => ({
        body: week({
          unusedExpiring: [
            { name: 'Yogur', amount: 4, unit: 'UNIT', expirationDate: '2026-10-06', estimated: false },
            { name: 'Pechuga de pollo', amount: 300, unit: 'GRAM', expirationDate: '2026-10-07', estimated: true },
          ],
        }),
      }),
    })
    await openPlan()

    const unused = within(await screen.findByRole('list', { name: 'El plan deja caducar' }))
    expect(unused.getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'Yogur (4 uds), con caducidad el 6 oct',
      'Pechuga de pollo (300 g), con caducidad estimada el 7 oct',
    ])
  })

  it('chooses a recipe for an empty meal', async () => {
    let meals: PlannedMeal[] = []
    const api = server({
      [PLAN]: () => ({ body: week({ meals }) }),
      [`PUT ${MONDAY_LUNCH}`]: () => {
        meals = [PASTA]
        return { status: 204 }
      },
    })
    const user = userEvent.setup()
    await openPlan()

    await user.click(await meal('lunes 5 oct', 'Comida').findByRole('button', { name: 'Elegir receta' }))
    const picker = within(screen.getByRole('dialog', { name: 'Elige una receta' }))
    await user.click(await picker.findByRole('button', { name: 'Pasta con calabacín y tomate' }))

    expect(await meal('lunes 5 oct', 'Comida').findByRole('link', { name: 'Pasta con calabacín y tomate' })).toBeVisible()
    expect(api.last(`PUT ${MONDAY_LUNCH}`)?.body).toEqual({ recipeId: 'r1' })
    // Only recipes the household eats are offered.
    expect(api.last(CATALOG)?.query.get('household')).toBe('h1')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('offers every recipe the household eats, not only a first page', async () => {
    const api = server()
    const user = userEvent.setup()
    await openPlan()

    await user.click(meal('lunes 5 oct', 'Comida').getByRole('button', { name: 'Elegir receta' }))

    await waitFor(() => expect(api.last(CATALOG)?.query.get('size')).toBe('100'))
  })

  it('warns when a meal contains what the household does not eat', async () => {
    server({
      [PLAN]: () => ({ body: week({ meals: [PASTA, CHICKEN] }) }),
      'GET /households/h1/diet': () => ({ body: { type: 'VEGETARIAN', avoided: [] } }),
    })
    await openPlan()

    expect(
      await meal('martes 6 oct', 'Cena').findByText('Esta receta contiene algo que en este hogar no se come: carne.'),
    ).toBeInTheDocument()
    expect(meal('lunes 5 oct', 'Comida').queryByRole('alert')).not.toBeInTheDocument()
  })

  it('says the household cooked a meal of today, and lets it say so', async () => {
    let cooked = false
    const api = server({
      [PLAN]: () => ({ body: week({ meals: [{ ...PASTA, cooked }, CHICKEN] }) }),
      'POST /households/h1/recipes/r1/cooked': () => {
        cooked = true
        return { status: 204 }
      },
    })
    const user = userEvent.setup()
    await openPlan()

    // Only today's meals can be said to be cooked: the server records it as cooked today.
    expect(meal('martes 6 oct', 'Cena').queryByRole('button', { name: 'La he cocinado' })).not.toBeInTheDocument()
    await user.click(meal('lunes 5 oct', 'Comida').getByRole('button', { name: 'La he cocinado' }))

    expect(await meal('lunes 5 oct', 'Comida').findByText('Cocinada')).toBeInTheDocument()
    expect(meal('lunes 5 oct', 'Comida').queryByRole('button', { name: 'La he cocinado' })).not.toBeInTheDocument()
    expect(api.count('POST /households/h1/recipes/r1/cooked')).toBe(1)
  })

  it('opens the week a link asks for', async () => {
    const api = server({
      [PLAN]: (_, { query }) =>
        query.get('week') === '2026-10-14'
          ? { body: week({ weekStart: '2026-10-12', weekEnd: '2026-10-18' }) }
          : { body: week() },
    })
    renderApp('/households/h1/plan?week=2026-10-14')

    expect(await screen.findByText('12 oct – 18 oct')).toBeInTheDocument()
    expect(api.last(PLAN)?.query.get('week')).toBe('2026-10-14')
  })

  it('searches among the recipes to choose from', async () => {
    const api = server()
    const user = userEvent.setup()
    await openPlan()

    await user.click(await meal('lunes 5 oct', 'Cena').findByRole('button', { name: 'Elegir receta' }))
    await user.type(screen.getByRole('searchbox', { name: 'Buscar receta' }), 'tortilla')

    await waitFor(() => expect(api.last(CATALOG)?.query.get('q')).toBe('tortilla'))
    await user.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('removes a meal', async () => {
    let meals = [PASTA]
    const api = server({
      [PLAN]: () => ({ body: week({ meals }) }),
      [`DELETE ${MONDAY_LUNCH}`]: () => {
        meals = []
        return { status: 204 }
      },
    })
    const user = userEvent.setup()
    await openPlan()

    await user.click(await meal('lunes 5 oct', 'Comida').findByRole('button', { name: 'Quitar' }))

    expect(await meal('lunes 5 oct', 'Comida').findByText('Nada planificado')).toBeInTheDocument()
    expect(api.count(`DELETE ${MONDAY_LUNCH}`)).toBe(1)
  })

  it('moves a meal, saying which one it would swap with', async () => {
    const api = server({
      [PLAN]: () => ({ body: week({ meals: [PASTA, CHICKEN] }) }),
      [`POST ${MONDAY_LUNCH}/move`]: () => ({ status: 204 }),
    })
    const user = userEvent.setup()
    await openPlan()

    await user.click(await meal('lunes 5 oct', 'Comida').findByRole('button', { name: 'Mover' }))
    const target = screen.getByRole('combobox', { name: 'Mover a' })
    expect(within(target).queryByRole('option', { name: 'lunes 5 oct · Comida' })).not.toBeInTheDocument()
    expect(within(target).getByRole('option', { name: 'lunes 5 oct · Cena' })).toBeInTheDocument()
    await user.selectOptions(
      target,
      within(target).getByRole('option', {
        name: 'martes 6 oct · Cena (se intercambia con Pollo a la plancha con brócoli)',
      }),
    )

    await waitFor(() =>
      expect(api.last(`POST ${MONDAY_LUNCH}/move`)?.body).toEqual({ date: '2026-10-06', slot: 'DINNER' }),
    )
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('fills the empty meals and says how many it planned and how many stay empty', async () => {
    const api = server({ [GENERATE]: () => ({ body: { filled: 4, unfilled: 10 } }) })
    const user = userEvent.setup()
    await openPlan()

    await user.click(await screen.findByRole('button', { name: 'Rellenar los huecos' }))

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Se han planificado 4 comidas. 10 comidas se quedan vacías: no hay más recetas que encajen sin repetir demasiado.',
    )
    expect(api.last(GENERATE)?.body).toEqual({ week: '2026-10-05', replaceGenerated: false })
    // The plan is asked for again: it has changed.
    expect(api.count(PLAN)).toBeGreaterThan(1)
    // Nothing was suggested before, so there is nothing to suggest again.
    expect(screen.queryByRole('button', { name: 'Rehacer la propuesta' })).not.toBeInTheDocument()
  })

  it('sends what the week lacks to the shopping list', async () => {
    const api = server({ 'POST /households/h1/shopping-list/from-plan': () => ({ body: { lines: 5 } }) })
    const user = userEvent.setup()
    await openPlan()

    await user.click(screen.getByRole('button', { name: 'Llevar a la lista lo que falta' }))

    const status = await screen.findByText(/Hay 5 cosas en la lista/)
    expect(within(status).getByRole('link', { name: 'Ver la lista' })).toHaveAttribute('href', '/households/h1/shopping')
    expect(api.last('POST /households/h1/shopping-list/from-plan')?.body).toEqual({ week: '2026-10-05' })
  })

  it('asks before replacing what was suggested', async () => {
    const api = server({
      [PLAN]: () => ({ body: week({ meals: [PASTA, CHICKEN] }) }),
      [GENERATE]: () => ({ body: { filled: 1, unfilled: 0 } }),
    })
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const user = userEvent.setup()
    await openPlan()

    await user.click(await screen.findByRole('button', { name: 'Rehacer la propuesta' }))
    expect(confirm).toHaveBeenCalledOnce()
    expect(api.count(GENERATE)).toBe(0)

    confirm.mockReturnValue(true)
    await user.click(screen.getByRole('button', { name: 'Rehacer la propuesta' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Se ha planificado 1 comida.')
    expect(api.last(GENERATE)?.body).toEqual({ week: '2026-10-05', replaceGenerated: true })
  })

  it('moves between weeks and does not offer to plan one that is over', async () => {
    const api = server({
      [PLAN]: (_, { query }) =>
        query.get('week') === '2026-09-28'
          ? { body: week({ weekStart: '2026-09-28', weekEnd: '2026-10-04' }) }
          : { body: week() },
    })
    const user = userEvent.setup()
    await openPlan()

    await user.click(await screen.findByRole('button', { name: /Semana anterior/ }))

    expect(await screen.findByText('28 sept – 4 oct')).toBeInTheDocument()
    expect(screen.getByText('Esta semana ya ha pasado.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Rellenar los huecos' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Semana siguiente/ }))
    await waitFor(() => expect(api.last(PLAN)?.query.get('week')).toBe('2026-10-05'))
    await user.click(await screen.findByRole('button', { name: 'Esta semana' }))
    await waitFor(() => expect(api.last(PLAN)?.query.get('week')).toBeNull())
  })

  it('asks for the plan again when another member changes it or the inventory', async () => {
    const stream = eventStream()
    let meals: PlannedMeal[] = []
    const api = server({
      [PLAN]: () => ({ body: week({ meals }) }),
      'GET /households/h1/events': () => ({ response: stream.response }),
    })
    await openPlan()
    await meal('lunes 5 oct', 'Comida').findByText('Nada planificado')
    await waitFor(() => expect(api.count('GET /households/h1/events')).toBe(1))

    meals = [PASTA]
    stream.send('meal-plan-changed')
    expect(await meal('lunes 5 oct', 'Comida').findByRole('link', { name: 'Pasta con calabacín y tomate' })).toBeVisible()

    const before = api.count(PLAN)
    stream.send('inventory-changed')
    await waitFor(() => expect(api.count(PLAN)).toBeGreaterThan(before))
  })

  it('explains why something could not be done', async () => {
    server({
      [PLAN]: () => ({ body: week({ meals: [PASTA] }) }),
      [`DELETE ${MONDAY_LUNCH}`]: () => problem(404, 'MEAL_NOT_FOUND'),
      [GENERATE]: () => problem(409, 'WEEK_IN_THE_PAST'),
    })
    const user = userEvent.setup()
    await openPlan()

    await user.click(await meal('lunes 5 oct', 'Comida').findByRole('button', { name: 'Quitar' }))
    expect(await screen.findByText('Esa comida ya no está en el plan.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Rellenar los huecos' }))
    expect(await screen.findByText('No se puede planificar una semana que ya ha pasado.')).toBeInTheDocument()
  })

  it('is reached from the inventory', async () => {
    server({
      'GET /households/h1/inventory': () => ({ body: { items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 } }),
      'GET /households/h1/inventory/consume-first': () => ({
        body: { counts: { EXPIRED: 0, TODAY: 0, URGENT: 0, SOON: 0, UPCOMING: 0, OK: 0, NO_DATE: 0 }, items: [] },
      }),
      'GET /households/h1/inventory/recent': () => ({ body: [] }),
    })
    renderApp('/households/h1')

    expect(await screen.findByRole('link', { name: 'Plan semanal' })).toHaveAttribute('href', '/households/h1/plan')
  })
})
