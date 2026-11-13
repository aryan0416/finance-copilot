const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })
const inrPrecise = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 })
const inrCompact = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  notation: 'compact',
  maximumFractionDigits: 1,
})

/** ₹1,23,456 (Indian digit grouping). */
export const formatINR = (value: number | null | undefined) => (value == null ? '—' : inr.format(value))
export const formatINRPrecise = (value: number | null | undefined) =>
  value == null ? '—' : inrPrecise.format(value)
/** ₹1.2L / ₹45K for axis ticks. */
export const formatINRCompact = (value: number) => inrCompact.format(value)

export const formatPercent = (value: number | null | undefined, digits = 1) =>
  value == null ? '—' : `${value.toFixed(digits)}%`

export function formatDate(iso: string | null | undefined) {
  if (!iso) return '—'
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
}

/** "2026-10" -> "Oct 2026" (or "Oct" when short). */
export function formatMonth(yearMonth: string, short = false) {
  const [y, m] = yearMonth.split('-').map(Number)
  return new Date(y, m - 1, 1).toLocaleDateString('en-IN', short ? { month: 'short' } : { month: 'short', year: 'numeric' })
}

export const frequencyLabel = { WEEKLY: 'Weekly', MONTHLY: 'Monthly', YEARLY: 'Yearly' } as const
