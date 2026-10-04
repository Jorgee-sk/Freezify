import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { PhotoCandidate } from '../api/scans'
import { fakeApi, problem } from '../test/fakeApi'
import { ANA, CASA, renderApp, signedIn } from '../test/renderApp'

const IDENTIFY = 'POST /households/h1/scans/food'

function candidate(name: string, confidence: number, overrides: Partial<PhotoCandidate> = {}): PhotoCandidate {
  return {
    foodId: `f-${name}`,
    name,
    confidence,
    category: 'VEGETABLES',
    defaultUnit: 'UNIT',
    defaultStorage: 'REFRIGERATOR',
    ...overrides,
  }
}

function server(routes: Parameters<typeof fakeApi>[0] = {}) {
  signedIn()
  return fakeApi({
    'GET /users/me': () => ({ body: ANA }),
    'GET /households/h1': () => ({ body: CASA }),
    'GET /households/h1/inventory': () => ({ body: { items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 } }),
    'GET /households/h1/inventory/recent': () => ({ body: [] }),
    'GET /households/h1/inventory/consume-first': () => ({
      body: { counts: { EXPIRED: 0, TODAY: 0, URGENT: 0, SOON: 0, UPCOMING: 0, OK: 0, NO_DATE: 0 }, items: [] },
    }),
    'GET /ai': () => ({ body: { enabled: true } }),
    ...routes,
  })
}

function form() {
  return within(screen.getByRole('region', { name: 'Nuevo alimento' }))
}

const PHOTO = new File([new Uint8Array([0xff, 0xd8, 0xff, 0xe0])], 'tomate.jpg', { type: 'image/jpeg' })

afterEach(() => vi.unstubAllGlobals())

describe('identifying a food from a photo', () => {
  it('offers candidates and fills in the one the person picks', async () => {
    const api = server({
      [IDENTIFY]: () => ({
        body: [
          candidate('Tomate', 0.81),
          candidate('Pimiento rojo', 0.12),
          candidate('Caqui', 0.07, { foodId: null, category: 'OTHER', defaultUnit: null, defaultStorage: null }),
        ],
      }),
    })
    const user = userEvent.setup()
    renderApp('/households/h1')
    await user.click(await screen.findByRole('button', { name: 'Añadir alimento' }))

    expect(form().getByText(/La foto se envía al servicio de IA/)).toBeInTheDocument()
    await user.upload(await form().findByLabelText('Identificar por foto'), PHOTO)

    const candidates = within(await form().findByRole('list', { name: '¿Qué es?' }))
    expect(candidates.getAllByRole('button').map((button) => button.textContent)).toEqual([
      'Tomate — 81 %',
      'Pimiento rojo — 12 %',
      'Caqui — 7 %',
    ])
    expect(api.last(IDENTIFY)?.query.get('lang')).toBe('es')
    expect((api.last(IDENTIFY)!.body as FormData).get('image')).toBeInstanceOf(Blob)
    // Nothing is filled in until a candidate is picked.
    expect(form().getByLabelText('Alimento')).toHaveValue('')

    await user.click(candidates.getByRole('button', { name: 'Tomate — 81 %' }))
    expect(form().getByLabelText('Alimento')).toHaveValue('Tomate')
    expect(form().getByLabelText('Ubicación')).toHaveValue('REFRIGERATOR')

    await user.click(candidates.getByRole('button', { name: 'Caqui — 7 %' }))
    expect(form().getByLabelText('Alimento')).toHaveValue('Caqui')
  })

  it('says so when the model is not sure, or sees no food', async () => {
    let answer: PhotoCandidate[] = [candidate('Pera', 0.4), candidate('Manzana', 0.35)]
    server({ [IDENTIFY]: () => ({ body: answer }) })
    const user = userEvent.setup()
    renderApp('/households/h1')
    await user.click(await screen.findByRole('button', { name: 'Añadir alimento' }))

    await user.upload(await form().findByLabelText('Identificar por foto'), PHOTO)
    expect(await form().findByRole('list', { name: 'No estamos seguros. ¿Es alguno de estos?' })).toBeInTheDocument()

    answer = []
    await user.upload(form().getByLabelText('Identificar por foto'), PHOTO)
    expect(await form().findByText(/No hemos reconocido ningún alimento/)).toBeInTheDocument()
  })

  it('explains why a photo was not read', async () => {
    server({ [IDENTIFY]: () => problem(429, 'AI_LIMIT_REACHED') })
    const user = userEvent.setup()
    renderApp('/households/h1')
    await user.click(await screen.findByRole('button', { name: 'Añadir alimento' }))

    await user.upload(await form().findByLabelText('Identificar por foto'), PHOTO)

    expect(await form().findByRole('alert')).toHaveTextContent('Has usado todas las peticiones de IA de hoy')
  })

  it('is not offered without a model', async () => {
    server({ 'GET /ai': () => ({ body: { enabled: false } }) })
    const user = userEvent.setup()
    renderApp('/households/h1')
    await user.click(await screen.findByRole('button', { name: 'Añadir alimento' }))

    await waitFor(() => expect(form().getByLabelText('Alimento')).toBeInTheDocument())
    expect(form().queryByLabelText('Identificar por foto')).not.toBeInTheDocument()
  })
})
