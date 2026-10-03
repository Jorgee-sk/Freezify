import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { UNITS, foodsApi } from '../api/inventory'
import type { Food, FoodCategory, Unit } from '../api/inventory'
import { queryKeys } from '../api/queryKeys'
import { shoppingApi } from '../api/shopping'
import type { ShoppingItem, ShoppingItemInput } from '../api/shopping'
import { ErrorMessage } from '../components/ErrorMessage'
import { currentLocale } from '../i18n'
import { formatQuantity, parseAmount, todayIso } from '../inventory/format'
import { useDebounced } from '../inventory/useDebounced'
import { addDays, weekdayAndDay } from '../plan/wording'
import { subscribeToHousehold } from '../realtime/householdEvents'

const MIN_SEARCH_LENGTH = 2

/** The shared shopping list of a household, made to be used in the shop: aisle by aisle, ticking things off. */
export function ShoppingListPage() {
  const { householdId = '' } = useParams()
  const { t } = useTranslation()
  const lang = currentLocale()
  const queryClient = useQueryClient()
  const [filled, setFilled] = useState<number | null>(null)
  const [week, setWeek] = useState<'this' | 'next'>('this')
  const [editing, setEditing] = useState<string | null>(null)

  const list = useQuery({
    queryKey: queryKeys.shoppingList(householdId, lang),
    queryFn: () => shoppingApi.list(householdId, lang),
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.shopping(householdId) })

  // Whoever else is in the shop, or at home adding things, is seen at once.
  const listening = list.isSuccess
  useEffect(() => {
    if (!listening) return
    return subscribeToHousehold(householdId, (event) => {
      if (event === 'shopping-list-changed' || event === 'reconnected') {
        void queryClient.invalidateQueries({ queryKey: queryKeys.shopping(householdId) })
      }
    })
  }, [householdId, listening, queryClient])

  const check = useMutation({
    mutationFn: (item: ShoppingItem) => shoppingApi.check(householdId, item.id, !item.checked, lang),
    onSuccess: refresh,
  })
  const remove = useMutation({
    mutationFn: (itemId: string) => shoppingApi.remove(householdId, itemId),
    onSuccess: refresh,
  })
  const removeChecked = useMutation({
    mutationFn: () => shoppingApi.removeChecked(householdId),
    onSuccess: refresh,
  })
  const fill = useMutation({
    mutationFn: () => shoppingApi.fillFromPlan(householdId, week === 'this' ? todayIso() : addDays(todayIso(), 7)),
    onSuccess: async (result) => {
      setFilled(result.lines)
      await refresh()
    },
  })

  const items = list.data?.items ?? []
  const bought = items.filter((item) => item.checked).length
  const aisles = items.reduce<{ category: FoodCategory; items: ShoppingItem[] }[]>((groups, item) => {
    const last = groups.at(-1)
    if (last?.category === item.category) last.items.push(item)
    else groups.push({ category: item.category, items: [item] })
    return groups
  }, [])

  function takeOffBought() {
    // They leave the list for good: never without asking.
    if (window.confirm(t('shopping.removeCheckedConfirm', { count: bought }))) removeChecked.mutate()
  }

  return (
    <>
      <Link to={`/households/${householdId}`} className="back-link">
        ← {t('households.backToInventory')}
      </Link>
      <div className="title-row">
        <h1>{t('shopping.title')}</h1>
        <Link to={`/households/${householdId}/plan`} className="button ghost push-right">
          {t('plan.open')}
        </Link>
      </div>

      <section className="card plan-generate">
        <p className="muted small">{t('shopping.fromPlanHelp')}</p>
        <div className="button-row">
          <select aria-label={t('shopping.week')} value={week} onChange={(event) => setWeek(event.target.value as 'this' | 'next')}>
            <option value="this">{t('plan.thisWeek')}</option>
            <option value="next">{t('plan.nextWeek')}</option>
          </select>
          <button type="button" className="button primary" disabled={fill.isPending} onClick={() => fill.mutate()}>
            {t('shopping.fromPlan')}
          </button>
        </div>
        <ErrorMessage error={fill.error} />
        {filled !== null && (
          <p role="status">{filled === 0 ? t('shopping.nothingLacking') : t('shopping.filled', { count: filled })}</p>
        )}
      </section>

      <AddLine householdId={householdId} />

      {list.isPending && <p className="muted">{t('app.loading')}</p>}
      <ErrorMessage error={list.error ?? check.error ?? remove.error ?? removeChecked.error} />
      {list.data?.items.length === 0 && <p className="card muted">{t('shopping.empty')}</p>}

      {aisles.map((aisle) => (
        <section key={aisle.category} className="card shopping-aisle" aria-labelledby={`aisle-${aisle.category}`}>
          <h2 id={`aisle-${aisle.category}`}>{t(`categories.${aisle.category}`)}</h2>
          <ul aria-labelledby={`aisle-${aisle.category}`}>
            {aisle.items.map((item) => (
              <li key={item.id} className={item.checked ? 'shopping-line checked' : 'shopping-line'}>
                <label className="check">
                  <input
                    type="checkbox"
                    checked={item.checked}
                    disabled={check.isPending}
                    onChange={() => check.mutate(item)}
                  />
                  <span>
                    {item.name}
                    {item.quantity && (
                      <>
                        {' · '}
                        <span className="muted">{formatQuantity(item.quantity)}</span>
                      </>
                    )}
                  </span>
                </label>
                {item.origin === 'PLAN' && item.neededOn && (
                  <span className="muted small">{t('shopping.forPlan', { day: weekdayAndDay(item.neededOn) })}</span>
                )}
                <span className="button-row push-right">
                  <button type="button" className="button small-button ghost" onClick={() => setEditing(item.id)}>
                    {t('shopping.change')}
                  </button>
                  <button
                    type="button"
                    className="button small-button ghost"
                    aria-label={t('shopping.removeLine', { name: item.name })}
                    disabled={remove.isPending}
                    onClick={() => remove.mutate(item.id)}
                  >
                    {t('plan.remove')}
                  </button>
                </span>
                {editing === item.id && (
                  <EditLine householdId={householdId} item={item} onDone={() => setEditing(null)} />
                )}
              </li>
            ))}
          </ul>
        </section>
      ))}

      {bought > 0 && (
        <button type="button" className="button" disabled={removeChecked.isPending} onClick={takeOffBought}>
          {t('shopping.removeChecked', { count: bought })}
        </button>
      )}
    </>
  )
}

