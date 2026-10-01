const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'
const REFRESH_TOKEN_KEY = 'freezify.refreshToken'

export interface FieldError {
  field: string
  message: string
}

/** A failed API call. `code` is the stable identifier the UI translates. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldError[]

  constructor(status: number, code: string, detail: string, fieldErrors: FieldError[] = []) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

interface TokenPair {
  accessToken: string
  refreshToken: string
}

// The access token lives only in memory; the refresh token survives reloads.
let accessToken: string | null = null
let refreshInFlight: Promise<boolean> | null = null
let onSessionExpired: (() => void) | null = null

export const session = {
  store(tokens: TokenPair) {
    accessToken = tokens.accessToken
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
  },
  clear() {
    accessToken = null
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  },
  refreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY)
  },
  /** Called when the session can no longer be renewed and the user must sign in again. */
  onExpired(handler: (() => void) | null) {
    onSessionExpired = handler
  },
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'
  body?: unknown
  /** `false` for the endpoints that are called without a session. */
  authenticated?: boolean
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, authenticated = true } = options

  const send = () => {
    const headers: Record<string, string> = { Accept: 'application/json' }
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    if (authenticated && accessToken) headers.Authorization = `Bearer ${accessToken}`
    return fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  }

  // After a page reload only the refresh token is left: renew first instead of provoking a 401.
  if (authenticated && !accessToken && session.refreshToken()) {
    await renewSession()
  }

  let response = await send()
  if (response.status === 401 && authenticated) {
    if (await renewSession()) {
      response = await send()
    }
    if (response.status === 401) {
      session.clear()
      onSessionExpired?.()
    }
  }

  if (!response.ok) throw await toApiError(response)
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

/**
 * Refresh tokens are single use and the backend revokes the whole session when one is presented twice,
 * so concurrent requests (and other tabs) must share a single refresh.
 */
function renewSession(): Promise<boolean> {
  refreshInFlight ??= withCrossTabLock(refresh).finally(() => {
    refreshInFlight = null
  })
  return refreshInFlight
}

async function refresh(): Promise<boolean> {
  // Read inside the lock: another tab may have rotated the token while this one waited.
  const refreshToken = session.refreshToken()
  if (!refreshToken) return false
  const response = await fetch(`${API_BASE}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })
  if (!response.ok) return false
  session.store((await response.json()) as TokenPair)
  return true
}

function withCrossTabLock<T>(task: () => Promise<T>): Promise<T> {
  if (typeof navigator !== 'undefined' && navigator.locks) {
    return navigator.locks.request('freezify.refresh', task) as Promise<T>
  }
  return task()
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const problem = (await response.json()) as { code?: string; detail?: string; errors?: FieldError[] }
    return new ApiError(response.status, problem.code ?? 'UNKNOWN', problem.detail ?? '', problem.errors ?? [])
  } catch {
    // Not a problem+json body: a proxy error page, or the backend is down.
    return new ApiError(response.status, 'UNKNOWN', response.statusText)
  }
}
