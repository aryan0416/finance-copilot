import { api } from './client'
import type {
  AskResponse,
  AuthResponse,
  Category,
  Dashboard,
  ImportSummary,
  InsightResponse,
  Page,
  RecategorizeResponse,
  StatementSummary,
  Subscription,
  Transaction,
  TransactionFilters,
  User,
} from './types'

export const authApi = {
  login: (email: string, password: string) =>
    api.post<AuthResponse>('/api/auth/login', { email, password }).then((r) => r.data),
  register: (email: string, password: string, displayName?: string) =>
    api.post<AuthResponse>('/api/auth/register', { email, password, displayName }).then((r) => r.data),
  me: () => api.get<User>('/api/auth/me').then((r) => r.data),
}

export const dashboardApi = {
  get: (month?: string) => api.get<Dashboard>('/api/dashboard', { params: { month } }).then((r) => r.data),
}

export const transactionApi = {
  list: (filters: TransactionFilters) => {
    // Drop empty values so the backend sees only active filters.
    const params = Object.fromEntries(Object.entries(filters).filter(([, v]) => v !== undefined && v !== ''))
    return api.get<Page<Transaction>>('/api/transactions', { params }).then((r) => r.data)
  },
  recategorize: (id: number, categoryId: number, createRule: boolean) =>
    api
      .patch<RecategorizeResponse>(`/api/transactions/${id}/category`, { categoryId, createRule })
      .then((r) => r.data),
}

export const categoryApi = {
  list: () => api.get<Category[]>('/api/categories').then((r) => r.data),
}

export const statementApi = {
  upload: (file: File, onProgress?: (percent: number) => void) => {
    const form = new FormData()
    form.append('file', file)
    return api
      .post<ImportSummary>('/api/statements', form, {
        timeout: 180_000, // first imports may wait on the LLM
        onUploadProgress: (e) => {
          if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100))
        },
      })
      .then((r) => r.data)
  },
  list: () => api.get<StatementSummary[]>('/api/statements').then((r) => r.data),
}

export const subscriptionApi = {
  list: () => api.get<Subscription[]>('/api/subscriptions').then((r) => r.data),
}

export const aiApi = {
  ask: (question: string) =>
    api.post<AskResponse>('/api/ask', { question }, { timeout: 90_000 }).then((r) => r.data),
  insight: (month?: string, refresh = false) =>
    api
      .get<InsightResponse>('/api/insights', { params: { month, refresh: refresh || undefined }, timeout: 90_000 })
      .then((r) => r.data),
}
