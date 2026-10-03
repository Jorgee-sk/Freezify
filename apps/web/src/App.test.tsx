import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { session } from './api/client'
import type { Household } from './api/endpoints'
import { fakeApi, problem } from './test/fakeApi'
import { ANA, CASA, SESSION, renderApp, signedIn } from './test/renderApp'

const EMPTY_PAGE = { items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 }
const NOTHING_TO_CONSUME_FIRST = {
  counts: { EXPIRED: 0, TODAY: 0, URGENT: 0, SOON: 0, UPCOMING: 0, OK: 0, NO_DATE: 0 },
  items: [],
}

afterEach(() => vi.unstubAllGlobals())

describe('authentication', () => {
  it('sends anonymous visitors to the login page', async () => {
    fakeApi({})
    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument()
  })

  it('signs in and shows the households of the user', async () => {
    const server = fakeApi({
      'POST /auth/login': () => ({ body: SESSION }),
      'GET /households': () => ({ body: [CASA] }),
    })
    const user = userEvent.setup()
    renderApp('/login')

    await user.type(screen.getByLabelText('Correo electrónico'), 'ana@example.com')
    await user.type(screen.getByLabelText('Contraseña'), 'correct-horse')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('heading', { name: 'Hola, Ana 👋' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: /Casa/ })).toHaveAttribute('href', '/households/h1')
    expect(screen.getByText('2 miembros')).toBeInTheDocument()
    expect(server.last('POST /auth/login')?.body).toEqual({ email: 'ana@example.com', password: 'correct-horse' })
    // The refresh token goes into an HttpOnly cookie, never into storage that a script can read.
    expect(server.last('POST /auth/login')?.headers['X-Freezify-Session']).toBe('cookie')
    expect(JSON.stringify({ ...localStorage })).not.toContain('refresh-1')
    expect(session.mayResume()).toBe(true)
  })

  it('shows a translated message when the credentials are wrong', async () => {
    fakeApi({ 'POST /auth/login': () => problem(401, 'INVALID_CREDENTIALS') })
    const user = userEvent.setup()
    renderApp('/login')

    await user.type(screen.getByLabelText('Correo electrónico'), 'ana@example.com')
    await user.type(screen.getByLabelText('Contraseña'), 'wrong-password')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Correo o contraseña incorrectos.')
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeEnabled()
  })

  it('registers with the language currently shown', async () => {
    const server = fakeApi({
      'POST /auth/register': () => ({ status: 201, body: SESSION }),
      'GET /households': () => ({ body: [] }),
    })
    const user = userEvent.setup()
    renderApp('/register')

    await user.type(screen.getByLabelText('Tu nombre'), 'Ana')
    await user.type(screen.getByLabelText('Correo electrónico'), 'ana@example.com')
    await user.type(screen.getByLabelText(/Contraseña/), 'correct-horse')
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    expect(await screen.findByText(/Todavía no perteneces a ningún hogar/)).toBeInTheDocument()
    expect(server.last('POST /auth/register')?.body).toEqual({
      email: 'ana@example.com',
      password: 'correct-horse',
      displayName: 'Ana',
      locale: 'es',
    })
  })

  it('restores the session from the cookie', async () => {
    localStorage.setItem('freezify.session', '1')
    fakeApi({
      'POST /auth/refresh': () => ({ body: { accessToken: 'access-2', refreshToken: 'refresh-2' } }),
      'GET /users/me': () => ({ body: ANA }),
      'GET /households': () => ({ body: [CASA] }),
    })
    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Hola, Ana 👋' })).toBeInTheDocument()
  })

  it('returns to the login page when the stored session is no longer valid', async () => {
    localStorage.setItem('freezify.session', '1')
    fakeApi({
      'POST /auth/refresh': () => problem(401, 'INVALID_REFRESH_TOKEN'),
      'GET /users/me': () => problem(401, 'UNAUTHORIZED'),
    })
    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument()
    expect(session.mayResume()).toBe(false)
  })

  it('signs out, revoking the refresh token', async () => {
    signedIn()
    const server = fakeApi({
      'GET /users/me': () => ({ body: ANA }),
      'GET /households': () => ({ body: [] }),
      'POST /auth/logout': () => ({ status: 204 }),
    })
    const user = userEvent.setup()
    renderApp('/')

    await user.click(await screen.findByRole('button', { name: 'Cerrar sesión' }))

    expect(await screen.findByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument()
    // The cookie goes with the request; nothing is sent in the body.
    expect(server.last('POST /auth/logout')?.body).toBeUndefined()
    expect(server.last('POST /auth/logout')?.headers['X-Freezify-Session']).toBe('cookie')
    expect(session.mayResume()).toBe(false)
  })

  it('switches language', async () => {
    fakeApi({})
    const user = userEvent.setup()
    renderApp('/login')

    await user.selectOptions(screen.getByRole('combobox', { name: 'Idioma' }), 'en')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })
})

