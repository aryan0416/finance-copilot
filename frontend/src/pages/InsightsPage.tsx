import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Lightbulb, Loader2, Lock, RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { PageHeader } from '@/components/common/PageHeader'
import { EmptyState, ErrorState } from '@/components/common/States'
import { aiApi, dashboardApi } from '@/api/endpoints'
import { formatINR, formatMonth, formatPercent } from '@/lib/format'

export function InsightsPage() {
  const queryClient = useQueryClient()
  const months = useQuery({ queryKey: ['dashboard', 'latest'], queryFn: () => dashboardApi.get() })
  const [month, setMonth] = useState<string | undefined>()
  const [regenerating, setRegenerating] = useState(false)
  const selected = month ?? months.data?.availableMonths[0]

  const insight = useQuery({
    queryKey: ['insight', selected],
    queryFn: () => aiApi.insight(selected),
    enabled: !!selected,
    staleTime: Infinity, // generated text only changes on explicit regenerate
    retry: false,
  })

  async function regenerate() {
    setRegenerating(true)
    try {
      const fresh = await aiApi.insight(selected, true)
      queryClient.setQueryData(['insight', selected], fresh)
    } finally {
      setRegenerating(false)
    }
  }

  const available = months.data?.availableMonths ?? []
  const figures = insight.data?.figures as
    | { income?: number; spent?: number; saved?: number; spentChangePercent?: number | null; recurringMonthlyCost?: number }
    | undefined

  return (
    <>
      <PageHeader
        title="Insights"
        description="A short AI summary of your month, written from aggregated numbers only."
        actions={
          available.length > 0 && (
            <>
              <Select value={selected} onValueChange={setMonth}>
                <SelectTrigger className="w-40" aria-label="Month">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {available.map((m) => (
                    <SelectItem key={m} value={m}>
                      {formatMonth(m)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button variant="outline" onClick={regenerate} disabled={regenerating || insight.isFetching}>
                {regenerating ? <Loader2 className="size-4 animate-spin" /> : <RefreshCw className="size-4" />}
                Regenerate
              </Button>
            </>
          )
        }
      />

      {months.isPending && <Skeleton className="h-48 rounded-xl" />}
      {months.isError && <ErrorState error={months.error} onRetry={() => months.refetch()} />}
      {months.isSuccess && available.length === 0 && (
        <EmptyState
          title="Nothing to summarise yet"
          description="Upload a statement and come back for your monthly insight."
          action={
            <Button asChild>
              <Link to="/upload">Upload a statement</Link>
            </Button>
          }
        />
      )}

      {selected && (
        <div className="space-y-4">
          <Card className="bg-gradient-to-br from-indigo-50 to-card">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Lightbulb className="size-5 text-amber-500" /> {formatMonth(selected)} in a nutshell
              </CardTitle>
              <CardDescription className="flex items-center gap-1">
                <Lock className="size-3" /> Only totals are sent to the AI, never individual transactions.
              </CardDescription>
            </CardHeader>
            <CardContent>
              {(insight.isPending || regenerating) && (
                <div className="space-y-2">
                  <Skeleton className="h-4 w-full" />
                  <Skeleton className="h-4 w-11/12" />
                  <Skeleton className="h-4 w-4/5" />
                </div>
              )}
              {insight.isError && !regenerating && <ErrorState error={insight.error} onRetry={() => insight.refetch()} />}
              {insight.data && !regenerating && <p className="text-base leading-relaxed">{insight.data.summary}</p>}
            </CardContent>
          </Card>

          {figures && (
            <div className="grid gap-4 sm:grid-cols-4">
              <Figure label="Income" value={formatINR(figures.income)} />
              <Figure label="Spent" value={formatINR(figures.spent)} />
              <Figure label="Saved" value={formatINR(figures.saved)} />
              <Figure label="Spend vs last month" value={figures.spentChangePercent == null ? '—' : formatPercent(figures.spentChangePercent)} />
            </div>
          )}

          {insight.data && (
            <details className="rounded-xl border bg-card p-4 text-sm">
              <summary className="cursor-pointer font-medium">Exactly what the AI saw</summary>
              <pre className="mt-3 overflow-x-auto rounded-lg bg-muted p-3 text-xs">
                {JSON.stringify(insight.data.figures, null, 2)}
              </pre>
            </details>
          )}
        </div>
      )}
    </>
  )
}

function Figure({ label, value }: { label: string; value: string }) {
  return (
    <Card>
      <CardContent>
        <p className="text-sm text-muted-foreground">{label}</p>
        <p className="mt-1 text-xl font-semibold tabular-nums">{value}</p>
      </CardContent>
    </Card>
  )
}
