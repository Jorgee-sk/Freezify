import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ConsumeFirst, InventoryItem } from '../api/inventory'
import { todayIso } from '../inventory/format'
import { eventStream, fakeApi, problem } from '../test/fakeApi'
import { ANA, CASA, renderApp, signedIn } from '../test/renderApp'

const LIST = 'GET /households/h1/inventory'

function item(overrides: Partial<InventoryItem> & Pick<InventoryItem, 'id' | 'name'>): InventoryItem {
  return {
    householdId: 'h1',
    foodId: null,
    category: 'OTHER',
    quantity: { amount: 1, unit: 'UNIT' },
    storageLocation: 'REFRIGERATOR',
    status: 'AVAILABLE',
    purchaseDate: '2026-10-01',
    expirationDate: null,
    expirationSource: null,
    userExpirationDate: null,
    daysUntilExpiration: null,
    priority: null,
    openedDate: null,
    barcode: null,
    brand: null,
    estimatedPrice: null,
    notes: null,
    createdAt: '2026-10-01T10:00:00Z',
    updatedAt: '2026-10-01T10:00:00Z',
    ...overrides,
  }
}

function page(items: InventoryItem[]) {
  return { body: { items, page: 0, size: 50, totalItems: items.length, totalPages: items.length ? 1 : 0 } }
}

/** What the consume-first endpoint answers for `items`, all of them needing attention. */
function consumeFirst(items: InventoryItem[]): ConsumeFirst {
  const counts = { EXPIRED: 0, TODAY: 0, URGENT: 0, SOON: 0, UPCOMING: 0, OK: 0, NO_DATE: 0 }
  for (const entry of items) counts[entry.priority ?? 'NO_DATE'] += 1
  return { counts, items }
}

const CONSUME_FIRST = 'GET /households/h1/inventory/consume-first'

const POLLO = item({
  id: 'i1',
  name: 'Pollo',
  quantity: { amount: 1, unit: 'KILOGRAM' },
  expirationDate: '2026-10-03',
  expirationSource: 'USER',
  userExpirationDate: '2026-10-03',
})

/** Routes every inventory test needs; `extra` adds or overrides. */
function server(items: InventoryItem[], extra: Parameters<typeof fakeApi>[0] = {}) {
  signedIn()
  return fakeApi({
    'GET /users/me': () => ({ body: ANA }),
    'GET /households/h1': () => ({ body: CASA }),
    [LIST]: () => page(items),
    'GET /households/h1/inventory/recent': () => ({ body: [] }),
    [CONSUME_FIRST]: () => ({ body: consumeFirst([]) }),
    ...extra,
  })
}

function row(name: string) {
  return within(within(screen.getByRole('list', { name: 'Inventario' })).getByText(name).closest('li')!)
}

/** Like `row`, but waits for the inventory to load first. */
async function findRow(name: string) {
  const list = await screen.findByRole('list', { name: 'Inventario' })
  return within((await within(list).findByText(name)).closest('li')!)
}

/** The add/edit form. Its fields share labels with the filters, so they are looked up inside it. */
function form() {
  return within(screen.getByRole('region', { name: /(Nuevo|Editar) alimento/ }))
}

afterEach(() => vi.unstubAllGlobals())