describe('households', () => {
  it('creates a household and opens it', async () => {
    signedIn()
    const created: Household = { id: 'h2', name: 'Piso', role: 'OWNER', memberCount: 1, createdAt: CASA.createdAt }
    const server = fakeApi({
      'GET /users/me': () => ({ body: ANA }),
      'GET /households': () => ({ body: [] }),
      'POST /households': () => ({ status: 201, body: created }),
      'GET /households/h2': () => ({ body: created }),
      'GET /households/h2/inventory': () => ({ body: EMPTY_PAGE }),
      'GET /households/h2/inventory/consume-first': () => ({ body: NOTHING_TO_CONSUME_FIRST }),
    })
    const user = userEvent.setup()
    renderApp('/')

    await user.type(await screen.findByLabelText('Nombre del hogar'), 'Piso')
    await user.click(screen.getByRole('button', { name: 'Crear' }))

    expect(await screen.findByRole('heading', { name: 'Piso' })).toBeInTheDocument()
    expect(server.last('POST /households')?.body).toEqual({ name: 'Piso' })
    expect(await screen.findByText(/Aún no hay alimentos/)).toBeInTheDocument()
  })

  it('explains why joining with a bad code failed', async () => {
    signedIn()
    fakeApi({
      'GET /users/me': () => ({ body: ANA }),
      'GET /households': () => ({ body: [] }),
      'POST /households/join': () => problem(404, 'INVITATION_NOT_FOUND'),
    })
    const user = userEvent.setup()
    renderApp('/')

    await user.type(await screen.findByLabelText('Código de invitación'), 'ZZZZZZZZ')
    await user.click(screen.getByRole('button', { name: 'Unirme' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('El código no es válido o ha caducado.')
  })

  it('shows the members of a household', async () => {
    signedIn()
    fakeApi({
      'GET /users/me': () => ({ body: ANA }),
      'GET /households/h1': () => ({ body: CASA }),
      'GET /households/h1/members': () => ({
        body: [{ userId: 'u1', displayName: 'Ana', email: ANA.email, role: 'OWNER', joinedAt: '2026-10-01T10:00:00Z' }],
      }),
      'GET /households/h1/invitations': () => ({ body: [] }),
    })
    renderApp('/households/h1/settings')

    const members = within(await screen.findByRole('region', { name: 'Miembros' }))
    expect(await members.findByText('Ana')).toBeInTheDocument()
    expect(members.getByText(/Desde el 01\/10\/2026/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Volver al inventario/ })).toHaveAttribute('href', '/households/h1')
  })

  it('lets the owner generate an invitation code', async () => {
    signedIn()
    fakeApi({
      'GET /users/me': () => ({ body: ANA }),
      'GET /households/h1': () => ({ body: CASA }),
      'GET /households/h1/members': () => ({ body: [] }),
      'GET /households/h1/invitations': () => ({ body: [] }),
      'POST /households/h1/invitations': () => ({
        status: 201,
        body: { code: 'ABCD2345', expiresAt: '2026-10-08T10:00:00Z' },
      }),
    })
    const user = userEvent.setup()
    renderApp('/households/h1/settings')

    await user.click(await screen.findByRole('button', { name: 'Generar código' }))

    expect(await screen.findByText('ABCD2345')).toBeInTheDocument()
    expect(screen.getByText('Válido hasta el 08/10/2026')).toBeInTheDocument()
  })

  it('asks for confirmation before deleting a household', async () => {
    signedIn()
    const server = fakeApi({
      'GET /users/me': () => ({ body: ANA }),
      'GET /households/h1': () => ({ body: CASA }),
      'GET /households/h1/members': () => ({ body: [] }),
      'GET /households/h1/invitations': () => ({ body: [] }),
      'GET /households': () => ({ body: [] }),
      'DELETE /households/h1': () => ({ status: 204 }),
    })
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const user = userEvent.setup()
    renderApp('/households/h1/settings')

    await user.click(await screen.findByRole('button', { name: 'Eliminar hogar' }))
    expect(confirm).toHaveBeenCalledOnce()
    expect(server.count('DELETE /households/h1')).toBe(0)

    confirm.mockReturnValue(true)
    await user.click(screen.getByRole('button', { name: 'Eliminar hogar' }))

    expect(await screen.findByText(/Todavía no perteneces a ningún hogar/)).toBeInTheDocument()
    expect(server.count('DELETE /households/h1')).toBe(1)
  })

  it('offers members leaving instead of owner actions', async () => {
    signedIn()
    fakeApi({
      'GET /users/me': () => ({ body: ANA }),
      'GET /households/h1': () => ({ body: { ...CASA, role: 'MEMBER' } }),
      'GET /households/h1/members': () => ({ body: [] }),
      'GET /households/h1/invitations': () => ({ body: [] }),
    })
    renderApp('/households/h1/settings')

    expect(await screen.findByRole('button', { name: 'Abandonar hogar' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Eliminar hogar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Renombrar' })).not.toBeInTheDocument()
  })

  it('shows a translated message for a household the user cannot access', async () => {
    signedIn()
    fakeApi({
      'GET /users/me': () => ({ body: ANA }),
      'GET /households/nope': () => problem(404, 'HOUSEHOLD_NOT_FOUND'),
    })
    renderApp('/households/nope')

    expect(await screen.findByRole('alert')).toHaveTextContent('Este hogar no existe o ya no perteneces a él.')
  })
})
