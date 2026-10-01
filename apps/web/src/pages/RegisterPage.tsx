import { useState } from 'react'
import type { FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ErrorMessage } from '../components/ErrorMessage'

export function RegisterPage() {
  const { t } = useTranslation()
  const { register } = useAuth()
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await register(email, password, displayName)
    } catch (failure) {
      setError(failure)
      setSubmitting(false)
    }
  }

  return (
    <section className="card auth-card">
      <h1>{t('auth.registerTitle')}</h1>
      <p className="muted">{t('app.tagline')}</p>
      <form onSubmit={(event) => void submit(event)}>
        <label>
          {t('auth.displayName')}
          <input
            type="text"
            autoComplete="given-name"
            required
            maxLength={80}
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
          />
        </label>
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
            autoComplete="new-password"
            required
            minLength={8}
            maxLength={72}
            aria-describedby="password-hint"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <small id="password-hint" className="muted">
            {t('auth.passwordHint')}
          </small>
        </label>
        <ErrorMessage error={error} />
        <button type="submit" className="button primary" disabled={submitting}>
          {t('auth.register')}
        </button>
      </form>
      <p className="muted">
        {t('auth.haveAccount')} <Link to="/login">{t('auth.goLogin')}</Link>
      </p>
    </section>
  )
}