/** Puts something on the list: a catalog food when one is picked from the suggestions, free text otherwise. */
function AddLine({ householdId }: { householdId: string }) {
  const { t } = useTranslation()
  const lang = currentLocale()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [food, setFood] = useState<Food | null>(null)
  const [amount, setAmount] = useState('')
  const [unit, setUnit] = useState<Unit>('UNIT')
  const [typing, setTyping] = useState(false)
  const searchText = useDebounced(name.trim())
  const suggestions = useQuery({
    queryKey: queryKeys.foodSearch(searchText, lang),
    queryFn: () => foodsApi.search(searchText, lang),
    enabled: typing && searchText.length >= MIN_SEARCH_LENGTH,
  })

  const add = useMutation({
    mutationFn: (input: ShoppingItemInput) => shoppingApi.add(householdId, input, lang),
    onSuccess: async () => {
      setName('')
      setFood(null)
      setAmount('')
      await queryClient.invalidateQueries({ queryKey: queryKeys.shopping(householdId) })
    },
  })

  function pick(picked: Food) {
    setFood(picked)
    setName(picked.name)
    setUnit(picked.defaultUnit)
    setTyping(false)
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    const value = parseAmount(amount)
    add.mutate({
      foodId: food?.id ?? null,
      name: food ? null : name.trim(),
      category: null,
      quantity: value === null ? null : { amount: value, unit },
    })
  }

  const showSuggestions = typing && searchText.length >= MIN_SEARCH_LENGTH && (suggestions.data?.length ?? 0) > 0
  return (
    <form className="card shopping-add" aria-labelledby="add-line-title" onSubmit={submit}>
      <h2 id="add-line-title">{t('shopping.addTitle')}</h2>
      <label>
        {t('shopping.what')}
        <input
          type="text"
          required
          maxLength={120}
          autoComplete="off"
          placeholder={t('shopping.whatPlaceholder')}
          value={name}
          onChange={(event) => {
            setName(event.target.value)
            setFood(null)
            setTyping(true)
          }}
        />
      </label>
      {showSuggestions && (
        <ul className="chips" aria-label={t('inventory.suggestions')}>
          {suggestions.data?.map((suggestion) => (
            <li key={suggestion.id}>
              <button type="button" className="chip" onClick={() => pick(suggestion)}>
                {suggestion.name}
              </button>
            </li>
          ))}
        </ul>
      )}
      <QuantityFields amount={amount} unit={unit} onAmount={setAmount} onUnit={setUnit} />
      <ErrorMessage error={add.error} />
      <div className="button-row">
        <button type="submit" className="button primary" disabled={add.isPending}>
          {t('shopping.add')}
        </button>
      </div>
    </form>
  )
}

