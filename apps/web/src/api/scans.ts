import { api } from './client'
import type { Locale } from './endpoints'
import type { FoodCategory, StorageLocation, Unit } from './inventory'

/** A food a photo may show, as a language model judged it. */
export interface PhotoCandidate {
  /** The catalog food, or null for something that is not in the catalog. */
  foodId: string | null
  name: string
  /** Between 0 and 1: how sure the model is. */
  confidence: number
  category: FoodCategory
  defaultUnit: Unit | null
  defaultStorage: StorageLocation | null
}

export const scansApi = {
  /** Needs a language model on the server. The photo is sent to it and not stored. */
  identifyFood: (householdId: string, lang: Locale, photo: Blob) => {
    const form = new FormData()
    form.append('image', photo, 'photo.jpg')
    return api<PhotoCandidate[]>(`/households/${householdId}/scans/food?${new URLSearchParams({ lang })}`, {
      method: 'POST',
      body: form,
    })
  },
}
