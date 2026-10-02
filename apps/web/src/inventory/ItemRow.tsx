import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { WASTE_REASONS, inventoryApi } from '../api/inventory'
import type { InventoryItem, Unit, WasteReason } from '../api/inventory'
import { queryKeys } from '../api/queryKeys'
import { ErrorMessage } from '../components/ErrorMessage'
import { PriorityBadge } from './PriorityBadge'
import { compatibleUnits, formatDay, formatQuantity, parseAmount } from './format'

interface Props {
  householdId: string
  item: InventoryItem
  onEdit: (item: InventoryItem) => void
}

type TakeOut = 'consume' | 'discard'

export function ItemRow({ householdId, item, onEdit }: Props) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [takeOut, setTakeOut] = useState<TakeOut | null>(null)
  const [amount, setAmount] = useState('')
  const [unit, setUnit] = useState<Unit>(item.quantity.unit)
  const [reason, setReason] = useState<WasteReason>('EXPIRED')
  const [amountInvalid, setAmountInvalid] = useState(false)

  const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.inventory(householdId) })
  const change = useMutation({
    mutationFn: (action: () => Promise<unknown>) => action(),
    onSuccess: async () => {
      setTakeOut(null)
      await refresh()
    },
  })

  const active = item.status === 'AVAILABLE' || item.status === 'OPENED' || item.status === 'EXPIRED'

  function start(kind: TakeOut) {
    change.reset()
    setAmountInvalid(false)
    // Everything that is left is the common case; a smaller amount is one edit away.
    setAmount(String(item.quantity.amount))
    setUnit(item.quantity.unit)
    setTakeOut(kind)
  }

  function confirmTakeOut(event: FormEvent) {
    event.preventDefault()
    const parsed = parseAmount(amount)
    setAmountInvalid(parsed === null)
    if (parsed === null || takeOut === null) return
    const quantity = { amount: parsed, unit }
    change.mutate(() =>
      takeOut === 'consume'
        ? inventoryApi.consume(householdId, item.id, quantity)
        : inventoryApi.discard(householdId, item.id, quantity, reason),
    )
  }

  function confirmDelete() {
    if (window.confirm(t('inventory.confirmDelete', { name: item.name }))) {
      change.mutate(() => inventoryApi.remove(householdId, item.id))
    }
  }

  return (
    <li className="card item-row">
      <div className="item-main">
        <div>
          <strong>{item.name}</strong>
          {item.brand && <span className="muted"> · {item.brand}</span>}
          <div className="muted small">
            {formatQuantity(item.quantity)} · {t(`locations.${item.storageLocation}`)} ·{' '}
            {item.expirationDate
              ? t(item.expirationSource === 'ESTIMATED' ? 'inventory.expiresEstimated' : 'inventory.expires', {
                  date: formatDay(item.expirationDate),
                })
              : t('inventory.noDate')}
            {item.openedDate && <> · {t('inventory.opened', { date: formatDay(item.openedDate) })}</>}
          </div>
        </div>
        <div className="item-badges">
          {active && <PriorityBadge item={item} />}
          {item.status !== 'AVAILABLE' && (
            <span className={`badge status-${item.status.toLowerCase()}`}>{t(`inventory.status.${item.status}`)}</span>
          )}
        </div>
      </div>

      {active && (
        <div className="item-actions">
          <button type="button" className="button small-button" onClick={() => start('consume')}>
            {t('inventory.consume')}
          </button>
          <button type="button" className="button small-button" onClick={() => start('discard')}>
            {t('inventory.discard')}
          </button>
          {item.status === 'AVAILABLE' && (
            <button
              type="button"
              className="button ghost small-button"
              disabled={change.isPending}
              onClick={() => change.mutate(() => inventoryApi.open(householdId, item.id))}
            >
              {t('inventory.open')}
            </button>
          )}
          <button type="button" className="button ghost small-button" onClick={() => onEdit(item)}>
            {t('inventory.edit')}
          </button>
          <button
            type="button"
            className="button ghost danger small-button"
            disabled={change.isPending}
            onClick={confirmDelete}
          >
            {t('inventory.delete')}
          </button>
        </div>
      )}

      {takeOut && (
        <form className="take-out" onSubmit={confirmTakeOut}>
          <strong>{t(takeOut === 'consume' ? 'inventory.consumeTitle' : 'inventory.discardTitle')}</strong>
          <div className="field-row">
            <label>
              {t('inventory.amount')}
              <input
                type="text"
                inputMode="decimal"
                required
                autoFocus
                aria-invalid={amountInvalid}
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
              />
            </label>
            <label>
              {t('inventory.unit')}
              <select value={unit} onChange={(event) => setUnit(event.target.value as Unit)}>
                {compatibleUnits(item.quantity.unit).map((option) => (
                  <option key={option} value={option}>
                    {t(`unitNames.${option}`)}
                  </option>
                ))}
              </select>
            </label>
            {takeOut === 'discard' && (
              <label>
                {t('inventory.reason')}
                <select value={reason} onChange={(event) => setReason(event.target.value as WasteReason)}>
                  {WASTE_REASONS.map((option) => (
                    <option key={option} value={option}>
                      {t(`inventory.reasonOption.${option}`)}
                    </option>
                  ))}
                </select>
              </label>
            )}
          </div>
          {amountInvalid && (
            <p role="alert" className="error">
              {t('inventory.invalidAmount')}
            </p>
          )}
          <div className="button-row">
            <button type="submit" className="button primary" disabled={change.isPending}>
              {t('inventory.confirm')}
            </button>
            <button type="button" className="button ghost" onClick={() => setTakeOut(null)}>
              {t('inventory.cancel')}
            </button>
          </div>
        </form>
      )}
      <ErrorMessage error={change.error} />
    </li>
  )
}
