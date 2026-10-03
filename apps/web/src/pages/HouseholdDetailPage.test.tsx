import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Invitation, Member } from '../api/endpoints'
import { fakeApi, problem } from '../test/fakeApi'
import { ANA, CASA, renderApp, signedIn } from '../test/renderApp'

const ANA_MEMBER: Member = {
  userId: 'u1',
  displayName: 'Ana',
  email: ANA.email,
  role: 'OWNER',
  joinedAt: '2026-10-01T10:00:00Z',
}
const LUCIA: Member = {
  userId: 'u2',
  displayName: 'Lucía',
  email: 'lucia@example.com',
  role: 'MEMBER',
  joinedAt: '2026-10-02T10:00:00Z',
}
const ANAS_CODE: Invitation = { code: 'ABCD2345', expiresAt: '2026-10-08T10:00:00Z', createdBy: 'u1' }
const LUCIAS_CODE: Invitation = { code: 'WXYZ6789', expiresAt: '2026-10-09T10:00:00Z', createdBy: 'u2' }

function server(routes: Parameters<typeof fakeApi>[0] = {}) {
  signedIn()
  return fakeApi({
    'GET /users/me': () => ({ body: ANA }),
    'GET /households/h1': () => ({ body: CASA }),
    'GET /households/h1/members': () => ({ body: [ANA_MEMBER, LUCIA] }),
    'GET /households/h1/invitations': () => ({ body: [ANAS_CODE, LUCIAS_CODE] }),
    'GET /households': () => ({ body: [CASA] }),
    ...routes,
  })
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('household settings', () => {
  it('lists the codes that still let someone join', async () => {
    server()
    renderApp('/households/h1/settings')

    const codes = within(await screen.findByRole('list', { name: 'Códigos activos' }))
    expect(codes.getByText('ABCD2345')).toBeInTheDocument()
    expect(codes.getByText('Válido hasta el 08/10/2026')).toBeInTheDocument()
    // The owner can revoke any code.
    expect(codes.getByRole('button', { name: 'Revocar el código WXYZ6789' })).toBeInTheDocument()
  })

  it('asks before revoking a code', async () => {
    let codes = [ANAS_CODE, LUCIAS_CODE]
    const api = server({
      'GET /households/h1/invitations': () => ({ body: codes }),
      'DELETE /households/h1/invitations/ABCD2345': () => {
        codes = [LUCIAS_CODE]
        return { status: 204 }
      },
    })
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const user = userEvent.setup()
    renderApp('/households/h1/settings')

    await user.click(await screen.findByRole('button', { name: 'Revocar el código ABCD2345' }))
    expect(confirm).toHaveBeenCalledWith(
      'El código ABCD2345 dejará de servir para unirse. Quien ya se unió con él se queda. ¿Continuar?',
    )
    expect(api.count('DELETE /households/h1/invitations/ABCD2345')).toBe(0)

    confirm.mockReturnValue(true)
    await user.click(screen.getByRole('button', { name: 'Revocar el código ABCD2345' }))

    await waitFor(() => expect(screen.queryByText('ABCD2345')).not.toBeInTheDocument())
    expect(api.count('DELETE /households/h1/invitations/ABCD2345')).toBe(1)
  })

  it('lets a member revoke only the codes they generated', async () => {
    signedIn()
    server({
      'GET /users/me': () => ({ body: { ...ANA, id: 'u2' } }),
      'GET /households/h1': () => ({ body: { ...CASA, role: 'MEMBER' } }),
    })
    renderApp('/households/h1/settings')

    const codes = within(await screen.findByRole('list', { name: 'Códigos activos' }))
    expect(codes.getByRole('button', { name: 'Revocar el código WXYZ6789' })).toBeInTheDocument()
    expect(codes.queryByRole('button', { name: 'Revocar el código ABCD2345' })).not.toBeInTheDocument()
  })

  it('shows a newly generated code among the active ones', async () => {
    let codes: Invitation[] = []
    server({
      'GET /households/h1/invitations': () => ({ body: codes }),
      'POST /households/h1/invitations': () => {
        codes = [ANAS_CODE]
        return { status: 201, body: ANAS_CODE }
      },
    })
    const user = userEvent.setup()
    renderApp('/households/h1/settings')

    await user.click(await screen.findByRole('button', { name: 'Generar código' }))

    expect(await screen.findByRole('list', { name: 'Códigos activos' })).toHaveTextContent('ABCD2345')
  })

  it('lets the owner hand the household over, asking first', async () => {
    let role: 'OWNER' | 'MEMBER' = 'OWNER'
    const api = server({
      'GET /households/h1': () => ({ body: { ...CASA, role } }),
      'POST /households/h1/owner': () => {
        role = 'MEMBER'
        return { status: 204 }
      },
    })
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = userEvent.setup()
    renderApp('/households/h1/settings')

    expect(await screen.findByText(/haz propietario antes a otro miembro/)).toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: 'Hacer propietario a Lucía' }))

    expect(confirm).toHaveBeenCalledWith(
      'Lucía pasará a ser el propietario del hogar y tú seguirás como miembro. Solo Lucía podrá devolvértelo. ¿Continuar?',
    )
    expect(api.last('POST /households/h1/owner')?.body).toEqual({ userId: 'u2' })
    // Now a member: leaving is offered instead of deleting.
    expect(await screen.findByRole('button', { name: 'Abandonar hogar' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Hacer propietario a Lucía' })).not.toBeInTheDocument()
  })

  it('explains why a code could not be revoked', async () => {
    server({ 'DELETE /households/h1/invitations/ABCD2345': () => problem(404, 'INVITATION_NOT_FOUND') })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = userEvent.setup()
    renderApp('/households/h1/settings')

    await user.click(await screen.findByRole('button', { name: 'Revocar el código ABCD2345' }))

    expect(await screen.findByText('El código no es válido o ha caducado.')).toBeInTheDocument()
  })
})
