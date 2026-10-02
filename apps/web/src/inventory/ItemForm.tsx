import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { FOOD_CATEGORIES, STORAGE_LOCATIONS, UNITS, foodsApi, inventoryApi } from '../api/inventory'
import type { Food, FoodCategory, InventoryItem, ItemInput, RecentFood, StorageLocation, Unit } from '../api/inventory'
import { queryKeys } from '../api/queryKeys'
import { ErrorMessage } from '../components/ErrorMessage'
import { currentLocale } from '../i18n'
import { formatDay, parseAmount, todayIso } from './format'
import { useDebounced } from './useDebounced'

interface Props {
  householdId: string
  /** The item being edited; absent when adding a new one. */
  item?: InventoryItem
  onDone: () => void
}

const MIN_SEARCH_LENGTH = 2

export function ItemForm({ householdId, item, onDone }: Props) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const [name, setName] = useState(item?.name ?? '')
  const [foodId, setFoodId] = useState<string | null>(item?.foodId ?? null)
  const [category, setCategory] = useState<FoodCategory | ''>(item?.category ?? '')
  const [amount, setAmount] = useState(item ? String(item.quantity.amount) : '1')
  const [unit, setUnit] = useState<Unit>(item?.quantity.unit ?? 'UNIT')
  const [location, setLocation] = useState<StorageLocation>(item?.storageLocation ?? 'REFRIGERATOR')
  // Only the date the user gave is editable; an estimate is shown as a hint and recalculated on save.
  const [expirationDate, setExpirationDate] = useState(item?.userExpirationDate ?? '')
  const [purchaseDate, setPurchaseDate] = useState(item?.purchaseDate ?? todayIso())
  const [brand, setBrand] = useState(item?.brand ?? '')
  const [price, setPrice] = useState(item?.estimatedPrice == null ? '' : String(item.estimatedPrice))
  const [notes, setNotes] = useState(item?.notes ?? '')
  const [amountInvalid, setAmountInvalid] = useState(false)
  // Suggestions are for what is being typed, not for a name that was just picked from them.
  const [typing, setTyping] = useState(false)

  const searchText = useDebounced(name.trim())
  const lang = currentLocale()
  const suggestions = useQuery({
    queryKey: queryKeys.foodSearch(searchText, lang),
    queryFn: () => foodsApi.search(searchText, lang),
    enabled: typing && searchText.length >= MIN_SEARCH_LENGTH,
  })
  const recent = useQuery({
    queryKey: queryKeys.recentFoods(householdId),
    queryFn: () => inventoryApi.recent(householdId),
    enabled: !item,
  })

  const save = useMutation({
    mutationFn: (input: ItemInput) =>
      item ? inventoryApi.update(householdId, item.id, input) : inventoryApi.create(householdId, input),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.inventory(householdId) })
      onDone()
    },
  })

  function typeName(value: string) {
    setName(value)
    setTyping(true)
    // A different name is no longer the catalog food that was picked.
    setFoodId(null)
  }

  function pickFood(food: Food) {
    setName(food.name)
    setFoodId(food.id)
    setCategory(food.category)
    setUnit(food.defaultUnit)
    setLocation(food.defaultStorage)
    setTyping(false)
  }

  function pickRecent(food: RecentFood) {
    setName(food.name)
    setFoodId(food.foodId)
    setCategory(food.category)
    setAmount(String(food.quantity.amount))
    setUnit(food.quantity.unit)
    setLocation(food.storageLocation)
    setTyping(false)
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    const parsedAmount = parseAmount(amount)
    setAmountInvalid(parsedAmount === null)
    if (parsedAmount === null) return
    save.mutate({
      foodId,
      name: name.trim(),
      category: category === '' ? null : category,
      quantity: { amount: parsedAmount, unit },
      storageLocation: location,
      purchaseDate: purchaseDate || null,
      expirationDate: expirationDate || null,
      openedDate: item?.openedDate ?? null,
      barcode: item?.barcode ?? null,
      brand: brand.trim() || null,
      estimatedPrice: parseAmount(price),
      notes: notes.trim() || null,
    })
  }

  const showSuggestions = typing && searchText.length >= MIN_SEARCH_LENGTH && (suggestions.data?.length ?? 0) > 0
  const showRecent = !item && name === '' && (recent.data?.length ?? 0) > 0

  return (
    <section className="card item-form" aria-labelledby="item-form-title">
      <h2 id="item-form-title">{t(item ? 'inventory.editTitle' : 'inventory.addTitle')}</h2>
      <form onSubmit={submit}>
        <label>
          {t('inventory.name')}
          <input
            type="text"
            required
            maxLength={120}
            autoFocus
            autoComplete="off"
            placeholder={t('inventory.namePlaceholder')}
            value={name}
            onChange={(event) => typeName(event.target.value)}
          />
        </label>

        {showSuggestions && (
          <ul className="chips" aria-label={t('inventory.suggestions')}>
            {suggestions.data?.map((food) => (
              <li key={food.id}>
                <button type="button" className="chip" onClick={() => pickFood(food)}>
                  {food.name}
                </button>
              </li>
            ))}
          </ul>
        )}
        {showRecent && (
          <ul className="chips" aria-label={t('inventory.recent')}>
            {recent.data?.map((food) => (
              <li key={food.name}>
                <button type="button" className="chip" onClick={() => pickRecent(food)}>
                  {food.name}
                </button>
              </li>
            ))}
          </ul>
        )}

        <div className="field-row">
          <label>
            {t('inventory.amount')}
            <input
              type="text"
              inputMode="decimal"
              required
              aria-invalid={amountInvalid}
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
            />
          </label>
          <label>
            {t('inventory.unit')}
            <select value={unit} onChange={(event) => setUnit(event.target.value as Unit)}>
              {UNITS.map((option) => (
                <option key={option} value={option}>
                  {t(`unitNames.${option}`)}
                </option>
              ))}
            </select>
          </label>
          <label>
            {t('inventory.location')}
            <select value={location} onChange={(event) => setLocation(event.target.value as StorageLocation)}>
              {STORAGE_LOCATIONS.map((option) => (
                <option key={option} value={option}>
                  {t(`locations.${option}`)}
                </option>
              ))}
            </select>
          </label>
        </div>
        {amountInvalid && (
          <p role="alert" className="error">
            {t('inventory.invalidAmount')}
          </p>
        )}

        <label>
          <span>
            {t('inventory.expirationDate')} <span className="muted">({t('inventory.optional')})</span>
          </span>
          <input
            type="date"
            aria-describedby="expiration-help"
            value={expirationDate}
            onChange={(event) => setExpirationDate(event.target.value)}
          />
          <small id="expiration-help" className="muted">
            {item?.expirationSource === 'ESTIMATED' && item.expirationDate
              ? t('inventory.currentEstimate', { date: formatDay(item.expirationDate) })
              : t('inventory.estimateHelp')}
          </small>
        </label>

        <details open={Boolean(item)}>
          <summary>{t('inventory.moreDetails')}</summary>
          <div className="details-fields">
            <label>
              {t('inventory.category')}
              <select value={category} onChange={(event) => setCategory(event.target.value as FoodCategory | '')}>
                <option value="">—</option>
                {FOOD_CATEGORIES.map((option) => (
                  <option key={option} value={option}>
                    {t(`categories.${option}`)}
                  </option>
                ))}
              </select>
            </label>
            <label>
              {t('inventory.purchaseDate')}
              <input type="date" value={purchaseDate} onChange={(event) => setPurchaseDate(event.target.value)} />
            </label>
            <label>
              {t('inventory.brand')}
              <input type="text" maxLength={80} value={brand} onChange={(event) => setBrand(event.target.value)} />
            </label>
            <label>
              {t('inventory.price')}
              <input type="text" inputMode="decimal" value={price} onChange={(event) => setPrice(event.target.value)} />
            </label>
            <label>
              {t('inventory.notes')}
              <input type="text" maxLength={500} value={notes} onChange={(event) => setNotes(event.target.value)} />
            </label>
          </div>
        </details>

        <ErrorMessage error={save.error} />
        <div className="button-row">
          <button type="submit" className="button primary" disabled={save.isPending}>
            {t('inventory.save')}
          </button>
          <button type="button" className="button ghost" onClick={onDone}>
            {t('inventory.cancel')}
          </button>
        </div>
      </form>
    </section>
  )
}
