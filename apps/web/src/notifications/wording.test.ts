import { describe, expect, it } from 'vitest'
import type { AppNotification, NotifiedItem } from '../api/notifications'
import i18n from '../i18n'
import { itemSentence, notificationTitle } from './wording'

function food(daysUntilExpiration: number, estimated = false): NotifiedItem {
  return { name: 'Leche', expirationDate: '2026-10-03', estimated, daysUntilExpiration }
}

function notification(items: NotifiedItem[], itemCount = items.length): AppNotification {
  return {
    id: 'n1',
    type: 'EXPIRATION',
    householdId: 'h1',
    householdName: 'Casa',
    day: '2026-10-02',
    itemCount,
    items,
    createdAt: '2026-10-02T07:00:00Z',
    read: false,
  }
}

describe('itemSentence', () => {
  it('says how long is left, or how long ago the date passed', () => {
    expect(itemSentence(food(3))).toBe('Leche: quedan 3 días para su fecha de caducidad')
    expect(itemSentence(food(1))).toBe('Leche: queda 1 día para su fecha de caducidad')
    expect(itemSentence(food(0))).toBe('Leche: su fecha de caducidad es hoy')
    expect(itemSentence(food(-1))).toBe('Leche: su fecha de caducidad pasó hace 1 día')
    expect(itemSentence(food(-4))).toBe('Leche: su fecha de caducidad pasó hace 4 días')
  })

  it('reads right whatever the number of the name', () => {
    // Not "Huevos caduca hoy": no verb agrees with the name.
    expect(itemSentence({ ...food(0), name: 'Huevos' })).toBe('Huevos: su fecha de caducidad es hoy')
    expect(itemSentence({ ...food(2), name: 'Huevos' })).toBe('Huevos: quedan 2 días para su fecha de caducidad')
  })

  it('never presents an estimate as a fact', () => {
    for (const days of [3, 1, 0, -1, -4]) {
      expect(itemSentence(food(days, true))).toContain('(fecha estimada)')
    }
    expect(itemSentence(food(0, true))).toBe('Leche: su fecha de caducidad es probablemente hoy (fecha estimada)')
  })

  it('follows the language of the app', async () => {
    await i18n.changeLanguage('en')

    expect(itemSentence(food(2))).toBe('Leche: 2 days left before the expiry date')
    expect(itemSentence(food(-1, true))).toBe('Leche: the expiry date was probably 1 day ago (estimated date)')
  })
})

describe('notificationTitle', () => {
  it('names the food when there is only one', () => {
    expect(notificationTitle(notification([food(2)]))).toBe('Leche: quedan 2 días para su fecha de caducidad')
  })

  it('counts the food when there are several, including what is not listed', () => {
    expect(notificationTitle(notification([food(2), food(3)], 7))).toBe(
      'Tienes 7 alimentos que deberías consumir pronto',
    )
  })
})
