const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'
/** Says there may be a session to resume after a reload. Only a hint: it holds no secret. */
const SESSION_HINT_KEY = 'freezify.session'
/** Where earlier versions kept the refresh token. It is handed over to the cookie once and removed. */
const LEGACY_REFRESH_TOKEN_KEY = 'freezify.refreshToken'
/**
 * Asks the backend to keep the refresh token in an HttpOnly cookie, out of reach of any script, instead of
 * returning it. The backend also refuses to use that cookie without this header, which a page on another site
 * cannot send.
 */
const SESSION_HEADER = { 'X-Freezify-Session': 'cookie' }

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

interface Tokens {
  accessToken: string
}

// The access token lives only in memory; the refresh token lives in a cookie this code cannot read.
let accessToken: string | null = null
let refreshInFlight: Promise<boolean> | null = null
let onSessionExpired: (() => void) | null = null

export const session = {
  store(tokens: Tokens) {
    accessToken = tokens.accessToken
    localStorage.setItem(SESSION_HINT_KEY, '1')
    localStorage.removeItem(LEGACY_REFRESH_TOKEN_KEY)
  },
  clear() {
    accessToken = null
    localStorage.removeItem(SESSION_HINT_KEY)
    localStorage.removeItem(LEGACY_REFRESH_TOKEN_KEY)
  },
  /** Whether a session may be waiting to be resumed: the cookie itself cannot be read to know for sure. */
  mayResume(): boolean {
    return localStorage.getItem(SESSION_HINT_KEY) !== null || localStorage.getItem(LEGACY_REFRESH_TOKEN_KEY) !== null
  },
  /** Called when the session can no longer be renewed and the user must sign in again. */
  onExpired(handler: (() => void) | null) {
    onSessionExpired = handler
  },
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'
  /** Sent as JSON, except a `FormData` (a file), which the browser encodes itself. */
  body?: unknown
  /** `false` for the endpoints that are called without a session. */
  authenticated?: boolean
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, authenticated = true } = options

  const send = () => {
    const headers: Record<string, string> = { Accept: 'application/json', ...SESSION_HEADER }
    const form = body instanceof FormData
    if (body !== undefined && !form) headers['Content-Type'] = 'application/json'
    if (authenticated && accessToken) headers.Authorization = `Bearer ${accessToken}`
    return fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : form ? body : JSON.stringify(body),
    })
  }

  // After a page reload only the cookie is left: renew first instead of provoking a 401.
  if (authenticated && !accessToken && session.mayResume()) {
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
 * Opens a long-lived response (server-sent events) with the same session handling as `api`.
 * `EventSource` cannot be used because it cannot send the Authorization header.
 */
export async function openStream(path: string, signal: AbortSignal): Promise<Response> {
  const send = () => {
    const headers: Record<string, string> = { Accept: 'text/event-stream' }
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`
    return fetch(`${API_BASE}${path}`, { headers, signal })
  }

  if (!accessToken && session.mayResume()) {
    await renewSession()
  }
  let response = await send()
  if (response.status === 401 && (await renewSession())) {
    response = await send()
  }
  if (!response.ok) throw await toApiError(response)
  return response
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
  if (!session.mayResume()) return false
  // A token kept by an earlier version is handed over once; the answer puts it in the cookie.
  const legacy = localStorage.getItem(LEGACY_REFRESH_TOKEN_KEY)
  const response = await fetch(`${API_BASE}/auth/refresh`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      ...SESSION_HEADER,
      ...(legacy ? { 'Content-Type': 'application/json' } : {}),
    },
    body: legacy ? JSON.stringify({ refreshToken: legacy }) : undefined,
    // The cookie goes with the request; other tabs see the rotated one at once.
    credentials: 'same-origin',
  })
  if (!response.ok) return false
  session.store((await response.json()) as Tokens)
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
