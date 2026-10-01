import { vi } from 'vitest'

type Handler = (body: unknown, request: { headers: Record<string, string> }) => { status?: number; body?: unknown }

export interface RecordedCall {
  route: string
  body: unknown
  headers: Record<string, string>
}

/**
 * Replaces `fetch` with a router keyed by "METHOD /path" (path relative to /api/v1).
 * An unexpected request fails the test instead of silently hanging.
 */
export function fakeApi(routes: Record<string, Handler>) {
  const calls: RecordedCall[] = []

  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input).replace(/^\/api\/v1/, '')
    const route = `${init?.method ?? 'GET'} ${url}`
    const headers = (init?.headers ?? {}) as Record<string, string>
    const body = typeof init?.body === 'string' ? (JSON.parse(init.body) as unknown) : undefined
    calls.push({ route, body, headers })

    const handler = routes[route]
    if (!handler) throw new Error(`Unexpected request: ${route}`)
    const result = handler(body, { headers })
    const status = result.status ?? 200
    return new Response(status === 204 ? null : JSON.stringify(result.body ?? {}), {
      status,
      headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
    })
  })

  vi.stubGlobal('fetch', fetchMock)
  return {
    calls,
    count: (route: string) => calls.filter((call) => call.route === route).length,
    last: (route: string) => calls.filter((call) => call.route === route).at(-1),
  }
}

export function problem(status: number, code: string) {
  return { status, body: { status, code, detail: code } }
}
