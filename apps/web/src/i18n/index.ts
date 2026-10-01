import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import type { Locale } from '../api/endpoints'
import { ApiError } from '../api/client'
import en from './locales/en.json'
import es from './locales/es.json'

export const LOCALES: Locale[] = ['es', 'en']
const LOCALE_KEY = 'freezify.locale'

function initialLocale(): Locale {
  const stored = localStorage.getItem(LOCALE_KEY)
  if (stored === 'es' || stored === 'en') return stored
  return navigator.language.toLowerCase().startsWith('es') ? 'es' : 'en'
}

void i18n.use(initReactI18next).init({
  resources: { es: { translation: es }, en: { translation: en } },
  lng: initialLocale(),
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
})

export function setLocale(locale: Locale) {
  localStorage.setItem(LOCALE_KEY, locale)
  document.documentElement.lang = locale
  void i18n.changeLanguage(locale)
}

export function currentLocale(): Locale {
  return i18n.language === 'es' ? 'es' : 'en'
}

/** Day/month/year in both languages, as the product targets Spain. */
export function formatDate(iso: string): string {
  const tag = currentLocale() === 'es' ? 'es-ES' : 'en-GB'
  return new Intl.DateTimeFormat(tag, { day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(iso))
}

/** Maps a failure to the i18n key of the message to show. */
export function errorKey(error: unknown): string {
  if (error instanceof ApiError) {
    const key = `errors.${error.code}`
    return i18n.exists(key) ? key : 'errors.generic'
  }
  // fetch rejects with TypeError when the request never got a response.
  return error instanceof TypeError ? 'errors.network' : 'errors.generic'
}

export default i18n
