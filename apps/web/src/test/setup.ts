import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeEach } from 'vitest'
import i18n from '../i18n'
import { session } from '../api/client'

beforeEach(async () => {
  await i18n.changeLanguage('es')
})

afterEach(() => {
  cleanup()
  session.clear()
  session.onExpired(null)
  localStorage.clear()
})