/** Changes how much of a line to buy. The line then belongs to people: the plan no longer changes it. */
function EditLine({ householdId, item, onDone }: { householdId: string; item: ShoppingItem; onDone: () => void }) {
  const { t } = useTranslation()
  const lang = currentLocale()
  const queryClient = useQueryClient()
  const [amount, setAmount] = useState(item.quantity ? String(item.quantity.amount) : '')
  const [unit, setUnit] = useState<Unit>(item.quantity?.unit ?? 'UNIT')
  const save = useMutation({
    mutationFn: (input: ShoppingItemInput) => shoppingApi.edit(householdId, item.id, input, lang),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.shopping(householdId) })
      onDone()
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    const value = parseAmount(amount)
    save.mutate({
      foodId: item.foodId,
      name: item.foodId ? null : item.name,
      category: item.category,
      quantity: value === null ? null : { amount: value, unit },
    })
  }

  return (
    <form className="plan-picker" aria-label={t('shopping.changeTitle', { name: item.name })} onSubmit={submit}>
      <QuantityFields amount={amount} unit={unit} onAmount={setAmount} onUnit={setUnit} />
      <ErrorMessage error={save.error} />
      <div className="button-row">
        <button type="submit" className="button small-button primary" disabled={save.isPending}>
          {t('inventory.save')}
        </button>
        <button type="button" className="button small-button ghost" onClick={onDone}>
          {t('inventory.cancel')}
        </button>
      </div>
    </form>
  )
}

/** How much, optionally: "pan" can go on the list without saying how much. */
function QuantityFields({
  amount,
  unit,
  onAmount,
  onUnit,
}: {
  amount: string
  unit: Unit
  onAmount: (amount: string) => void
  onUnit: (unit: Unit) => void
}) {
  const { t } = useTranslation()
  return (
    <div className="field-row">
      <label>
        {t('shopping.amount')}
        <input
          type="text"
          inputMode="decimal"
          placeholder={t('shopping.amountPlaceholder')}
          value={amount}
          onChange={(event) => onAmount(event.target.value)}
        />
      </label>
      <label>
        {t('inventory.unit')}
        <select value={unit} onChange={(event) => onUnit(event.target.value as Unit)}>
          {UNITS.map((option) => (
            <option key={option} value={option}>
              {t(`unitNames.${option}`)}
            </option>
          ))}
        </select>
      </label>
    </div>
  )
}
