import { describe, expect, it } from 'vitest'
import i18n from '../i18n'
import { compatibleUnits, formatDay, formatQuantity, parseAmount, todayIso } from './format'

describe('formatQuantity', () => {
  it('uses Spanish number format and unit abbreviations', () => {
    expect(formatQuantity({ amount: 500, unit: 'GRAM' })).toBe('500 g')
    expect(formatQuantity({ amount: 1.5, unit: 'KILOGRAM' })).toBe('1,5 kg')
    expect(formatQuantity({ amount: 0.75, unit: 'LITER' })).toBe('0,75 l')
    expect(formatQuantity({ amount: 750, unit: 'MILLILITER' })).toBe('750 ml')
  })

  it('pluralises counted units', () => {
    expect(formatQuantity({ amount: 1, unit: 'UNIT' })).toBe('1 ud')
    expect(formatQuantity({ amount: 6, unit: 'UNIT' })).toBe('6 uds')
  })

  it('follows the language of the app', async () => {
    await i18n.changeLanguage('en')
    expect(formatQuantity({ amount: 1.5, unit: 'KILOGRAM' })).toBe('1.5 kg')
    expect(formatQuantity({ amount: 2, unit: 'UNIT' })).toBe('2 units')
  })
})

describe('formatDay', () => {
  it('shows day/month/year in both languages', async () => {
    expect(formatDay('2026-10-05')).toBe('05/10/2026')
    await i18n.changeLanguage('en')
    expect(formatDay('2026-10-05')).toBe('05/10/2026')
  })

  it('does not shift the first and last day of a year', () => {
    expect(formatDay('2026-01-01')).toBe('01/01/2026')
    expect(formatDay('2026-12-31')).toBe('31/12/2026')
  })
})

describe('parseAmount', () => {
  it('accepts a comma or a point as decimal separator', () => {
    expect(parseAmount('1,5')).toBe(1.5)
    expect(parseAmount(' 0.25 ')).toBe(0.25)
    expect(parseAmount('6')).toBe(6)
  })

  it('rejects anything that is not a positive number', () => {
    for (const text of ['', '  ', '0', '-2', 'abc', '1,2,3']) {
      expect(parseAmount(text)).toBeNull()
    }
  })
})

describe('compatibleUnits', () => {
  it('only offers units of the same dimension', () => {
    expect(compatibleUnits('KILOGRAM')).toEqual(['GRAM', 'KILOGRAM'])
    expect(compatibleUnits('MILLILITER')).toEqual(['MILLILITER', 'LITER'])
    expect(compatibleUnits('UNIT')).toEqual(['UNIT'])
  })
})

describe('todayIso', () => {
  it('uses the local calendar day', () => {
    expect(todayIso(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05')
    expect(todayIso(new Date(2026, 9, 31, 0, 1))).toBe('2026-10-31')
  })
})
