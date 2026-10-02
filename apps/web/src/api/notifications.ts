import { api } from './client'
import type { FoodCategory, Page } from './inventory'

export type NotificationFrequency = 'DAILY' | 'EVERY_THREE_DAYS' | 'WEEKLY'
/** How close to its date a food has to be before it is worth a notification. */
export type NotificationThreshold = 'TODAY' | 'URGENT' | 'SOON'

export const NOTIFICATION_FREQUENCIES: NotificationFrequency[] = ['DAILY', 'EVERY_THREE_DAYS', 'WEEKLY']
export const NOTIFICATION_THRESHOLDS: NotificationThreshold[] = ['TODAY', 'URGENT', 'SOON']

export interface NotifiedItem {
  name: string
  expirationDate: string
  /** Estimated dates must be worded as estimates. */
  estimated: boolean
  /** Counted from the day of the notification; negative when the date had passed. */
  daysUntilExpiration: number
}

/** Facts, not sentences: the wording is the client's, in the user's language. */
export interface AppNotification {
  id: string
  type: 'EXPIRATION'
  householdId: string
  householdName: string
  /** The calendar day the notification is about. */
  day: string
  /** How many items needed attention that day; `items` holds only the most pressing ones. */
  itemCount: number
  items: NotifiedItem[]
  createdAt: string
  read: boolean
}

export interface NotificationPreferences {
  expirationAlerts: boolean
  /** 0–23, on the clock of the application's time zone. */
  deliveryHour: number
  frequency: NotificationFrequency
  threshold: NotificationThreshold
  mutedCategories: FoodCategory[]
}

export const notificationsApi = {
  list: (page: number) => api<Page<AppNotification>>(`/notifications?${new URLSearchParams({ page: String(page) })}`),
  unreadCount: () => api<{ count: number }>('/notifications/unread-count'),
  markRead: (id: string) => api<void>(`/notifications/${id}/read`, { method: 'POST' }),
  markAllRead: () => api<void>('/notifications/read-all', { method: 'POST' }),
  preferences: () => api<NotificationPreferences>('/notifications/preferences'),
  updatePreferences: (preferences: NotificationPreferences) =>
    api<NotificationPreferences>('/notifications/preferences', { method: 'PUT', body: preferences }),
}
