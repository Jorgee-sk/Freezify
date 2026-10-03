import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AppNotification, NotificationPreferences, NotifiedItem } from '../api/notifications'
import { fakeApi } from '../test/fakeApi'
import { ANA, CASA, renderApp, signedIn } from '../test/renderApp'

const LIST = 'GET /notifications'
const UNREAD = 'GET /notifications/unread-count'
const PREFERENCES = 'GET /notifications/preferences'

function food(name: string, daysUntilExpiration: number, estimated = false): NotifiedItem {
  return { name, expirationDate: '2026-10-03', estimated, daysUntilExpiration }
}

function notification(id: string, items: NotifiedItem[], overrides: Partial<AppNotification> = {}): AppNotification {
  return {
    id,
    type: 'EXPIRATION',
    householdId: 'h1',
    householdName: 'Casa',
    day: '2026-10-02',
    itemCount: items.length,
    items,
    createdAt: '2026-10-02T07:00:00Z',
    read: false,
    ...overrides,
  }
}

function page(items: AppNotification[]) {
  return { body: { items, page: 0, size: 20, totalItems: items.length, totalPages: items.length ? 1 : 0 } }
}

const DEFAULTS: NotificationPreferences = {
  expirationAlerts: true,
  deliveryHour: 9,
  frequency: 'DAILY',
  threshold: 'URGENT',
  mutedCategories: [],
}

function server(routes: Parameters<typeof fakeApi>[0]) {
  signedIn()
  return fakeApi({ 'GET /users/me': () => ({ body: ANA }), ...routes })
}

afterEach(() => vi.unstubAllGlobals())

describe('notification bell', () => {
  it('says how many notifications are waiting and leads to them', async () => {
    server({ 'GET /households': () => ({ body: [CASA] }), [UNREAD]: () => ({ body: { count: 2 } }) })
    renderApp('/')

    const bell = await screen.findByRole('link', { name: 'Avisos: 2 sin leer' })
    expect(bell).toHaveAttribute('href', '/notifications')
    expect(bell).toHaveTextContent('2')
  })

  it('shows no count when everything has been read', async () => {
    server({ 'GET /households': () => ({ body: [CASA] }) })
    renderApp('/')

    const bell = await screen.findByRole('link', { name: 'Avisos' })
    expect(bell).not.toHaveTextContent(/\d/)
  })

  it('is absent for visitors who are not signed in', async () => {
    fakeApi({})
    renderApp('/login')

    expect(await screen.findByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Avisos/ })).not.toBeInTheDocument()
  })
})

describe('notifications', () => {
  it('names a single food and counts several', async () => {
    server({
      [LIST]: () =>
        page([
          notification('n2', [food('Yogur', -1), food('Jamón', 0), food('Leche', 2)], { itemCount: 4 }),
          notification('n1', [food('Brócoli', 2)], { day: '2026-10-01', read: true }),
        ]),
    })
    renderApp('/notifications')

    const list = within(await screen.findByRole('list', { name: 'Avisos' }))
    const several = within(
      (await list.findByText('Tienes 4 alimentos que deberías consumir pronto')).closest('a')!,
    )
    expect(several.getByText('Yogur: su fecha de caducidad pasó hace 1 día')).toBeInTheDocument()
    expect(several.getByText('Jamón: su fecha de caducidad es hoy')).toBeInTheDocument()
    expect(several.getByText('Leche: quedan 2 días para su fecha de caducidad')).toBeInTheDocument()
    expect(several.getByText('y 1 más')).toBeInTheDocument()
    expect(several.getByText('Casa · 02/10/2026')).toBeInTheDocument()
    expect(several.getByText('Nuevo')).toBeInTheDocument()

    const single = within(list.getByText('Brócoli: quedan 2 días para su fecha de caducidad').closest('a')!)
    expect(single.getByText('Casa · 01/10/2026')).toBeInTheDocument()
    expect(single.queryByText('Nuevo')).not.toBeInTheDocument()
    expect(single.queryByRole('listitem')).not.toBeInTheDocument()
  })

  it('words an estimated date as an estimate', async () => {
    server({ [LIST]: () => page([notification('n1', [food('Merluza', 1, true)])]) })
    renderApp('/notifications')

    expect(
      await screen.findByText('Merluza: queda aproximadamente 1 día para su fecha de caducidad (fecha estimada)'),
    ).toBeInTheDocument()
  })

  it('says so when there is nothing to show', async () => {
    server({ [LIST]: () => page([]) })
    renderApp('/notifications')

    expect(await screen.findByText(/No tienes avisos/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Marcar todo como leído' })).not.toBeInTheDocument()
  })

  it('opens the inventory the notification talks about and marks it as read', async () => {
    const api = server({
      [LIST]: () => page([notification('n1', [food('Leche', 1)])]),
      'POST /notifications/n1/read': () => ({ status: 204 }),
      'GET /households/h1': () => ({ body: CASA }),
      'GET /households/h1/inventory': () => ({ body: { items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 } }),
      'GET /households/h1/inventory/consume-first': () => ({
        body: { counts: { EXPIRED: 0, TODAY: 0, URGENT: 0, SOON: 0, UPCOMING: 0, OK: 0, NO_DATE: 0 }, items: [] },
      }),
    })
    const user = userEvent.setup()
    renderApp('/notifications')

    await user.click(await screen.findByRole('link', { name: /Leche: queda 1 día para su fecha de caducidad/ }))

    expect(await screen.findByRole('heading', { name: 'Inventario' })).toBeInTheDocument()
    expect(api.count('POST /notifications/n1/read')).toBe(1)
  })

  it('does not mark again what was already read', async () => {
    const api = server({
      [LIST]: () => page([notification('n1', [food('Leche', 1)], { read: true })]),
      'GET /households/h1': () => ({ body: CASA }),
      'GET /households/h1/inventory': () => ({ body: { items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 } }),
      'GET /households/h1/inventory/consume-first': () => ({
        body: { counts: { EXPIRED: 0, TODAY: 0, URGENT: 0, SOON: 0, UPCOMING: 0, OK: 0, NO_DATE: 0 }, items: [] },
      }),
    })
    const user = userEvent.setup()
    renderApp('/notifications')

    await user.click(await screen.findByRole('link', { name: /Leche: queda 1 día para su fecha de caducidad/ }))

    expect(await screen.findByRole('heading', { name: 'Inventario' })).toBeInTheDocument()
    expect(api.count('POST /notifications/n1/read')).toBe(0)
  })

  it('marks everything as read at once', async () => {
    let read = false
    const api = server({
      [LIST]: () => page([notification('n1', [food('Leche', 1)], { read })]),
      [UNREAD]: () => ({ body: { count: read ? 0 : 1 } }),
      'POST /notifications/read-all': () => {
        read = true
        return { status: 204 }
      },
    })
    const user = userEvent.setup()
    renderApp('/notifications')

    expect(await screen.findByRole('link', { name: 'Avisos: 1 sin leer' })).toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: 'Marcar todo como leído' }))

    await waitFor(() => expect(screen.queryByText('Nuevo')).not.toBeInTheDocument())
    expect(screen.queryByRole('button', { name: 'Marcar todo como leído' })).not.toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Avisos' })).toBeInTheDocument()
    expect(api.count('POST /notifications/read-all')).toBe(1)
  })
})

