import { useMutation, useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { queryKeys } from '../api/queryKeys'
import { aiApi } from '../api/recipes'
import { scansApi } from '../api/scans'
import type { PhotoCandidate } from '../api/scans'
import { ErrorMessage } from '../components/ErrorMessage'
import { currentLocale } from '../i18n'
import { shrinkPhoto } from './shrinkPhoto'

/** Below this the model is guessing, and the candidates are introduced as guesses. */
const SURE = 0.6

interface Props {
  householdId: string
  /** The person picked one of the candidates. */
  onPick: (candidate: PhotoCandidate) => void
}

/**
 * "What is this?": a photo of a food, identified by the server's language model as candidates, each with how
 * sure it is. Nothing is filled in until the person picks one. Only offered where the server has a model.
 */
export function FoodPhoto({ householdId, onPick }: Props) {
  const { t } = useTranslation()
  const lang = currentLocale()
  const ai = useQuery({ queryKey: queryKeys.ai, queryFn: aiApi.status, staleTime: Infinity })
  const identify = useMutation({
    mutationFn: async (photo: File) => scansApi.identifyFood(householdId, lang, await shrinkPhoto(photo)),
  })

  if (!ai.data?.enabled) return null
  const candidates = identify.data
  const sure = (candidates?.[0]?.confidence ?? 0) >= SURE

  return (
    <div className="food-photo">
      <label className="button">
        {t('photo.take')}
        <input
          type="file"
          accept="image/jpeg,image/png,image/webp"
          capture="environment"
          className="visually-hidden"
          disabled={identify.isPending}
          onChange={(event) => {
            const photo = event.target.files?.[0]
            // The same file can be chosen again.
            event.target.value = ''
            if (photo) identify.mutate(photo)
          }}
        />
      </label>
      <p className="muted small">{t('photo.privacy')}</p>
      {identify.isPending && (
        <p role="status" className="muted">
          {t('photo.reading')}
        </p>
      )}
      <ErrorMessage error={identify.error} />
      {candidates && !identify.isPending && (
        <>
          {candidates.length === 0 ? (
            <p role="status">{t('photo.nothing')}</p>
          ) : (
            <>
              <p id="photo-candidates">{sure ? t('photo.whatIsIt') : t('photo.notSure')}</p>
              <ul className="chips" aria-labelledby="photo-candidates">
                {candidates.map((candidate) => (
                  <li key={candidate.foodId ?? candidate.name}>
                    <button type="button" className="chip" onClick={() => onPick(candidate)}>
                      {candidate.name} — {Math.round(candidate.confidence * 100)} %
                    </button>
                  </li>
                ))}
              </ul>
              <p className="muted small">{t('photo.noneHelp')}</p>
            </>
          )}
        </>
      )}
    </div>
  )
}
