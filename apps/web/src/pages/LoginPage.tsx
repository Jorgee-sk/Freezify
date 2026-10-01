import { useState } from 'react'
import type { FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ErrorMessage } from '../components/ErrorMessage'

export function LoginPage() {
  const { t } = useTranslation()
  const { login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await login(email, password)
    } catch (failure) {
      setError(failure)
      setSubmitting(false)
    }
  }

  return (
    <section className="card auth-card">
      <h1>{t('auth.loginTitle')}</h1>
      <p className="muted">{t('app.tagline')}</p>
      <form onSubmit={(event) => void submit(event)}>
        <label>
          {t('auth.email')}
          <input
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </label>
        <label>
          {t('auth.password')}
          <input
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </label>
        <ErrorMessage error={error} />
        <button type="submit" className="button primary" disabled={submitting}>
          {t('auth.login')}
        </button>
      </form>
      <p className="muted">
        {t('auth.noAccount')} <Link to="/register">{t('auth.goRegister')}</Link>
      </p>
    </section>
  )
}
