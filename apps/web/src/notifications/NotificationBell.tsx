import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { notificationsApi } from '../api/notifications'
import { queryKeys } from '../api/queryKeys'

const REFRESH_EVERY_MS = 5 * 60_000

/** The way into the notifications, with how many are waiting to be read. */
export function NotificationBell() {
  const { t } = useTranslation()
  const unread = useQuery({
    queryKey: queryKeys.unreadNotifications,
    queryFn: notificationsApi.unreadCount,
    refetchInterval: REFRESH_EVERY_MS,
  })
  const count = unread.data?.count ?? 0

  return (
    <Link
      to="/notifications"
      className="bell"
      aria-label={count > 0 ? t('notifications.bellUnread', { count }) : t('notifications.title')}
    >
      <span aria-hidden="true">🔔</span>
      {count > 0 && (
        <span className="bell-count" aria-hidden="true">
          {count > 9 ? '9+' : count}
        </span>
      )}
    </Link>
  )
}
