import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { householdsApi } from '../api/endpoints'
import { FOOD_CATEGORIES, STORAGE_LOCATIONS, inventoryApi } from '../api/inventory'
import type { FoodCategory, InventoryFilter, InventoryItem, ItemState, StorageLocation } from '../api/inventory'
import { queryKeys } from '../api/queryKeys'
import { ErrorMessage } from '../components/ErrorMessage'
import { ConsumeFirstPanel } from '../inventory/ConsumeFirstPanel'
import { ItemForm } from '../inventory/ItemForm'
import { ItemRow } from '../inventory/ItemRow'
import { useDebounced } from '../inventory/useDebounced'
import { useInventoryEvents } from '../realtime/useInventoryEvents'

const STATES: ItemState[] = ['ACTIVE', 'FINISHED', 'ALL']

/** `null` when closed, `'new'` when adding, or the item being edited. */
type Editing = null | 'new' | InventoryItem

export function InventoryPage() {
  const { householdId = '' } = useParams()
  const { t } = useTranslation()
  const [state, setState] = useState<ItemState>('ACTIVE')
  const [location, setLocation] = useState<StorageLocation | null>(null)
  const [category, setCategory] = useState<FoodCategory | null>(null)
  const [text, setText] = useState('')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<Editing>(null)

  const searchText = useDebounced(text)
  const filter: InventoryFilter = { state, location, category, text: searchText, page }

  const household = useQuery({
    queryKey: queryKeys.household(householdId),
    queryFn: () => householdsApi.get(householdId),
  })
  const items = useQuery({
    queryKey: queryKeys.inventoryList(householdId, filter),
    queryFn: () => inventoryApi.list(householdId, filter),
    enabled: household.isSuccess,
    // Keeps the list on screen while the next filter or page loads.
    placeholderData: keepPreviousData,
  })

  // What other members (or this user on another device) change shows up without reloading.
  useInventoryEvents(householdId, household.isSuccess)

  /** Any change of filter starts again from the first page. */
  function filtering<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value)
      setPage(0)
    }
  }

  if (household.isPending) return <p className="muted">{t('app.loading')}</p>
  if (household.isError) {
    return (
      <>
        <ErrorMessage error={household.error} />
        <Link to="/">{t('households.back')}</Link>
      </>
    )
  }

  const filtered = state !== 'ACTIVE' || location !== null || category !== null || searchText.trim() !== ''

  return (
    <>
      <Link to="/" className="back-link">
        ← {t('households.back')}
      </Link>
      <div className="title-row">
        <h1>{household.data.name}</h1>
        <Link to={`/households/${householdId}/recipes`} className="button">
          {t('recipes.title')}
        </Link>
        <Link to={`/households/${householdId}/plan`} className="button">
          {t('plan.open')}
        </Link>
        <Link to={`/households/${householdId}/settings`} className="button ghost">
          {t('households.settings')}
        </Link>
      </div>

      <div className="title-row">
        <h2 id="inventory-title">{t('inventory.title')}</h2>
        {items.data && <span className="muted">{t('inventory.count', { count: items.data.totalItems })}</span>}
        {editing === null && (
          <button type="button" className="button primary push-right" onClick={() => setEditing('new')}>
            {t('inventory.add')}
          </button>
        )}
      </div>

      {editing !== null && (
        <ItemForm
          // A fresh form per item, so no state leaks from one edit into the next.
          key={editing === 'new' ? 'new' : editing.id}
          householdId={householdId}
          item={editing === 'new' ? undefined : editing}
          onDone={() => setEditing(null)}
        />
      )}

      <ConsumeFirstPanel householdId={householdId} />

      <div className="filters" role="search">
        <input
          type="search"
          aria-label={t('inventory.search')}
          placeholder={t('inventory.searchPlaceholder')}
          value={text}
          onChange={(event) => filtering(setText)(event.target.value)}
        />
        <select
          aria-label={t('inventory.category')}
          value={category ?? ''}
          onChange={(event) => filtering(setCategory)((event.target.value || null) as FoodCategory | null)}
        >
          <option value="">{t('inventory.allCategories')}</option>
          {FOOD_CATEGORIES.map((option) => (
            <option key={option} value={option}>
              {t(`categories.${option}`)}
            </option>
          ))}
        </select>
        <select
          aria-label={t('inventory.state')}
          value={state}
          onChange={(event) => filtering(setState)(event.target.value as ItemState)}
        >
          {STATES.map((option) => (
            <option key={option} value={option}>
              {t(`inventory.stateOption.${option}`)}
            </option>
          ))}
        </select>
      </div>
      <div className="chips" role="group" aria-label={t('inventory.location')}>
        <button
          type="button"
          className="chip"
          aria-pressed={location === null}
          onClick={() => filtering(setLocation)(null)}
        >
          {t('inventory.allLocations')}
        </button>
        {STORAGE_LOCATIONS.map((option) => (
          <button
            key={option}
            type="button"
            className="chip"
            aria-pressed={location === option}
            onClick={() => filtering(setLocation)(option)}
          >
            {t(`locations.${option}`)}
          </button>
        ))}
      </div>

      {items.isPending && <p className="muted">{t('app.loading')}</p>}
      <ErrorMessage error={items.error} />
      {items.data?.items.length === 0 && (
        <p className="card muted">{t(filtered ? 'inventory.emptyFiltered' : 'inventory.empty')}</p>
      )}
      <ul className="item-list" aria-labelledby="inventory-title">
        {items.data?.items.map((item) => (
          <ItemRow key={item.id} householdId={householdId} item={item} onEdit={setEditing} />
        ))}
      </ul>

      {items.data && items.data.totalPages > 1 && (
        <nav className="pagination">
          <button type="button" className="button" disabled={page === 0} onClick={() => setPage(page - 1)}>
            {t('inventory.previous')}
          </button>
          <span className="muted">{t('inventory.pageOf', { page: page + 1, total: items.data.totalPages })}</span>
          <button
            type="button"
            className="button"
            disabled={page + 1 >= items.data.totalPages}
            onClick={() => setPage(page + 1)}
          >
            {t('inventory.next')}
          </button>
        </nav>
      )}
    </>
  )
}
