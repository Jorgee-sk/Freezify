import { useTranslation } from 'react-i18next'
import type { InventoryItem } from '../api/inventory'

/**
 * How soon the item should be eaten. Nothing is shown for items without a date or with plenty of time left:
 * a badge on every row would stop meaning anything.
 */
export function PriorityBadge({ item }: { item: InventoryItem }) {
  const { t } = useTranslation()
  const { priority, daysUntilExpiration: days } = item
  if (priority === null || priority === 'OK' || days === null) return null

  // "Expires today" already says how long is left.
  const detail =
    days < 0 ? t('priority.daysAgo', { count: -days }) : days > 0 ? t('priority.daysLeft', { count: days }) : null

  return (
    <span className={`badge priority priority-${priority.toLowerCase()}`}>
      {t(`priority.${priority}`)}
      {detail && ` · ${detail}`}
    </span>
  )
}
