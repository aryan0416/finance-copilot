import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { getToken, setToken, setUnauthorizedHandler } from '@/api/client'
import { authApi } from '@/api/endpoints'
import type { AuthResponse, User } from '@/api/types'

interface AuthState {
  user: User | null
  /** True while a stored token is being validated on page load. */
  initializing: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, displayName?: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<User | null>(null)
  const [initializing, setInitializing] = useState(() => getToken() != null)

  const logout = useCallback(() => {
    setToken(null)
    setUser(null)
    queryClient.clear() // never show the previous user's cached data
  }, [queryClient])

  useEffect(() => {
    setUnauthorizedHandler(logout)
  }, [logout])

  // Restore the session on reload: a stored token is only trusted once /me accepts it.
  useEffect(() => {
    if (!getToken()) return
    authApi
      .me()
      .then(setUser)
      .catch(() => setToken(null))
      .finally(() => setInitializing(false))
  }, [])

  const accept = useCallback(
    (response: AuthResponse) => {
      queryClient.clear()
      setToken(response.accessToken)
      setUser(response.user)
    },
    [queryClient],
  )

  const value = useMemo<AuthState>(
    () => ({
      user,
      initializing,
      login: async (email, password) => accept(await authApi.login(email, password)),
      register: async (email, password, displayName) => accept(await authApi.register(email, password, displayName)),
      logout,
    }),
    [user, initializing, accept, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
