// Mirrors the backend DTO records (app.financecopilot.dto). Keep in sync when an API changes.

export type Direction = 'DEBIT' | 'CREDIT'
export type CategorySource = 'RULE' | 'CACHE' | 'AI' | 'USER' | 'NONE'
export type CategoryKind = 'EXPENSE' | 'INCOME' | 'TRANSFER'
export type Frequency = 'WEEKLY' | 'MONTHLY' | 'YEARLY'

export interface User {
  id: number
  email: string
  displayName: string | null
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: User
}

export interface Category {
  id: number
  name: string
  kind: CategoryKind
  color: string
  system: boolean
}

export interface Transaction {
  id: number
  date: string
  description: string
  merchant: string
  amount: number
  direction: Direction
  balance: number | null
  category: Category | null
  categorySource: CategorySource
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface TransactionFilters {
  from?: string
  to?: string
  categoryId?: number
  direction?: Direction
  q?: string
  source?: CategorySource
  page?: number
  size?: number
}

export interface RecategorizeResponse {
  transaction: Transaction
  ruleCreated: boolean
  similarUpdated: number
}

export interface ImportSummary {
  statementId: number
  fileName: string
  fileType: 'CSV' | 'PDF'
  imported: number
  duplicates: number
  failed: number
  failedRows: { line: number; reason: string }[]
  periodStart: string | null
  periodEnd: string | null
  categorization: {
    byRule: number
    byCache: number
    byAi: number
    uncategorised: number
    withoutAiPercent: number
  }
}

export interface StatementSummary {
  id: number
  fileName: string
  fileType: 'CSV' | 'PDF'
  imported: number
  duplicates: number
  failed: number
  periodStart: string | null
  periodEnd: string | null
  uploadedAt: string
}

export interface Subscription {
  id: number
  merchant: string
  category: Category | null
  averageAmount: number
  frequency: Frequency
  occurrences: number
  lastChargeDate: string
  nextExpectedDate: string
  status: 'ACTIVE' | 'INACTIVE'
  yearlyCost: number
}

export interface Dashboard {
  month: string
  availableMonths: string[]
  kpis: {
    spent: number
    income: number
    savingsRate: number | null
    previousSpent: number | null
    spentChangePercent: number | null
    transactionCount: number
  }
  monthly: { month: string; income: number; spent: number; spentChangePercent: number | null }[]
  categories: { categoryId: number; name: string; color: string; amount: number; count: number }[]
  topMerchants: { merchant: string; amount: number; count: number }[]
  anomalies: {
    transactionId: number
    date: string
    merchant: string
    category: string
    amount: number
    categoryAverage: number
    threshold: number
  }[]
  upcoming: { merchant: string; amount: number; expectedDate: string; frequency: Frequency }[]
  categorisation: {
    total: number
    byRule: number
    byCache: number
    byAi: number
    byUser: number
    uncategorised: number
    autoCategorisedPercent: number
    withoutAiPercent: number
    aiCalls: number
  }
}

/** RFC 7807 body returned by every backend error. */
export interface ProblemDetail {
  title?: string
  status?: number
  detail?: string
  fieldErrors?: Record<string, string>
}

export type ChartType = 'bar' | 'line' | 'pie' | 'table' | 'number'

export interface AskResponse {
  question: string
  sql: string
  chartType: ChartType
  explanation: string
  columns: string[]
  rows: (string | number | boolean | null)[][]
  latencyMs: number
}

export interface InsightResponse {
  month: string
  summary: string
  figures: Record<string, unknown>
  generatedAt: string
}
