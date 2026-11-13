import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, ArrowDownRight, ArrowUpRight, CalendarClock, Cpu, PiggyBank, TrendingUp, Wallet } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { PageHeader } from '@/components/common/PageHeader'
import { CardSkeletons, EmptyState, ErrorState } from '@/components/common/States'
import { TrendChart } from '@/components/charts/TrendChart'
import { CategoryDonut } from '@/components/charts/CategoryDonut'
import { dashboardApi } from '@/api/endpoints'
import type { Dashboard } from '@/api/types'
import { formatDate, formatINR, formatMonth, formatPercent, frequencyLabel } from '@/lib/format'

export function DashboardPage() {
  const [month, setMonth] = useState<string | undefined>()
  const query = useQuery({ queryKey: ['dashboard', month ?? 'latest'], queryFn: () => dashboardApi.get(month) })

  const data = query.data
  const hasData = !!data && data.availableMonths.length > 0

  return (
    <>
      <PageHeader
        title="Dashboard"
        description={hasData ? `Your money in ${formatMonth(data.month)}` : 'Your spending at a glance'}
        actions={
          hasData && (
            <Select value={data.month} onValueChange={setMonth}>
              <SelectTrigger className="w-40" aria-label="Month">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {data.availableMonths.map((m) => (
                  <SelectItem key={m} value={m}>
                    {formatMonth(m)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )
        }
      />

      {query.isPending && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <CardSkeletons count={4} />
          <CardSkeletons count={2} className="h-72 sm:col-span-2" />
        </div>
      )}
      {query.isError && <ErrorState error={query.error} onRetry={() => query.refetch()} />}
      {data && !hasData && (
        <EmptyState
          title="No transactions yet"
          description="Upload a bank statement (CSV or PDF) to see your dashboard."
          action={
            <Button asChild>
              <Link to="/upload">Upload a statement</Link>
            </Button>
          }
        />
      )}
      {hasData && <DashboardContent data={data} />}
    </>
  )
}

function DashboardContent({ data }: { data: Dashboard }) {
  const { kpis, categorisation } = data
  const change = kpis.spentChangePercent
  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <KpiCard
          icon={Wallet}
          label="Spent this month"
          value={formatINR(kpis.spent)}
          hint={
            change == null ? (
              'No previous month to compare'
            ) : (
              <span className={change > 0 ? 'text-red-600' : 'text-emerald-600'}>
                {change > 0 ? <ArrowUpRight className="inline size-3.5" /> : <ArrowDownRight className="inline size-3.5" />}
                {Math.abs(change)}% vs last month
              </span>
            )
          }
        />
        <KpiCard icon={TrendingUp} label="Income" value={formatINR(kpis.income)} hint={`${kpis.transactionCount} transactions`} />
        <KpiCard
          icon={PiggyBank}
          label="Savings rate"
          value={formatPercent(kpis.savingsRate)}
          hint={kpis.savingsRate == null ? 'No income this month' : `${formatINR(kpis.income - kpis.spent)} saved`}
        />
        <KpiCard
          icon={Cpu}
          label="Auto-categorised"
          value={formatPercent(categorisation.autoCategorisedPercent)}
          hint={`${formatPercent(categorisation.withoutAiPercent)} without an AI call · ${categorisation.aiCalls} AI calls`}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-5">
        <Card className="lg:col-span-3">
          <CardHeader>
            <CardTitle>Income vs spending</CardTitle>
            <CardDescription>Last 6 months. Spending excludes transfers and investments.</CardDescription>
          </CardHeader>
          <CardContent>
            <TrendChart data={data.monthly} />
          </CardContent>
        </Card>
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Where it went</CardTitle>
            <CardDescription>Spending by category, {formatMonth(data.month)}</CardDescription>
          </CardHeader>
          <CardContent>
            {data.categories.length ? (
              <CategoryDonut data={data.categories} />
            ) : (
              <p className="py-8 text-center text-sm text-muted-foreground">No spending this month.</p>
            )}
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <TopMerchants data={data.topMerchants} />
        <UpcomingCharges data={data.upcoming} />
        <Anomalies data={data.anomalies} />
      </div>
    </div>
  )
}

function KpiCard({
  icon: Icon,
  label,
  value,
  hint,
}: {
  icon: typeof Wallet
  label: string
  value: string
  hint: React.ReactNode
}) {
  return (
    <Card>
      <CardContent className="space-y-1">
        <div className="flex items-center justify-between text-sm text-muted-foreground">
          {label}
          <Icon className="size-4" />
        </div>
        <p className="text-2xl font-semibold tabular-nums tracking-tight">{value}</p>
        <p className="text-xs text-muted-foreground">{hint}</p>
      </CardContent>
    </Card>
  )
}

function TopMerchants({ data }: { data: Dashboard['topMerchants'] }) {
  const max = Math.max(1, ...data.map((m) => m.amount))
  return (
    <Card>
      <CardHeader>
        <CardTitle>Top merchants</CardTitle>
      </CardHeader>
      <CardContent>
        {data.length === 0 ? (
          <p className="text-sm text-muted-foreground">No spending this month.</p>
        ) : (
          <ul className="space-y-3">
            {data.map((m) => (
              <li key={m.merchant}>
                <div className="flex justify-between text-sm">
                  <span className="truncate font-medium">{m.merchant}</span>
                  <span className="tabular-nums text-muted-foreground">{formatINR(m.amount)}</span>
                </div>
                <div className="mt-1 h-1.5 rounded-full bg-muted">
                  <div className="h-1.5 rounded-full bg-[#2a78d6]" style={{ width: `${(100 * m.amount) / max}%` }} />
                </div>
                <p className="mt-0.5 text-xs text-muted-foreground">{m.count} {m.count === 1 ? 'payment' : 'payments'}</p>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

function UpcomingCharges({ data }: { data: Dashboard['upcoming'] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <CalendarClock className="size-4 text-muted-foreground" /> Upcoming charges
        </CardTitle>
        <CardDescription>Subscriptions due in the next 30 days</CardDescription>
      </CardHeader>
      <CardContent>
        {data.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nothing due soon.</p>
        ) : (
          <ul className="divide-y">
            {data.slice(0, 6).map((s) => (
              <li key={s.merchant} className="flex items-center justify-between py-2 text-sm">
                <div>
                  <p className="font-medium">{s.merchant}</p>
                  <p className="text-xs text-muted-foreground">
                    {frequencyLabel[s.frequency]} · {formatDate(s.expectedDate)}
                  </p>
                </div>
                <span className="tabular-nums">{formatINR(s.amount)}</span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

function Anomalies({ data }: { data: Dashboard['anomalies'] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <AlertTriangle className="size-4 text-amber-600" /> Unusual spends
        </CardTitle>
        <CardDescription>Above average + 2σ for their category (last 3 months)</CardDescription>
      </CardHeader>
      <CardContent>
        {data.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nothing unusual. Nice.</p>
        ) : (
          <ul className="divide-y">
            {data.slice(0, 5).map((a) => (
              <li key={a.transactionId} className="py-2 text-sm">
                <div className="flex justify-between">
                  <span className="font-medium">{a.merchant}</span>
                  <span className="font-medium tabular-nums">{formatINR(a.amount)}</span>
                </div>
                <p className="text-xs text-muted-foreground">
                  {formatDate(a.date)} · {a.category} usually ~{formatINR(a.categoryAverage)}
                </p>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
