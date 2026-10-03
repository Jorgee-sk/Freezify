import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ShoppingItem } from '../api/shopping'
import { todayIso } from '../inventory/format'
import { addDays } from '../plan/wording'
import { eventStream, fakeApi, problem } from '../test/fakeApi'
import { ANA, CASA, renderApp, signedIn } from '../test/renderApp'

const LIST = 'GET /households/h1/shopping-list'
const ADD = 'POST /households/h1/shopping-list/items'
const FROM_PLAN = 'POST /households/h1/shopping-list/from-plan'

function line(id: string, name: string, changes: Partial<ShoppingItem> = {}): ShoppingItem {
  return {
    id,
    foodId: `food-${id}`,
    name,
    category: 'OTHER',
    quantity: null,
    origin: 'MANUAL',
    neededOn: null,
    checked: false,
    checkedAt: null,
    ...changes,
  }
}

const ZUCCHINI = line('i1', 'Calabacín', { category: 'VEGETABLES', quantity: { amount: 2, unit: 'UNIT' } })
const CHICKEN = line('i2', 'Pechuga de pollo', {
  category: 'MEAT',
  quantity: { amount: 400, unit: 'GRAM' },
  origin: 'PLAN',
  neededOn: '2026-10-05',
})
const BATTERIES = line('i3', 'Pilas', { foodId: null })

function server(routes: Parameters<typeof fakeApi>[0] = {}) {
  signedIn()
  return fakeApi({
    'GET /users/me': () => ({ body: ANA }),
    'GET /households/h1': () => ({ body: CASA }),
    [LIST]: () => ({ body: { items: [ZUCCHINI, CHICKEN, BATTERIES] } }),
    ...routes,
  })
}

async function openList() {
  renderApp('/households/h1/shopping')
  await screen.findByRole('heading', { name: 'Lista de la compra' })
}

