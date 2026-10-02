import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { FOOD_CATEGORIES } from '../api/inventory'
import type { FoodCategory } from '../api/inventory'
import { NOTIFICATION_FREQUENCIES, NOTIFICATION_THRESHOLDS, notificationsApi } from '../api/notifications'
import type { NotificationFrequency, NotificationPreferences, NotificationThreshold } from '../api/notifications'
import { queryKeys } from '../api/queryKeys'
import { ErrorMessage } from '../components/ErrorMessage'

const HOURS = Array.from({ length: 24 }, (_, hour) => hour)

export function NotificationPreferencesPage() {
  const { t } = useTranslation()
  const preferences = useQuery({
    queryKey: queryKeys.notificationPreferences,
    queryFn: notificationsApi.preferences,
  })

  return (
    <>
      <Link to="/notifications" className="back-link">
        ← {t('notifications.backToList')}
      </Link>
      <h1>{t('notifications.preferencesTitle')}</h1>
      {preferences.isPending && <p className="muted">{t('app.loading')}</p>}
      <ErrorMessage error={preferences.error} />
      {/* The form starts from what the server has; it is only rendered once that is known. */}
      {preferences.data && <PreferencesForm initial={preferences.data} />}
    </>
  )
}

function PreferencesForm({ initial }: { initial: NotificationPreferences }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState(initial)
  const [saved, setSaved] = useState(false)

  const save = useMutation({
    mutationFn: notificationsApi.updatePreferences,
    onSuccess: (stored) => {
      queryClient.setQueryData(queryKeys.notificationPreferences, stored)
      setSaved(true)
    },
  })

  function change(changes: Partial<NotificationPreferences>) {
    setDraft({ ...draft, ...changes })
    setSaved(false)
  }

  function toggleCategory(category: FoodCategory, wanted: boolean) {
    const muted = draft.mutedCategories.filter((other) => other !== category)
    change({ mutedCategories: wanted ? muted : [...muted, category] })
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    save.mutate(draft)
  }

  const off = !draft.expirationAlerts

  return (
    <form className="card preferences-form" onSubmit={submit}>
      <label className="check">
        <input
          type="checkbox"
          checked={draft.expirationAlerts}
          onChange={(event) => change({ expirationAlerts: event.target.checked })}
        />
        {t('notifications.pref.enabled')}
      </label>
      <p className="muted small">{t('notifications.pref.antiSpam')}</p>

      <div className="field-row">
        <label>
          {t('notifications.pref.hour')}
          <select
            value={draft.deliveryHour}
            disabled={off}
            onChange={(event) => change({ deliveryHour: Number(event.target.value) })}
          >
            {HOURS.map((hour) => (
              <option key={hour} value={hour}>
                {`${String(hour).padStart(2, '0')}:00`}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t('notifications.pref.frequency')}
          <select
            value={draft.frequency}
            disabled={off}
            onChange={(event) => change({ frequency: event.target.value as NotificationFrequency })}
          >
            {NOTIFICATION_FREQUENCIES.map((option) => (
              <option key={option} value={option}>
                {t(`notifications.pref.frequencyOption.${option}`)}
              </option>
            ))}
          </select>
        </label>
      </div>
      <p className="muted small">{t('notifications.pref.hourHelp')}</p>

      <label>
        {t('notifications.pref.threshold')}
        <select
          value={draft.threshold}
          disabled={off}
          onChange={(event) => change({ threshold: event.target.value as NotificationThreshold })}
        >
          {NOTIFICATION_THRESHOLDS.map((option) => (
            <option key={option} value={option}>
              {t(`notifications.pref.thresholdOption.${option}`)}
            </option>
          ))}
        </select>
      </label>

      <fieldset disabled={off}>
        <legend>{t('notifications.pref.categories')}</legend>
        <div className="check-grid">
          {FOOD_CATEGORIES.map((category) => (
            <label key={category} className="check">
              <input
                type="checkbox"
                checked={!draft.mutedCategories.includes(category)}
                onChange={(event) => toggleCategory(category, event.target.checked)}
              />
              {t(`categories.${category}`)}
            </label>
          ))}
        </div>
      </fieldset>

      <ErrorMessage error={save.error} />
      <div className="button-row">
        <button type="submit" className="button primary" disabled={save.isPending}>
          {t('inventory.save')}
        </button>
        {saved && (
          <span role="status" className="muted">
            {t('notifications.pref.saved')}
          </span>
        )}
      </div>
    </form>
  )
}
