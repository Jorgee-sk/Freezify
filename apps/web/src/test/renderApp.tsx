import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AppRoutes } from '../App'
import { session } from '../api/client'
import type { Household, User } from '../api/endpoints'
import { AuthProvider } from '../auth/AuthContext'

export const ANA: User = {
  id: 'u1',
  email: 'ana@example.com',
  displayName: 'Ana',
  locale: 'es',
  createdAt: '2026-10-01T10:00:00Z',
}
export const CASA: Household = {
  id: 'h1',
  name: 'Casa',
  role: 'OWNER',
  memberCount: 2,
  createdAt: '2026-10-01T10:00:00Z',
}
export const SESSION = { accessToken: 'access-1', refreshToken: 'refresh-1', expiresIn: 900, user: ANA }

/** Renders the whole application at `path`, with real routing, session handling and data fetching. */
export function renderApp(path = '/') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter initialEntries={[path]}>
          <AppRoutes />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

/** Makes the next `renderApp` start with Ana signed in (the test still has to serve `GET /users/me`). */
export function signedIn() {
  session.store(SESSION)
}
