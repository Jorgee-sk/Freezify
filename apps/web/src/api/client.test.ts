import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeApi, problem } from '../test/fakeApi'
import { ApiError, api, session } from './client'

const USER = { id: 'u1', displayName: 'Ana' }

afterEach(() => vi.unstubAllGlobals())

describe('api client', () => {
  it('sends the access token and returns the parsed body', async () => {
    session.store({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    const server = fakeApi({ 'GET /users/me': () => ({ body: USER }) })

    await expect(api('/users/me')).resolves.toEqual(USER)

    expect(server.last('GET /users/me')?.headers.Authorization).toBe('Bearer access-1')
  })

  it('does not send credentials to unauthenticated endpoints', async () => {
    session.store({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    const server = fakeApi({ 'POST /auth/login': () => ({ body: {} }) })

    await api('/auth/login', { method: 'POST', body: { email: 'a@b.c', password: 'x' }, authenticated: false })

    expect(server.last('POST /auth/login')?.headers.Authorization).toBeUndefined()
    expect(server.last('POST /auth/login')?.body).toEqual({ email: 'a@b.c', password: 'x' })
  })

  it('renews an expired session once and retries the request', async () => {
    session.store({ accessToken: 'expired', refreshToken: 'refresh-1' })
    const server = fakeApi({
      'GET /users/me': (_, { headers }) =>
        headers.Authorization === 'Bearer access-2' ? { body: USER } : problem(401, 'UNAUTHORIZED'),
      'POST /auth/refresh': () => ({ body: { accessToken: 'access-2', refreshToken: 'refresh-2' } }),
    })

    await expect(api('/users/me')).resolves.toEqual(USER)

    expect(server.last('POST /auth/refresh')?.body).toEqual({ refreshToken: 'refresh-1' })
    expect(session.refreshToken()).toBe('refresh-2')
  })

  it('shares a single refresh between concurrent requests', async () => {
    // Refresh tokens are single use: a second refresh with the same token would revoke the session.
    session.store({ accessToken: 'expired', refreshToken: 'refresh-1' })
    const server = fakeApi({
      'GET /users/me': (_, { headers }) =>
        headers.Authorization === 'Bearer access-2' ? { body: USER } : problem(401, 'UNAUTHORIZED'),
      'GET /households': (_, { headers }) =>
        headers.Authorization === 'Bearer access-2' ? { body: [] } : problem(401, 'UNAUTHORIZED'),
      'POST /auth/refresh': () => ({ body: { accessToken: 'access-2', refreshToken: 'refresh-2' } }),
    })

    await Promise.all([api('/users/me'), api('/households'), api('/users/me')])

    expect(server.count('POST /auth/refresh')).toBe(1)
  })

  it('restores the session after a reload, when only the refresh token is left', async () => {
    localStorage.setItem('freezify.refreshToken', 'refresh-1')
    const server = fakeApi({
      'GET /users/me': () => ({ body: USER }),
      'POST /auth/refresh': () => ({ body: { accessToken: 'access-2', refreshToken: 'refresh-2' } }),
    })

    await api('/users/me')

    expect(server.calls.map((call) => call.route)).toEqual(['POST /auth/refresh', 'GET /users/me'])
    expect(server.last('GET /users/me')?.headers.Authorization).toBe('Bearer access-2')
  })

  it('ends the session when it cannot be renewed', async () => {
    session.store({ accessToken: 'expired', refreshToken: 'revoked' })
    const expired = vi.fn()
    session.onExpired(expired)
    fakeApi({
      'GET /users/me': () => problem(401, 'UNAUTHORIZED'),
      'POST /auth/refresh': () => problem(401, 'INVALID_REFRESH_TOKEN'),
    })

    await expect(api('/users/me')).rejects.toMatchObject({ status: 401, code: 'UNAUTHORIZED' })

    expect(expired).toHaveBeenCalledOnce()
    expect(session.refreshToken()).toBeNull()
  })

  it('exposes the error code and field errors of a problem response', async () => {
    fakeApi({
      'POST /auth/register': () => ({
        status: 400,
        body: { code: 'VALIDATION_ERROR', detail: 'bad', errors: [{ field: 'email', message: 'invalid' }] },
      }),
    })

    const error = await api('/auth/register', { method: 'POST', body: {}, authenticated: false }).catch(
      (failure: unknown) => failure,
    )

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ code: 'VALIDATION_ERROR', fieldErrors: [{ field: 'email', message: 'invalid' }] })
  })

  it('handles responses without a body', async () => {
    session.store({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    fakeApi({ 'DELETE /households/h1': () => ({ status: 204 }) })

    await expect(api('/households/h1', { method: 'DELETE' })).resolves.toBeUndefined()
  })
})
