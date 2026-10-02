import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { notificationsApi } from '../api/notifications'
import type { AppNotification } from '../api/notifications'
import { queryKeys } from '../api/queryKeys'
import { ErrorMessage } from '../components/ErrorMessage'
import { formatDay } from '../inventory/format'
import { itemSentence, notificationTitle } from '../notifications/wording'

export function NotificationsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)

  const notifications = useQuery({
    queryKey: queryKeys.notificationList(page),
    queryFn: () => notificationsApi.list(page),
  })

  const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications })
  const markRead = useMutation({ mutationFn: notificationsApi.markRead, onSuccess: refresh })
  const markAllRead = useMutation({ mutationFn: notificationsApi.markAllRead, onSuccess: refresh })

  const items = notifications.data?.items ?? []

  return (
    <>
      <Link to="/" className="back-link">
        ← {t('households.back')}
      </Link>
      <div className="title-row">
        <h1 id="notifications-title">{t('notifications.title')}</h1>
        <Link to="/notifications/preferences" className="button push-right">
          {t('notifications.preferences')}
        </Link>
        {items.some((notification) => !notification.read) && (
          <button type="button" className="button" disabled={markAllRead.isPending} onClick={() => markAllRead.mutate()}>
            {t('notifications.markAllRead')}
          </button>
        )}
      </div>

      {notifications.isPending && <p className="muted">{t('app.loading')}</p>}
      <ErrorMessage error={notifications.error ?? markAllRead.error} />
      {notifications.data && items.length === 0 && <p className="card muted">{t('notifications.empty')}</p>}

      <ul className="notification-list" aria-labelledby="notifications-title">
        {items.map((notification) => (
          <li key={notification.id}>
            <NotificationCard
              notification={notification}
              onOpen={() => {
                if (!notification.read) markRead.mutate(notification.id)
              }}
            />
          </li>
        ))}
      </ul>

      {notifications.data && notifications.data.totalPages > 1 && (
        <nav className="pagination">
          <button type="button" className="button" disabled={page === 0} onClick={() => setPage(page - 1)}>
            {t('inventory.previous')}
          </button>
          <span className="muted">
            {t('inventory.pageOf', { page: page + 1, total: notifications.data.totalPages })}
          </span>
          <button
            type="button"
            className="button"
            disabled={page + 1 >= notifications.data.totalPages}
            onClick={() => setPage(page + 1)}
          >
            {t('inventory.next')}
          </button>
        </nav>
      )}
    </>
  )
}

/** Opening a notification leads to the inventory it talks about, which is where something can be done. */
function NotificationCard({ notification, onOpen }: { notification: AppNotification; onOpen: () => void }) {
  const { t } = useTranslation()
  const hidden = notification.itemCount - notification.items.length

  return (
    <Link
      to={`/households/${notification.householdId}`}
      className={`card notification ${notification.read ? '' : 'notification-unread'}`}
      onClick={onOpen}
    >
      <span className="notification-title">
        <strong>{notificationTitle(notification)}</strong>
        {!notification.read && <span className="badge badge-owner">{t('notifications.new')}</span>}
      </span>
      {notification.itemCount > 1 && (
        <ul>
          {notification.items.map((item) => (
            <li key={`${item.name}-${item.expirationDate}`}>{itemSentence(item)}</li>
          ))}
          {hidden > 0 && <li className="muted">{t('consumeFirst.more', { count: hidden })}</li>}
        </ul>
      )}
      <span className="muted small">
        {notification.householdName} · {formatDay(notification.day)}
      </span>
    </Link>
  )
}
