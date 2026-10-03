import { ApiError, openStream } from '../api/client'

/** `reconnected` is not sent by the server: it says that anything may have been missed while disconnected. */
export type HouseholdEvent = 'inventory-changed' | 'meal-plan-changed' | 'shopping-list-changed' | 'reconnected'

interface Options {
  /** First wait before reconnecting; doubles after every failed attempt. */
  retryDelayMs?: number
  maxRetryDelayMs?: number
  /** The server sends a heartbeat every 25 s, so a longer silence means the connection is dead. */
  idleTimeoutMs?: number
}

/**
 * Listens to what happens in a household and calls `onEvent` for each event, reconnecting for as long as the
 * subscription lives. Events carry no data: the listener is expected to fetch again.
 *
 * After a reconnection `onEvent` is called once with `reconnected`, because anything may have been missed
 * while disconnected.
 *
 * @returns a function that ends the subscription
 */
export function subscribeToHousehold(
  householdId: string,
  onEvent: (event: HouseholdEvent) => void,
  { retryDelayMs = 1_000, maxRetryDelayMs = 30_000, idleTimeoutMs = 60_000 }: Options = {},
): () => void {
  const controller = new AbortController()
  const { signal } = controller

  async function run() {
    let delay = retryDelayMs
    let connectedBefore = false
    while (!signal.aborted) {
      try {
        const response = await openStream(`/households/${householdId}/events`, signal)
        if (connectedBefore) onEvent('reconnected')
        connectedBefore = true
        delay = retryDelayMs
        await readEvents(response, signal, idleTimeoutMs, (name) => {
          if (name === 'inventory-changed' || name === 'meal-plan-changed' || name === 'shopping-list-changed') {
            onEvent(name)
          }
        })
      } catch (error) {
        // No longer a member, or the household is gone: asking again will not change the answer.
        if (error instanceof ApiError && (error.status === 403 || error.status === 404)) return
      }
      if (signal.aborted) return
      await sleep(delay, signal)
      delay = Math.min(delay * 2, maxRetryDelayMs)
    }
  }

  void run()
  return () => controller.abort()
}

/** Reads a `text/event-stream` body until it ends, reporting the name of each event. */
export async function readEvents(
  response: Response,
  signal: AbortSignal,
  idleTimeoutMs: number,
  onEvent: (name: string) => void,
): Promise<void> {
  if (!response.body) return
  const reader = response.body.getReader()
  const stop = () => void reader.cancel().catch(() => undefined)
  signal.addEventListener('abort', stop)
  const decoder = new TextDecoder()
  let pending = ''
  let idle: ReturnType<typeof setTimeout> | undefined
  try {
    for (;;) {
      clearTimeout(idle)
      idle = setTimeout(stop, idleTimeoutMs)
      const { done, value } = await reader.read()
      if (done) return
      pending += decoder.decode(value, { stream: true })
      // A chunk can end in the middle of a line; the unfinished part waits for the next chunk.
      const lines = pending.split('\n')
      pending = lines.pop() ?? ''
      for (const line of lines) {
        if (line.startsWith('event:')) onEvent(line.slice('event:'.length).trim())
      }
    }
  } finally {
    clearTimeout(idle)
    signal.removeEventListener('abort', stop)
  }
}

function sleep(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const timer = setTimeout(done, ms)
    function done() {
      clearTimeout(timer)
      signal.removeEventListener('abort', done)
      resolve()
    }
    signal.addEventListener('abort', done)
  })
}
