import { useTranslation } from 'react-i18next'
import { Link, Outlet } from 'react-router-dom'
import type { Locale } from '../api/endpoints'
import { useAuth } from '../auth/useAuth'
import { LOCALES, currentLocale } from '../i18n'
import { NotificationBell } from '../notifications/NotificationBell'

export function Layout() {
  const { t } = useTranslation()
  const auth = useAuth()

  return (
    <>
      <header className="topbar">
        <Link to="/" className="brand">
          <img src="/favicon.svg" alt="" width="28" height="28" />
          {t('app.name')}
        </Link>
        <div className="topbar-actions">
          <select
            aria-label={t('nav.language')}
            value={currentLocale()}
            onChange={(event) => void auth.changeLocale(event.target.value as Locale)}
          >
            {LOCALES.map((locale) => (
              <option key={locale} value={locale}>
                {locale.toUpperCase()}
              </option>
            ))}
          </select>
          {auth.status === 'authenticated' && (
            <>
              <NotificationBell />
              <span className="muted user-name">{auth.user.displayName}</span>
              <button type="button" className="button ghost" onClick={() => void auth.logout()}>
                {t('nav.logout')}
              </button>
            </>
          )}
        </div>
      </header>
      <main className="page">
        <Outlet />
      </main>
    </>
  )
}
