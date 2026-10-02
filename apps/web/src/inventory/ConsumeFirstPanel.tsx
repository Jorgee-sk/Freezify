import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { inventoryApi } from '../api/inventory'
import { queryKeys } from '../api/queryKeys'
import { formatQuantity } from './format'
import { PriorityBadge } from './PriorityBadge'

const VISIBLE_ITEMS = 5

/**
 * "What should I eat first?", answered before anything else on the screen and whatever filter is active
 * below. It is absent when nothing needs attention.
 */
export function ConsumeFirstPanel({ householdId }: { householdId: string }) {
  const { t } = useTranslation()
  const consumeFirst = useQuery({
    queryKey: queryKeys.consumeFirst(householdId),
    queryFn: () => inventoryApi.consumeFirst(householdId),
  })

  const data = consumeFirst.data
  if (!data) return null
  const { counts, items } = data
  const total = counts.EXPIRED + counts.TODAY + counts.URGENT + counts.SOON
  if (total === 0) return null
  const hidden = total - Math.min(items.length, VISIBLE_ITEMS)

  return (
    <section className="card consume-first" aria-labelledby="consume-first-title">
      <h2 id="consume-first-title">🔥 {t('consumeFirst.title')}</h2>
      <p className="muted small">{t('consumeFirst.count', { count: total })}</p>
      <ul>
        {items.slice(0, VISIBLE_ITEMS).map((item) => (
          <li key={item.id}>
            <span>
              <strong>{item.name}</strong> <span className="muted small">{formatQuantity(item.quantity)}</span>
            </span>
            <PriorityBadge item={item} />
          </li>
        ))}
      </ul>
      {hidden > 0 && <p className="muted small">{t('consumeFirst.more', { count: hidden })}</p>}
    </section>
  )
}
