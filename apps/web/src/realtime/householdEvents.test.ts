import { afterEach, describe, expect, it, vi } from 'vitest'
import { session } from '../api/client'
import { eventStream, fakeApi, problem } from '../test/fakeApi'
import { readEvents, subscribeToHousehold } from './householdEvents'

const EVENTS = 'GET /households/h1/events'
const FAST = { retryDelayMs: 5, maxRetryDelayMs: 20 }

afterEach(() => vi.unstubAllGlobals())

describe('readEvents', () => {
  it('reports each event, even when it arrives split across chunks', async () => {
    const stream = eventStream()
    const names: string[] = []
    const reading = readEvents(stream.response, new AbortController().signal, 1_000, (name) => names.push(name))

    stream.write('event:connected\ndata:{}\n\neve')
    stream.write('nt:inventory-changed\ndata:{}\n\n')
    stream.write(':keepalive\n\n')
    stream.write('event: inventory-changed\r\ndata:{}\r\n\r\n')
    stream.close()
    await reading

    expect(names).toEqual(['connected', 'inventory-changed', 'inventory-changed'])
  })

  it('gives up on a connection that has gone silent', async () => {
    const stream = eventStream()

    await expect(readEvents(stream.response, new AbortController().signal, 20, () => {})).resolves.toBeUndefined()
  })

  it('stops reading when the subscription ends', async () => {
    const stream = eventStream()
    const controller = new AbortController()
    const reading = readEvents(stream.response, controller.signal, 60_000, () => {})

    controller.abort()

    await expect(reading).resolves.toBeUndefined()
  })
})

describe('subscribeToHousehold', () => {
  it('sends the session token and reports inventory changes', async () => {
    session.store({ accessToken: 'access-1' })
    const stream = eventStream()
    const api = fakeApi({ [EVENTS]: () => ({ response: stream.response }) })
    const onEvent = vi.fn()

    const unsubscribe = subscribeToHousehold('h1', onEvent, FAST)
    await vi.waitFor(() => expect(api.count(EVENTS)).toBe(1))
    stream.send('connected')
    stream.send('inventory-changed')

    await vi.waitFor(() => expect(onEvent).toHaveBeenCalledTimes(1))
    expect(onEvent).toHaveBeenCalledWith('inventory-changed')
    expect(api.last(EVENTS)!.headers.Authorization).toBe('Bearer access-1')
    expect(api.last(EVENTS)!.headers.Accept).toBe('text/event-stream')
    unsubscribe()
  })

  it('reconnects when the connection drops and reports that something may have been missed', async () => {
    session.store({ accessToken: 'access-1' })
    const first = eventStream()
    const second = eventStream()
    const streams = [first, second]
    const api = fakeApi({ [EVENTS]: () => ({ response: streams.shift()!.response }) })
    const onEvent = vi.fn()

    const unsubscribe = subscribeToHousehold('h1', onEvent, FAST)
    await vi.waitFor(() => expect(api.count(EVENTS)).toBe(1))
    expect(onEvent).not.toHaveBeenCalled()
    first.close()

    await vi.waitFor(() => expect(api.count(EVENTS)).toBe(2))
    await vi.waitFor(() => expect(onEvent).toHaveBeenCalledTimes(1))
    second.send('inventory-changed')
    await vi.waitFor(() => expect(onEvent).toHaveBeenCalledTimes(2))
    unsubscribe()
  })

  it('keeps trying while the server cannot be reached', async () => {
    session.store({ accessToken: 'access-1' })
    let attempts = 0
    const stream = eventStream()
    const api = fakeApi({
      [EVENTS]: () => {
        attempts += 1
        return attempts < 3 ? problem(503, 'UNAVAILABLE') : { response: stream.response }
      },
    })

    const unsubscribe = subscribeToHousehold('h1', () => {}, FAST)

    await vi.waitFor(() => expect(api.count(EVENTS)).toBe(3))
    unsubscribe()
  })

  it('renews an expired session before listening', async () => {
    session.store({ accessToken: 'expired' })
    const stream = eventStream()
    const api = fakeApi({
      [EVENTS]: (_, { headers }) =>
        headers.Authorization === 'Bearer access-2' ? { response: stream.response } : problem(401, 'UNAUTHORIZED'),
      'POST /auth/refresh': () => ({ body: { accessToken: 'access-2', refreshToken: 'refresh-2' } }),
    })

    const unsubscribe = subscribeToHousehold('h1', () => {}, FAST)

    await vi.waitFor(() => expect(api.count(EVENTS)).toBe(2))
    expect(api.count('POST /auth/refresh')).toBe(1)
    unsubscribe()
  })

  it('stops for good when the user no longer belongs to the household', async () => {
    session.store({ accessToken: 'access-1' })
    const api = fakeApi({ [EVENTS]: () => problem(404, 'HOUSEHOLD_NOT_FOUND') })

    subscribeToHousehold('h1', () => {}, FAST)
    await vi.waitFor(() => expect(api.count(EVENTS)).toBe(1))
    await new Promise((resolve) => setTimeout(resolve, 60))

    expect(api.count(EVENTS)).toBe(1)
  })

  it('does not reconnect after unsubscribing', async () => {
    session.store({ accessToken: 'access-1' })
    const stream = eventStream()
    const api = fakeApi({ [EVENTS]: () => ({ response: stream.response }) })

    const unsubscribe = subscribeToHousehold('h1', () => {}, FAST)
    await vi.waitFor(() => expect(api.count(EVENTS)).toBe(1))
    unsubscribe()
    await new Promise((resolve) => setTimeout(resolve, 60))

    expect(api.count(EVENTS)).toBe(1)
  })
})