function aisle(name: string) {
  return within(screen.getByRole('list', { name }))
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('shopping list', () => {
  it('shows the list aisle by aisle, with amounts and what each plan line is for', async () => {
    server()
    await openList()

    expect(await screen.findByRole('heading', { name: 'Verduras' })).toBeInTheDocument()
    expect(screen.getAllByRole('heading', { level: 2 }).map((heading) => heading.textContent)).toEqual([
      'Añadir a la lista',
      'Verduras',
      'Carne',
      'Otros',
    ])
    expect(aisle('Verduras').getByRole('checkbox', { name: 'Calabacín · 2 uds' })).not.toBeChecked()
    expect(aisle('Carne').getByRole('checkbox', { name: 'Pechuga de pollo · 400 g' })).toBeInTheDocument()
    expect(aisle('Carne').getByText('Para el plan · lunes 5 oct')).toBeInTheDocument()
    expect(aisle('Otros').getByRole('checkbox', { name: 'Pilas' })).toBeInTheDocument()
  })

  it('says so when the list is empty', async () => {
    server({ [LIST]: () => ({ body: { items: [] } }) })
    await openList()

    expect(await screen.findByText(/La lista está vacía/)).toBeInTheDocument()
  })

  it('ticks off what is bought', async () => {
    let zucchini = ZUCCHINI
    const api = server({
      [LIST]: () => ({ body: { items: [zucchini] } }),
      'PUT /households/h1/shopping-list/items/i1/checked': (body) => {
        zucchini = { ...zucchini, checked: (body as { checked: boolean }).checked }
        return { body: zucchini }
      },
    })
    const user = userEvent.setup()
    await openList()

    await user.click(await screen.findByRole('checkbox', { name: 'Calabacín · 2 uds' }))

    await waitFor(() => expect(screen.getByRole('checkbox', { name: 'Calabacín · 2 uds' })).toBeChecked())
    expect(api.last('PUT /households/h1/shopping-list/items/i1/checked')?.body).toEqual({ checked: true })
  })

  it('adds free text without a quantity', async () => {
    const api = server({ [ADD]: (body) => ({ status: 201, body }) })
    const user = userEvent.setup()
    await openList()

    const form = within(screen.getByRole('form', { name: 'Añadir a la lista' }))
    await user.type(form.getByRole('textbox', { name: 'Qué' }), 'Papel de cocina')
    await user.click(form.getByRole('button', { name: 'Añadir' }))

    await waitFor(() =>
      expect(api.last(ADD)?.body).toEqual({ foodId: null, name: 'Papel de cocina', category: null, quantity: null }),
    )
    expect(form.getByRole('textbox', { name: 'Qué' })).toHaveValue('')
  })

  it('adds a food of the catalog with an amount', async () => {
    const api = server({
      'GET /foods': () => ({
        body: [{ id: 'f9', name: 'Patatas', category: 'VEGETABLES', defaultUnit: 'KILOGRAM', defaultStorage: 'PANTRY' }],
      }),
      [ADD]: (body) => ({ status: 201, body }),
    })
    const user = userEvent.setup()
    await openList()

    const form = within(screen.getByRole('form', { name: 'Añadir a la lista' }))
    await user.type(form.getByRole('textbox', { name: 'Qué' }), 'pat')
    await user.click(await form.findByRole('button', { name: 'Patatas' }))
    await user.type(form.getByRole('textbox', { name: 'Cantidad' }), '1,5')
    await user.click(form.getByRole('button', { name: 'Añadir' }))

    await waitFor(() =>
      expect(api.last(ADD)?.body).toEqual({
        foodId: 'f9',
        name: null,
        category: null,
        quantity: { amount: 1.5, unit: 'KILOGRAM' },
      }),
    )
  })

  it('changes how much to buy', async () => {
    const api = server({ 'PUT /households/h1/shopping-list/items/i2': (body) => ({ body }) })
    const user = userEvent.setup()
    await openList()

    const meat = within(await screen.findByRole('list', { name: 'Carne' }))
    await user.click(meat.getByRole('button', { name: 'Cambiar' }))
    const form = within(screen.getByRole('form', { name: 'Cambiar la cantidad de Pechuga de pollo' }))
    const amount = form.getByRole('textbox', { name: 'Cantidad' })
    await user.clear(amount)
    await user.type(amount, '1')
    await user.selectOptions(form.getByRole('combobox', { name: 'Unidad' }), 'KILOGRAM')
    await user.click(form.getByRole('button', { name: 'Guardar' }))

    await waitFor(() =>
      expect(api.last('PUT /households/h1/shopping-list/items/i2')?.body).toEqual({
        foodId: 'food-i2',
        name: null,
        category: 'MEAT',
        quantity: { amount: 1, unit: 'KILOGRAM' },
      }),
    )
    await waitFor(() => expect(screen.queryByRole('form', { name: /Cambiar la cantidad/ })).not.toBeInTheDocument())
  })

  it('removes a line', async () => {
    const api = server({ 'DELETE /households/h1/shopping-list/items/i3': () => ({ status: 204 }) })
    const user = userEvent.setup()
    await openList()

    await user.click(await screen.findByRole('button', { name: 'Quitar Pilas' }))

    await waitFor(() => expect(api.count('DELETE /households/h1/shopping-list/items/i3')).toBe(1))
  })

  it('asks before taking what was bought off the list', async () => {
    const api = server({
      [LIST]: () => ({ body: { items: [{ ...ZUCCHINI, checked: true }, CHICKEN] } }),
      'DELETE /households/h1/shopping-list/items/checked': () => ({ body: { removed: 1 } }),
    })
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const user = userEvent.setup()
    await openList()

    await user.click(await screen.findByRole('button', { name: 'Quitar lo comprado (1)' }))
    expect(confirm).toHaveBeenCalledWith('Se quitará de la lista 1 cosa comprada. ¿Continuar?')
    expect(api.count('DELETE /households/h1/shopping-list/items/checked')).toBe(0)

    confirm.mockReturnValue(true)
    await user.click(screen.getByRole('button', { name: 'Quitar lo comprado (1)' }))
    await waitFor(() => expect(api.count('DELETE /households/h1/shopping-list/items/checked')).toBe(1))
  })

  it('fills the list with what the plan of this week or the next lacks', async () => {
    let lines = 3
    const api = server({ [FROM_PLAN]: () => ({ body: { lines } }) })
    const user = userEvent.setup()
    await openList()

    await user.click(screen.getByRole('button', { name: 'Añadir lo que falta para el plan' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Hay 3 cosas en la lista para el plan de esa semana.')
    expect(api.last(FROM_PLAN)?.body).toEqual({ week: todayIso() })

    lines = 0
    await user.selectOptions(screen.getByRole('combobox', { name: 'Semana' }), 'next')
    await user.click(screen.getByRole('button', { name: 'Añadir lo que falta para el plan' }))
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Al plan de esa semana no le falta nada.'))
    expect(api.last(FROM_PLAN)?.body).toEqual({ week: addDays(todayIso(), 7) })
  })

  it('shows at once what another member changes', async () => {
    const stream = eventStream()
    let items = [ZUCCHINI]
    const api = server({
      [LIST]: () => ({ body: { items } }),
      'GET /households/h1/events': () => ({ response: stream.response }),
    })
    await openList()
    await screen.findByRole('checkbox', { name: 'Calabacín · 2 uds' })
    await waitFor(() => expect(api.count('GET /households/h1/events')).toBe(1))

    items = [{ ...ZUCCHINI, checked: true }]
    stream.send('shopping-list-changed')

    await waitFor(() => expect(screen.getByRole('checkbox', { name: 'Calabacín · 2 uds' })).toBeChecked())
  })

  it('explains why something could not be done', async () => {
    server({ 'DELETE /households/h1/shopping-list/items/i3': () => problem(404, 'SHOPPING_ITEM_NOT_FOUND') })
    const user = userEvent.setup()
    await openList()

    await user.click(await screen.findByRole('button', { name: 'Quitar Pilas' }))

    expect(await screen.findByText('Eso ya no está en la lista.')).toBeInTheDocument()
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

    expect(await screen.findByRole('link', { name: 'Lista de la compra' })).toHaveAttribute('href', '/households/h1/shopping')
  })
})
