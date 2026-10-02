import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { AVOIDABLE_TRAITS, DIET_TYPES, dietApi } from '../api/recipes'
import type { Diet, FoodTrait } from '../api/recipes'
import { queryKeys } from '../api/queryKeys'
import { ErrorMessage } from '../components/ErrorMessage'

/** What is not cooked in the household. It belongs to the household: every member sees and can change it. */
export function DietPage() {
  const { householdId = '' } = useParams()
  const { t } = useTranslation()
  const diet = useQuery({ queryKey: queryKeys.diet(householdId), queryFn: () => dietApi.get(householdId) })

  return (
    <>
      <Link to={`/households/${householdId}/recipes`} className="back-link">
        ← {t('recipes.backToRecipes')}
      </Link>
      <h1>{t('diet.title')}</h1>
      {diet.isPending && <p className="muted">{t('app.loading')}</p>}
      <ErrorMessage error={diet.error} />
      {/* The form starts from what the server has; it is only rendered once that is known. */}
      {diet.data && <DietForm householdId={householdId} initial={diet.data} />}
    </>
  )
}

function DietForm({ householdId, initial }: { householdId: string; initial: Diet }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState(initial)
  const [saved, setSaved] = useState(false)

  const save = useMutation({
    mutationFn: (diet: Diet) => dietApi.update(householdId, diet),
    onSuccess: async (stored) => {
      queryClient.setQueryData(queryKeys.diet(householdId), stored)
      setSaved(true)
      // Every list of recipes was filtered with the old restrictions.
      await queryClient.invalidateQueries({ queryKey: queryKeys.recipes })
    },
  })

  function change(changes: Partial<Diet>) {
    setDraft({ ...draft, ...changes })
    setSaved(false)
  }

  function toggle(trait: FoodTrait, avoid: boolean) {
    const others = draft.avoided.filter((other) => other !== trait)
    change({ avoided: avoid ? [...others, trait] : others })
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    save.mutate(draft)
  }

  return (
    <form className="card preferences-form" onSubmit={submit}>
      <p className="muted small">{t('diet.shared')}</p>

      <fieldset>
        <legend>{t('diet.typeLabel')}</legend>
        <div className="check-grid">
          {DIET_TYPES.map((type) => (
            <label key={type} className="check">
              <input type="radio" name="diet" checked={draft.type === type} onChange={() => change({ type })} />
              {t(`diet.typeOption.${type}`)}
            </label>
          ))}
        </div>
      </fieldset>

      <fieldset>
        <legend>{t('diet.avoidLabel')}</legend>
        <div className="check-grid">
          {AVOIDABLE_TRAITS.map((trait) => (
            <label key={trait} className="check">
              <input
                type="checkbox"
                checked={draft.avoided.includes(trait)}
                onChange={(event) => toggle(trait, event.target.checked)}
              />
              {t(`diet.traitOption.${trait}`)}
            </label>
          ))}
        </div>
      </fieldset>

      <p role="note" className="warning">
        {t('diet.disclaimer')}
      </p>

      <ErrorMessage error={save.error} />
      <div className="button-row">
        <button type="submit" className="button primary" disabled={save.isPending}>
          {t('inventory.save')}
        </button>
        {saved && (
          <span role="status" className="muted">
            {t('diet.saved')}
          </span>
        )}
      </div>
    </form>
  )
}
