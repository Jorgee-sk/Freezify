import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { session } from '../api/client'
import { authApi, usersApi } from '../api/endpoints'
import type { Locale, Session, User } from '../api/endpoints'
import { currentLocale, setLocale } from '../i18n'
import { AuthContext } from './useAuth'
import type { AuthState } from './useAuth'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  // Without a session to resume there is nothing to restore, so the user is known to be anonymous.
  const [state, setState] = useState<AuthState>(() =>
    session.mayResume() ? { status: 'loading', user: null } : { status: 'anonymous', user: null },
  )

  const signedOut = useCallback(() => {
    session.clear()
    queryClient.clear()
    setState({ status: 'anonymous', user: null })
  }, [queryClient])

  const signedIn = useCallback((user: User) => {
    setLocale(user.locale)
    setState({ status: 'authenticated', user })
  }, [])

  useEffect(() => {
    session.onExpired(signedOut)
    return () => session.onExpired(null)
  }, [signedOut])

  useEffect(() => {
    if (!session.mayResume()) return
    let cancelled = false
    usersApi
      .me()
      .then((user) => !cancelled && signedIn(user))
      .catch(() => !cancelled && signedOut())
    return () => {
      cancelled = true
    }
  }, [signedIn, signedOut])

  const value = useMemo(() => {
    const start = (result: Session) => {
      session.store(result)
      signedIn(result.user)
    }
    return {
      ...state,
      login: async (email: string, password: string) => start(await authApi.login({ email, password })),
      register: async (email: string, password: string, displayName: string) =>
        start(await authApi.register({ email, password, displayName, locale: currentLocale() })),
      logout: async () => {
        signedOut()
        // Best effort: the local session is gone either way. The cookie goes with the request.
        await authApi.logout().catch(() => undefined)
      },
      changeLocale: async (locale: Locale) => {
        setLocale(locale)
        if (state.status === 'authenticated') {
          const user = await usersApi.updateMe({ locale })
          setState({ status: 'authenticated', user })
        }
      },
    }
  }, [state, signedIn, signedOut])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
