import { useTranslation } from 'react-i18next'
import { errorKey } from '../i18n'

export function ErrorMessage({ error }: { error: unknown }) {
  const { t } = useTranslation()
  if (!error) return null
  return (
    <p role="alert" className="error">
      {t(errorKey(error))}
    </p>
  )
}
