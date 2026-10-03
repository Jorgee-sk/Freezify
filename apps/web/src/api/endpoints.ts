import { api } from './client'

export type Locale = 'es' | 'en'
export type HouseholdRole = 'OWNER' | 'MEMBER'

export interface User {
  id: string
  email: string
  displayName: string
  locale: Locale
  createdAt: string
}

export interface Session {
  accessToken: string
  /** Always null on the web: the refresh token is in an HttpOnly cookie. */
  refreshToken: string | null
  expiresIn: number
  user: User
}

export interface Household {
  id: string
  name: string
  role: HouseholdRole
  memberCount: number
  createdAt: string
}

export interface Member {
  userId: string
  displayName: string
  email: string
  role: HouseholdRole
  joinedAt: string
}

export interface Invitation {
  code: string
  expiresAt: string
  /** Who generated it: they and the owner can revoke it. */
  createdBy: string
}

export const authApi = {
  register: (input: { email: string; password: string; displayName: string; locale: Locale }) =>
    api<Session>('/auth/register', { method: 'POST', body: input, authenticated: false }),
  login: (input: { email: string; password: string }) =>
    api<Session>('/auth/login', { method: 'POST', body: input, authenticated: false }),
  /** Revokes the refresh token of the cookie and tells the browser to forget it. */
  logout: () => api<void>('/auth/logout', { method: 'POST', authenticated: false }),
}

export const usersApi = {
  me: () => api<User>('/users/me'),
  updateMe: (input: { displayName?: string; locale?: Locale }) =>
    api<User>('/users/me', { method: 'PATCH', body: input }),
}

export const householdsApi = {
  list: () => api<Household[]>('/households'),
  get: (id: string) => api<Household>(`/households/${id}`),
  create: (name: string) => api<Household>('/households', { method: 'POST', body: { name } }),
  rename: (id: string, name: string) => api<Household>(`/households/${id}`, { method: 'PATCH', body: { name } }),
  remove: (id: string) => api<void>(`/households/${id}`, { method: 'DELETE' }),
  members: (id: string) => api<Member[]>(`/households/${id}/members`),
  removeMember: (id: string, userId: string) =>
    api<void>(`/households/${id}/members/${userId}`, { method: 'DELETE' }),
  invite: (id: string) => api<Invitation>(`/households/${id}/invitations`, { method: 'POST' }),
  /** The codes that still let someone join. */
  invitations: (id: string) => api<Invitation[]>(`/households/${id}/invitations`),
  revokeInvitation: (id: string, code: string) =>
    api<void>(`/households/${id}/invitations/${code}`, { method: 'DELETE' }),
  /** The owner hands the household over to another member and stays as a member. */
  transferOwnership: (id: string, userId: string) =>
    api<void>(`/households/${id}/owner`, { method: 'POST', body: { userId } }),
  join: (code: string) => api<Household>('/households/join', { method: 'POST', body: { code } }),
}