describe('inventory list', () => {
  it('shows what the household has, with quantity, place and expiration', async () => {
    server([
      POLLO,
      item({ id: 'i2', name: 'Huevos', quantity: { amount: 6, unit: 'UNIT' }, brand: 'Campo' }),
      item({
        id: 'i3',
        name: 'Leche',
        quantity: { amount: 0.75, unit: 'LITER' },
        status: 'OPENED',
        openedDate: '2026-09-30',
        expirationDate: '2026-10-06',
        expirationSource: 'ESTIMATED',
      }),
    ])
    renderApp('/households/h1')

    expect(await screen.findByRole('heading', { name: 'Casa' })).toBeInTheDocument()
    expect(await screen.findByText('3 alimentos')).toBeInTheDocument()
    expect(row('Pollo').getByText(/1 kg · Nevera · Caduca el 03\/10\/2026/)).toBeInTheDocument()
    expect(row('Huevos').getByText(/6 uds · Nevera · Sin fecha de caducidad/)).toBeInTheDocument()
    expect(row('Huevos').getByText(/Campo/)).toBeInTheDocument()
    // An estimated date is never shown as if it were the one on the package.
    expect(row('Leche').getByText(/Caduca hacia el 06\/10\/2026 \(fecha estimada\)/)).toBeInTheDocument()
    expect(row('Leche').getByText(/Abierto el 30\/09\/2026/)).toBeInTheDocument()
    expect(row('Leche').queryByRole('button', { name: 'Abrir' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Miembros y ajustes' })).toHaveAttribute(
      'href',
      '/households/h1/settings',
    )
  })

  it('invites to add food when the inventory is empty', async () => {
    server([])
    renderApp('/households/h1')

    expect(await screen.findByText(/Aún no hay alimentos/)).toBeInTheDocument()
  })

  it('filters by place, category, state and text', async () => {
    const api = server([POLLO])
    const user = userEvent.setup()
    renderApp('/households/h1')
    await screen.findByText('Pollo')
    expect(Object.fromEntries(api.last(LIST)!.query)).toEqual({ state: 'ACTIVE', page: '0', size: '50' })

    await user.click(screen.getByRole('button', { name: 'Congelador' }))
    await waitFor(() => expect(api.last(LIST)!.query.get('location')).toBe('FREEZER'))
    expect(screen.getByRole('button', { name: 'Congelador' })).toHaveAttribute('aria-pressed', 'true')

    await user.selectOptions(screen.getByRole('combobox', { name: 'Categoría' }), 'MEAT')
    await waitFor(() => expect(api.last(LIST)!.query.get('category')).toBe('MEAT'))

    await user.selectOptions(screen.getByRole('combobox', { name: 'Mostrar' }), 'FINISHED')
    await waitFor(() => expect(api.last(LIST)!.query.get('state')).toBe('FINISHED'))

    await user.type(screen.getByRole('searchbox', { name: 'Buscar' }), 'pol')
    await waitFor(() => expect(api.last(LIST)!.query.get('q')).toBe('pol'))
    expect(api.last(LIST)!.query.get('location')).toBe('FREEZER')
  })

  it('says so when nothing matches the filter', async () => {
    server([])
    const user = userEvent.setup()
    renderApp('/households/h1')
    await screen.findByText(/Aún no hay alimentos/)

    await user.click(screen.getByRole('button', { name: 'Despensa' }))

    expect(await screen.findByText('Ningún alimento coincide con el filtro.')).toBeInTheDocument()
  })

  it('does not offer actions on food that is already finished', async () => {
    server([item({ id: 'i9', name: 'Yogur', status: 'CONSUMED', quantity: { amount: 0, unit: 'UNIT' } })])
    renderApp('/households/h1')

    expect((await findRow('Yogur')).getByText('Consumido')).toBeInTheDocument()
    expect(row('Yogur').queryByRole('button')).not.toBeInTheDocument()
  })

  it('pages through long inventories', async () => {
    const api = server([], {
      [LIST]: (_, { query }) => ({
        body: {
          items: [item({ id: `p${query.get('page')}`, name: `Alimento de la página ${query.get('page')}` })],
          page: Number(query.get('page')),
          size: 50,
          totalItems: 120,
          totalPages: 3,
        },
      }),
    })
    const user = userEvent.setup()
    renderApp('/households/h1')

    expect(await screen.findByText('Página 1 de 3')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Siguiente' }))

    expect(await screen.findByText('Alimento de la página 1')).toBeInTheDocument()
    expect(screen.getByText('Página 2 de 3')).toBeInTheDocument()
    expect(api.last(LIST)!.query.get('page')).toBe('1')
  })
})

describe('expiration priority', () => {
  const expired = item({ id: 'e1', name: 'Leche', expirationDate: '2026-09-29', daysUntilExpiration: -3, priority: 'EXPIRED' })
  const today = item({ id: 'e2', name: 'Yogur', expirationDate: '2026-10-02', daysUntilExpiration: 0, priority: 'TODAY' })
  const urgent = item({ id: 'e3', name: 'Pollo', expirationDate: '2026-10-03', daysUntilExpiration: 1, priority: 'URGENT' })
  const soon = item({ id: 'e4', name: 'Lechuga', expirationDate: '2026-10-07', daysUntilExpiration: 5, priority: 'SOON' })
  const upcoming = item({ id: 'e5', name: 'Queso', expirationDate: '2026-10-10', daysUntilExpiration: 8, priority: 'UPCOMING' })
  const fine = item({ id: 'e6', name: 'Arroz', expirationDate: '2027-03-01', daysUntilExpiration: 150, priority: 'OK' })
  const undated = item({ id: 'e7', name: 'Sal' })

  it('labels each item with how soon it should be eaten', async () => {
    server([expired, today, urgent, soon, upcoming, fine, undated])
    renderApp('/households/h1')

    expect((await findRow('Leche')).getByText('Caducado · hace 3 días')).toBeInTheDocument()
    expect(row('Yogur').getByText('Vence hoy')).toBeInTheDocument()
    expect(row('Pollo').getByText('Urgente · queda 1 día')).toBeInTheDocument()
    expect(row('Lechuga').getByText('Consumir pronto · quedan 5 días')).toBeInTheDocument()
    expect(row('Queso').getByText('Próximo · quedan 8 días')).toBeInTheDocument()
    // No badge when there is plenty of time, or no date at all.
    expect(row('Arroz').queryByText(/quedan/)).not.toBeInTheDocument()
    expect(row('Sal').queryByText(/quedan|Caducado|Urgente/)).not.toBeInTheDocument()
  })

it('says "expired" once for food the daily sweep has marked as expired', async () => {
    server([{ ...expired, status: 'EXPIRED' }])
    renderApp('/households/h1')

    const leche = await findRow('Leche')
    expect(leche.getAllByText(/Caducado/)).toHaveLength(1)
    // It is still in the house: it can be eaten at one's own risk, thrown away, or its date corrected.
    expect(leche.getByRole('button', { name: 'Consumir' })).toBeInTheDocument()
    expect(leche.getByRole('button', { name: 'Tirar' })).toBeInTheDocument()
    expect(leche.getByRole('button', { name: 'Editar' })).toBeInTheDocument()
    expect(leche.queryByRole('button', { name: 'Abrir' })).not.toBeInTheDocument()
  })

  it('answers "what should I eat first?" at the top of the inventory', async () => {
    server([expired, urgent, soon, fine], {
      [CONSUME_FIRST]: () => ({ body: consumeFirst([expired, urgent, soon]) }),
    })
    renderApp('/households/h1')

    const panel = within(await screen.findByRole('region', { name: /Consume primero/ }))
    expect(panel.getByText('3 alimentos que no pueden esperar')).toBeInTheDocument()
    expect(panel.getAllByRole('listitem').map((entry) => entry.textContent)).toEqual([
      'Leche 1 udCaducado · hace 3 días',
      'Pollo 1 udUrgente · queda 1 día',
      'Lechuga 1 udConsumir pronto · quedan 5 días',
    ])
  })

  it('shows the five most pressing and says how many more there are', async () => {
    const many = Array.from({ length: 7 }, (_, index) =>
      item({ id: `u${index}`, name: `Alimento ${index}`, daysUntilExpiration: 1, priority: 'URGENT' }),
    )
    server(many, { [CONSUME_FIRST]: () => ({ body: consumeFirst(many) }) })
    renderApp('/households/h1')

    const panel = within(await screen.findByRole('region', { name: /Consume primero/ }))
    expect(panel.getAllByRole('listitem')).toHaveLength(5)
    expect(panel.getByText('y 2 más')).toBeInTheDocument()
  })

  it('says nothing when no food needs attention', async () => {
    server([fine, upcoming], { [CONSUME_FIRST]: () => ({ body: consumeFirst([fine, upcoming]) }) })
    renderApp('/households/h1')

    await findRow('Arroz')
    expect(screen.queryByRole('region', { name: /Consume primero/ })).not.toBeInTheDocument()
  })

  it('is refreshed when the inventory changes', async () => {
    let pressing: InventoryItem[] = [urgent]
    const api = server([urgent], {
      [CONSUME_FIRST]: () => ({ body: consumeFirst(pressing) }),
      'POST /households/h1/inventory/e3/consume': () => {
        pressing = []
        return { body: urgent }
      },
    })
    const user = userEvent.setup()
    renderApp('/households/h1')
    await screen.findByRole('region', { name: /Consume primero/ })

    await user.click((await findRow('Pollo')).getByRole('button', { name: 'Consumir' }))
    await user.click(row('Pollo').getByRole('button', { name: 'Confirmar' }))

    await waitFor(() => expect(screen.queryByRole('region', { name: /Consume primero/ })).not.toBeInTheDocument())
    expect(api.count(CONSUME_FIRST)).toBe(2)
  })
})

describe('live updates', () => {
  it('shows what another member changes without reloading', async () => {
    const stream = eventStream()
    const items = [POLLO]
    const api = server([], {
      [LIST]: () => page(items),
      'GET /households/h1/events': () => ({ response: stream.response }),
    })
    renderApp('/households/h1')
    await findRow('Pollo')
    const requestsBefore = api.count(LIST)

    // Someone else adds eggs; the server only says that the inventory changed.
    items.push(item({ id: 'i2', name: 'Huevos' }))
    stream.send('inventory-changed')

    expect(await within(screen.getByRole('list', { name: 'Inventario' })).findByText('Huevos')).toBeInTheDocument()
    expect(api.count(LIST)).toBe(requestsBefore + 1)
  })

  it('does not listen to a household the user cannot open', async () => {
    signedIn()
    const api = fakeApi({
      'GET /users/me': () => ({ body: ANA }),
      'GET /households/h1': () => problem(404, 'HOUSEHOLD_NOT_FOUND'),
    })
    renderApp('/households/h1')

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(api.count('GET /households/h1/events')).toBe(0)
  })
})

describe('adding and editing food', () => {
  it('adds a food picked from the catalog suggestions', async () => {
    const api = server([], {
      'GET /foods': () => ({
        body: [
          { id: 'f1', name: 'Tomate', category: 'VEGETABLES', defaultUnit: 'UNIT', defaultStorage: 'REFRIGERATOR' },
          { id: 'f2', name: 'Tomate frito', category: 'PANTRY', defaultUnit: 'GRAM', defaultStorage: 'PANTRY' },
        ],
      }),
      'POST /households/h1/inventory': () => ({ status: 201, body: item({ id: 'new', name: 'Tomate frito' }) }),
    })
    const user = userEvent.setup()
    renderApp('/households/h1')

    await user.click(await screen.findByRole('button', { name: 'Añadir alimento' }))
    await user.type(form().getByLabelText('Alimento'), 'tom')
    await user.click(await screen.findByRole('button', { name: 'Tomate frito' }))

    // The catalog food brings its usual unit and place.
    expect(form().getByLabelText('Alimento')).toHaveValue('Tomate frito')
    expect(form().getByLabelText('Unidad')).toHaveValue('GRAM')
    expect(form().getByLabelText('Ubicación')).toHaveValue('PANTRY')
    expect(api.last('GET /foods')!.query.get('lang')).toBe('es')

    await user.clear(form().getByLabelText('Cantidad'))
    await user.type(form().getByLabelText('Cantidad'), '400')
    await user.type(form().getByLabelText(/Fecha de caducidad/), '2027-03-01')
    await user.click(form().getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(screen.queryByRole('heading', { name: 'Nuevo alimento' })).not.toBeInTheDocument())
    expect(api.last('POST /households/h1/inventory')!.body).toEqual({
      foodId: 'f2',
      name: 'Tomate frito',
      category: 'PANTRY',
      quantity: { amount: 400, unit: 'GRAM' },
      storageLocation: 'PANTRY',
      purchaseDate: todayIso(),
      expirationDate: '2027-03-01',
      openedDate: null,
      barcode: null,
      brand: null,
      estimatedPrice: null,
      notes: null,
    })
    // The list is fetched again after the change.
    expect(api.count(LIST)).toBeGreaterThan(1)
  })

  it('adds a food that is not in the catalog, with a decimal comma', async () => {
    const api = server([], {
      'GET /foods': () => ({ body: [] }),
      'POST /households/h1/inventory': () => ({ status: 201, body: item({ id: 'new', name: 'Kimchi' }) }),
    })
    const user = userEvent.setup()
    renderApp('/households/h1')

    await user.click(await screen.findByRole('button', { name: 'Añadir alimento' }))
    await user.type(form().getByLabelText('Alimento'), 'Kimchi')
    await user.clear(form().getByLabelText('Cantidad'))
    await user.type(form().getByLabelText('Cantidad'), '0,5')
    await user.selectOptions(form().getByLabelText('Unidad'), 'KILOGRAM')
    await user.click(form().getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(api.count('POST /households/h1/inventory')).toBe(1))
    expect(api.last('POST /households/h1/inventory')!.body).toMatchObject({
      foodId: null,
      name: 'Kimchi',
      category: null,
      quantity: { amount: 0.5, unit: 'KILOGRAM' },
      expirationDate: null,
    })
  })

  it('offers recently added foods to add them again in one click', async () => {
    server([], {
      'GET /households/h1/inventory/recent': () => ({
        body: [
          {
            name: 'Leche',
            foodId: 'f5',
            category: 'DAIRY',
            quantity: { amount: 2, unit: 'LITER' },
            storageLocation: 'REFRIGERATOR',
          },
        ],
      }),
    })
    const user = userEvent.setup()
    renderApp('/households/h1')

    await user.click(await screen.findByRole('button', { name: 'Añadir alimento' }))
    await user.click(await within(screen.getByRole('list', { name: 'Añadidos recientemente' })).findByText('Leche'))

    expect(form().getByLabelText('Alimento')).toHaveValue('Leche')
    expect(form().getByLabelText('Cantidad')).toHaveValue('2')
    expect(form().getByLabelText('Unidad')).toHaveValue('LITER')
  })

  it('does not send a quantity that is not a positive number', async () => {
    const api = server([], { 'GET /foods': () => ({ body: [] }) })
    const user = userEvent.setup()
    renderApp('/households/h1')

    await user.click(await screen.findByRole('button', { name: 'Añadir alimento' }))
    await user.type(form().getByLabelText('Alimento'), 'Arroz')
    await user.clear(form().getByLabelText('Cantidad'))
    await user.type(form().getByLabelText('Cantidad'), 'mucho')
    await user.click(form().getByRole('button', { name: 'Guardar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Introduce una cantidad mayor que cero.')
    expect(api.count('POST /households/h1/inventory')).toBe(0)
  })

  it('edits an item keeping what the form does not show', async () => {
    const opened = { ...POLLO, status: 'OPENED' as const, openedDate: '2026-10-01', barcode: '8412345678905' }
    const api = server([opened], {
      'PUT /households/h1/inventory/i1': () => ({ body: opened }),
    })
    const user = userEvent.setup()
    renderApp('/households/h1')

    await user.click((await findRow('Pollo')).getByRole('button', { name: 'Editar' }))
    expect(screen.getByRole('heading', { name: 'Editar alimento' })).toBeInTheDocument()
    expect(form().getByLabelText('Alimento')).toHaveValue('Pollo')
    expect(form().getByLabelText('Unidad')).toHaveValue('KILOGRAM')

    await user.selectOptions(form().getByLabelText('Ubicación'), 'FREEZER')
    await user.type(form().getByLabelText('Precio estimado (€)'), '7,95')
    await user.click(form().getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(api.count('PUT /households/h1/inventory/i1')).toBe(1))
    expect(api.last('PUT /households/h1/inventory/i1')!.body).toMatchObject({
      name: 'Pollo',
      storageLocation: 'FREEZER',
      estimatedPrice: 7.95,
      expirationDate: '2026-10-03',
      openedDate: '2026-10-01',
      barcode: '8412345678905',
    })
  })
})

describe('estimated dates', () => {
  // Opened milk: the carton says December, but once opened it is estimated to last until the 7th.
  const openedMilk = item({
    id: 'm1',
    name: 'Leche',
    status: 'OPENED',
    openedDate: '2026-10-04',
    expirationDate: '2026-10-07',
    expirationSource: 'ESTIMATED',
    userExpirationDate: '2026-12-24',
  })
  const chicken = item({ id: 'c1', name: 'Pollo', expirationDate: '2026-10-03', expirationSource: 'ESTIMATED' })

  it('edits the date the user gave, not the estimate that replaced it', async () => {
    const api = server([openedMilk], { 'PUT /households/h1/inventory/m1': () => ({ body: openedMilk }) })
    const user = userEvent.setup()
    renderApp('/households/h1')

    await user.click((await findRow('Leche')).getByRole('button', { name: 'Editar' }))
    expect(form().getByLabelText(/Fecha de caducidad/)).toHaveValue('2026-12-24')
    expect(form().getByText(/Ahora mismo estimamos el 07\/10\/2026/)).toBeInTheDocument()

    await user.click(form().getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(api.count('PUT /households/h1/inventory/m1')).toBe(1))
    expect(api.last('PUT /households/h1/inventory/m1')!.body).toMatchObject({ expirationDate: '2026-12-24' })
  })

  it('never sends an estimate back as if the user had typed it', async () => {
    const api = server([chicken], { 'PUT /households/h1/inventory/c1': () => ({ body: chicken }) })
    const user = userEvent.setup()
    renderApp('/households/h1')

    await user.click((await findRow('Pollo')).getByRole('button', { name: 'Editar' }))
    expect(form().getByLabelText(/Fecha de caducidad/)).toHaveValue('')
    expect(form().getByText(/Ahora mismo estimamos el 03\/10\/2026/)).toBeInTheDocument()

    await user.click(form().getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(api.count('PUT /households/h1/inventory/c1')).toBe(1))
    expect(api.last('PUT /households/h1/inventory/c1')!.body).toMatchObject({ expirationDate: null })
  })

  it('tells the user that an empty date will be estimated', async () => {
    server([])
    const user = userEvent.setup()
    renderApp('/households/h1')

    await user.click(await screen.findByRole('button', { name: 'Añadir alimento' }))

    expect(form().getByText(/Si la dejas vacía, la estimamos/)).toBeInTheDocument()
  })
})

describe('using food', () => {
  it('consumes part of an item, in a compatible unit', async () => {
    const api = server([POLLO], {
      'POST /households/h1/inventory/i1/consume': () => ({ body: POLLO }),
    })
    const user = userEvent.setup()
    renderApp('/households/h1')

    await user.click((await findRow('Pollo')).getByRole('button', { name: 'Consumir' }))
    const panel = row('Pollo')
    // Everything that is left is proposed by default.
    expect(panel.getByLabelText('Cantidad')).toHaveValue('1')
    // Kilograms can be given in grams, but never in liters or units.
    expect(within(panel.getByLabelText('Unidad')).getAllByRole('option').map((option) => option.textContent)).toEqual([
      'gramos',
      'kilogramos',
    ])

    await user.clear(panel.getByLabelText('Cantidad'))
    await user.type(panel.getByLabelText('Cantidad'), '250')
    await user.selectOptions(panel.getByLabelText('Unidad'), 'GRAM')
    await user.click(panel.getByRole('button', { name: 'Confirmar' }))

    await waitFor(() => expect(api.count('POST /households/h1/inventory/i1/consume')).toBe(1))
    expect(api.last('POST /households/h1/inventory/i1/consume')!.body).toEqual({
      quantity: { amount: 250, unit: 'GRAM' },
    })
    await waitFor(() => expect(row('Pollo').queryByRole('button', { name: 'Confirmar' })).not.toBeInTheDocument())
  })

  it('records why food was thrown away', async () => {
    const api = server([POLLO], {
      'POST /households/h1/inventory/i1/discard': () => ({ body: POLLO }),
    })
    const user = userEvent.setup()
    renderApp('/households/h1')

    await user.click((await findRow('Pollo')).getByRole('button', { name: 'Tirar' }))
    await user.selectOptions(row('Pollo').getByLabelText('Motivo'), 'SPOILED')
    await user.click(row('Pollo').getByRole('button', { name: 'Confirmar' }))

    await waitFor(() => expect(api.count('POST /households/h1/inventory/i1/discard')).toBe(1))
    expect(api.last('POST /households/h1/inventory/i1/discard')!.body).toEqual({
      quantity: { amount: 1, unit: 'KILOGRAM' },
      reason: 'SPOILED',
    })
  })

  it('explains a rejected quantity and lets the user correct it', async () => {
    server([POLLO], {
      'POST /households/h1/inventory/i1/consume': () => problem(400, 'QUANTITY_EXCEEDS_AVAILABLE'),
    })
    const user = userEvent.setup()
    renderApp('/households/h1')

    await user.click((await findRow('Pollo')).getByRole('button', { name: 'Consumir' }))
    await user.click(row('Pollo').getByRole('button', { name: 'Confirmar' }))

    expect((await findRow('Pollo')).getByRole('alert')).toHaveTextContent('Queda menos cantidad de la indicada.')
    expect(row('Pollo').getByRole('button', { name: 'Confirmar' })).toBeEnabled()
  })

  it('marks an item as opened', async () => {
    const api = server([POLLO], {
      'POST /households/h1/inventory/i1/open': () => ({ body: POLLO }),
    })
    const user = userEvent.setup()
    renderApp('/households/h1')

    await user.click((await findRow('Pollo')).getByRole('button', { name: 'Abrir' }))

    await waitFor(() => expect(api.count('POST /households/h1/inventory/i1/open')).toBe(1))
  })

  it('asks for confirmation before deleting an item', async () => {
    const api = server([POLLO], {
      'DELETE /households/h1/inventory/i1': () => ({ status: 204 }),
    })
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const user = userEvent.setup()
    renderApp('/households/h1')

    await user.click((await findRow('Pollo')).getByRole('button', { name: 'Eliminar' }))
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('Pollo'))
    expect(api.count('DELETE /households/h1/inventory/i1')).toBe(0)

    confirm.mockReturnValue(true)
    await user.click(row('Pollo').getByRole('button', { name: 'Eliminar' }))

    await waitFor(() => expect(api.count('DELETE /households/h1/inventory/i1')).toBe(1))
  })
})
