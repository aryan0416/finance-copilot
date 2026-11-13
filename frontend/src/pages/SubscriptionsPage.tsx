import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Skeleton } from '@/components/ui/skeleton'
import { PageHeader } from '@/components/common/PageHeader'
import { EmptyState, ErrorState } from '@/components/common/States'
import { CategoryBadge } from '@/components/common/CategoryBadge'
import { subscriptionApi } from '@/api/endpoints'
import { formatDate, formatINR, formatINRPrecise, frequencyLabel } from '@/lib/format'

export function SubscriptionsPage() {
  const query = useQuery({ queryKey: ['subscriptions'], queryFn: subscriptionApi.list })
  const subs = query.data ?? []
  const active = subs.filter((s) => s.status === 'ACTIVE')
  const yearly = active.reduce((sum, s) => sum + s.yearlyCost, 0)

  return (
    <>
      <PageHeader
        title="Subscriptions & recurring payments"
        description="Detected from your history: 3+ charges of a similar amount (±10%) at a weekly, monthly or yearly rhythm."
      />

      {query.isPending && <Skeleton className="h-64 rounded-xl" />}
      {query.isError && <ErrorState error={query.error} onRetry={() => query.refetch()} />}
      {query.isSuccess && subs.length === 0 && (
        <EmptyState
          title="No recurring charges found yet"
          description="Detection needs at least three charges per merchant, so upload two or three months of statements."
          action={
            <Button asChild>
              <Link to="/upload">Upload statements</Link>
            </Button>
          }
        />
      )}

      {subs.length > 0 && (
        <>
          <div className="mb-4 grid gap-4 sm:grid-cols-3">
            <Summary label="Active recurring payments" value={String(active.length)} />
            <Summary label="Monthly equivalent" value={formatINR(yearly / 12)} />
            <Summary label="Yearly cost" value={formatINR(yearly)} />
          </div>
          <Card className="overflow-hidden p-0">
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Merchant</TableHead>
                    <TableHead>Category</TableHead>
                    <TableHead>Frequency</TableHead>
                    <TableHead className="text-right">Avg. amount</TableHead>
                    <TableHead>Next expected</TableHead>
                    <TableHead className="text-right">Yearly cost</TableHead>
                    <TableHead>Status</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {subs.map((s) => (
                    <TableRow key={s.id} className={s.status === 'INACTIVE' ? 'opacity-60' : undefined}>
                      <TableCell>
                        <p className="font-medium">{s.merchant}</p>
                        <p className="text-xs text-muted-foreground">
                          {s.occurrences} charges, last {formatDate(s.lastChargeDate)}
                        </p>
                      </TableCell>
                      <TableCell>
                        <CategoryBadge category={s.category} />
                      </TableCell>
                      <TableCell>{frequencyLabel[s.frequency]}</TableCell>
                      <TableCell className="text-right tabular-nums">{formatINRPrecise(s.averageAmount)}</TableCell>
                      <TableCell className="whitespace-nowrap">{formatDate(s.nextExpectedDate)}</TableCell>
                      <TableCell className="text-right font-medium tabular-nums">{formatINR(s.yearlyCost)}</TableCell>
                      <TableCell>
                        <Badge variant={s.status === 'ACTIVE' ? 'default' : 'secondary'}>
                          {s.status === 'ACTIVE' ? 'Active' : 'Stopped?'}
                        </Badge>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </Card>
        </>
      )}
    </>
  )
}

function Summary({ label, value }: { label: string; value: string }) {
  return (
    <Card>
      <CardContent>
        <p className="text-sm text-muted-foreground">{label}</p>
        <p className="mt-1 text-2xl font-semibold tabular-nums">{value}</p>
      </CardContent>
    </Card>
  )
}
