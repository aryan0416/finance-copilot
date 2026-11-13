import axios, { AxiosError } from 'axios'
import type { ProblemDetail } from './types'

const TOKEN_KEY = 'fc.accessToken'

/**
 * Token storage: kept in memory for requests and mirrored to localStorage so a page reload can restore
 * the session (AuthProvider then validates it via /api/auth/me).
 */
let accessToken: string | null = readStoredToken()

function readStoredToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function getToken() {
  return accessToken
}

export function setToken(token: string | null) {
  accessToken = token
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    // storage unavailable (private mode): the in-memory token still works for this tab
  }
}

let onUnauthorized: () => void = () => {}

/** AuthProvider registers a handler so an expired token logs the user out wherever the 401 happens. */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

export const api = axios.create({
  // Empty in dev: Vite proxies /api to Spring Boot. Set VITE_API_BASE_URL for a separate API host.
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 60_000,
})

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ProblemDetail>) => {
    const isAuthCall = error.config?.url?.startsWith('/api/auth/login') || error.config?.url?.startsWith('/api/auth/register')
    if (error.response?.status === 401 && !isAuthCall) {
      setToken(null)
      onUnauthorized()
    }
    return Promise.reject(error)
  },
)

/** Human-readable message from a backend problem+json error (or a network failure). */
export function errorMessage(error: unknown, fallback = 'Something went wrong'): string {
  if (axios.isAxiosError<ProblemDetail>(error)) {
    if (!error.response) return 'Cannot reach the server. Is the backend running?'
    const body = error.response.data
    if (body?.fieldErrors) {
      return Object.entries(body.fieldErrors)
        .map(([field, message]) => `${field} ${message}`)
        .join('; ')
    }
    return body?.detail ?? body?.title ?? fallback
  }
  return error instanceof Error ? error.message : fallback
}
