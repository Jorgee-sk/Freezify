import { vi } from 'vitest'

interface RequestInfoForHandler {
  headers: Record<string, string>
  query: URLSearchParams
}

type Handler = (
  body: unknown,
  request: RequestInfoForHandler,
) => { status?: number; body?: unknown; response?: Response }

export interface RecordedCall {
  route: string
  body: unknown
  headers: Record<string, string>
  query: URLSearchParams
}

/**
 * Replaces `fetch` with a router keyed by "METHOD /path" (path relative to /api/v1, without query string).
 * An unexpected request fails the test instead of silently hanging, except for what every signed-in page asks
 * for: unless a test serves them, event streams stay open and silent, there are no unread notifications and the
 * server has no language model.
 */
export function fakeApi(routes: Record<string, Handler>) {
  const calls: RecordedCall[] = []

  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const [path, queryString = ''] = String(input).replace(/^\/api\/v1/, '').split('?')
    const route = `${init?.method ?? 'GET'} ${path}`
    const headers = (init?.headers ?? {}) as Record<string, string>
    const query = new URLSearchParams(queryString)
    const body =
      typeof init?.body === 'string' ? (JSON.parse(init.body) as unknown) : init?.body instanceof FormData ? init.body : undefined
    calls.push({ route, body, headers, query })

    const handler = routes[route]
    if (!handler && route.endsWith('/events')) return eventStream().response
    if (!handler && route === 'GET /notifications/unread-count') return Response.json({ count: 0 })
    // Unless a test says otherwise, the server has no language model.
    if (!handler && route === 'GET /ai') return Response.json({ enabled: false })
    if (!handler) throw new Error(`Unexpected request: ${route}`)
    const result = handler(body, { headers, query })
    if (result.response) return result.response
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

/** A server-sent event response that the test drives: push events into it, or close it. */
export function eventStream() {
  let controller!: ReadableStreamDefaultController<Uint8Array>
  const body = new ReadableStream<Uint8Array>({
    start(streamController) {
      controller = streamController
    },
  })
  const encoder = new TextEncoder()
  return {
    response: new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } }),
    /** Writes raw text, to test events that arrive split across chunks. */
    write: (text: string) => controller.enqueue(encoder.encode(text)),
    send: (name: string) => controller.enqueue(encoder.encode(`event:${name}\ndata:{}\n\n`)),
    close: () => controller.close(),
  }
}