describe('notification preferences', () => {
  it('saves what the user chooses', async () => {
    const api = server({
      [PREFERENCES]: () => ({ body: DEFAULTS }),
      'PUT /notifications/preferences': (body) => ({ body }),
    })
    const user = userEvent.setup()
    renderApp('/notifications/preferences')

    expect(await screen.findByLabelText('Avisarme de los alimentos que van a caducar')).toBeChecked()
    expect(screen.getByLabelText('A partir de las')).toHaveValue('9')
    expect(screen.getByLabelText('Lácteos')).toBeChecked()

    await user.selectOptions(screen.getByLabelText('A partir de las'), '20:00')
    await user.selectOptions(screen.getByLabelText('Como mucho'), 'Cada 3 días')
    await user.selectOptions(screen.getByLabelText('Avisarme cuando un alimento'), 'Caduque en 5 días o menos')
    await user.click(screen.getByLabelText('Lácteos'))
    await user.click(screen.getByLabelText('Bebidas'))
    await user.click(screen.getByRole('button', { name: 'Guardar' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Preferencias guardadas')
    expect(api.last('PUT /notifications/preferences')?.body).toEqual({
      expirationAlerts: true,
      deliveryHour: 20,
      frequency: 'EVERY_THREE_DAYS',
      threshold: 'SOON',
      mutedCategories: ['DAIRY', 'BEVERAGES'],
    })

    // A further change is not saved until the user says so.
    await user.click(screen.getByLabelText('Lácteos'))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('shows what was chosen before and lets the user turn everything off', async () => {
    const api = server({
      [PREFERENCES]: () => ({
        body: { ...DEFAULTS, deliveryHour: 21, frequency: 'WEEKLY', threshold: 'TODAY', mutedCategories: ['MEAT'] },
      }),
      'PUT /notifications/preferences': (body) => ({ body }),
    })
    const user = userEvent.setup()
    renderApp('/notifications/preferences')

    expect(await screen.findByLabelText('A partir de las')).toHaveValue('21')
    expect(screen.getByLabelText('Como mucho')).toHaveValue('WEEKLY')
    expect(screen.getByLabelText('Avisarme cuando un alimento')).toHaveValue('TODAY')
    expect(screen.getByLabelText('Carne')).not.toBeChecked()
    expect(screen.getByLabelText('Pescado')).toBeChecked()

    await user.click(screen.getByLabelText('Avisarme de los alimentos que van a caducar'))
    expect(screen.getByLabelText('A partir de las')).toBeDisabled()
    expect(screen.getByLabelText('Carne')).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Guardar' }))

    await screen.findByRole('status')
    expect(api.last('PUT /notifications/preferences')?.body).toMatchObject({
      expirationAlerts: false,
      deliveryHour: 21,
      mutedCategories: ['MEAT'],
    })
  })

  it('is reachable from the list of notifications', async () => {
    server({ [LIST]: () => page([]), [PREFERENCES]: () => ({ body: DEFAULTS }) })
    const user = userEvent.setup()
    renderApp('/notifications')

    await user.click(await screen.findByRole('link', { name: 'Preferencias' }))

    expect(await screen.findByRole('heading', { name: 'Preferencias de avisos' })).toBeInTheDocument()
  })
})
