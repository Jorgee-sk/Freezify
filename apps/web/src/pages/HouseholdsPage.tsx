import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { householdsApi } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import { useAuth } from '../auth/useAuth'
import { ErrorMessage } from '../components/ErrorMessage'

export function HouseholdsPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [code, setCode] = useState('')

  const households = useQuery({ queryKey: queryKeys.households, queryFn: householdsApi.list })

  const create = useMutation({
    mutationFn: householdsApi.create,
    onSuccess: async (household) => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.households })
      void navigate(`/households/${household.id}`)
    },
  })

  const join = useMutation({
    mutationFn: householdsApi.join,
    onSuccess: async (household) => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.households })
      void navigate(`/households/${household.id}`)
    },
  })

  function submitCreate(event: FormEvent) {
    event.preventDefault()
    create.mutate(name)
  }

  function submitJoin(event: FormEvent) {
    event.preventDefault()
    join.mutate(code)
  }

  return (
    <>
      <h1>{t('households.greeting', { name: user?.displayName })}</h1>

      <section aria-labelledby="households-title">
        <h2 id="households-title">{t('households.title')}</h2>
        {households.isPending && <p className="muted">{t('app.loading')}</p>}
        <ErrorMessage error={households.error} />
        {households.data?.length === 0 && <p className="card muted">{t('households.empty')}</p>}
        <ul className="household-list">
          {households.data?.map((household) => (
            <li key={household.id}>
              <Link to={`/households/${household.id}`} className="card household-card">
                <span className="household-name">{household.name}</span>
                <span className="muted">{t('households.members', { count: household.memberCount })}</span>
                <span className={`badge ${household.role === 'OWNER' ? 'badge-owner' : ''}`}>
                  {t(`households.role.${household.role}`)}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <div className="grid-two">
        <section className="card" aria-labelledby="create-title">
          <h2 id="create-title">{t('households.createTitle')}</h2>
          <form onSubmit={submitCreate}>
            <label>
              {t('households.nameLabel')}
              <input
                type="text"
                required
                maxLength={80}
                placeholder={t('households.namePlaceholder')}
                value={name}
                onChange={(event) => setName(event.target.value)}
              />
            </label>
            <ErrorMessage error={create.error} />
            <button type="submit" className="button primary" disabled={create.isPending}>
              {t('households.create')}
            </button>
          </form>
        </section>

        <section className="card" aria-labelledby="join-title">
          <h2 id="join-title">{t('households.joinTitle')}</h2>
          <form onSubmit={submitJoin}>
            <label>
              {t('households.codeLabel')}
              <input
                type="text"
                required
                maxLength={32}
                autoCapitalize="characters"
                autoComplete="off"
                className="code-input"
                value={code}
                onChange={(event) => setCode(event.target.value)}
              />
            </label>
            <ErrorMessage error={join.error} />
            <button type="submit" className="button" disabled={join.isPending}>
              {t('households.join')}
            </button>
          </form>
        </section>
      </div>
    </>
  )
}
