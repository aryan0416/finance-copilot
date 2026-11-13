import { useState } from 'react'
import { Link } from 'react-router-dom'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Loader2, Search, X } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { PageHeader } from '@/components/common/PageHeader'
import { EmptyState, ErrorState } from '@/components/common/States'
import { CategoryBadge, SourceTag } from '@/components/common/CategoryBadge'
import { transactionApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import type { Category, CategorySource, Direction, Transaction, TransactionFilters } from '@/api/types'
import { useCategories } from '@/hooks/useCategories'
import { useDebounced } from '@/hooks/useDebounced'
import { formatDate, formatINRPrecise } from '@/lib/format'

const PAGE_SIZE = 25
const ALL = 'all'

interface PendingChange {
  transaction: Transaction
  category: Category
}

export function TransactionsPage() {
  const queryClient = useQueryClient()
  const categories = useCategories()
  const [search, setSearch] = useState('')
  const [filters, setFilters] = useState<Omit<TransactionFilters, 'q' | 'page' | 'size'>>({})
  const [page, setPage] = useState(0)
  const [pending, setPending] = useState<PendingChange | null>(null)
  const q = useDebounced(search.trim())

  const params: TransactionFilters = { ...filters, q: q || undefined, page, size: PAGE_SIZE }
  const query = useQuery({
    queryKey: ['transactions', params],
    queryFn: () => transactionApi.list(params),
    placeholderData: keepPreviousData,
  })

  const recategorize = useMutation({
    mutationFn: ({ transaction, category, createRule }: PendingChange & { createRule: boolean }) =>
      transactionApi.recategorize(transaction.id, category.id, createRule),
    onSuccess: (result, { category }) => {
      setPending(null)
      void queryClient.invalidateQueries({ queryKey: ['transactions'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      toast.success(
        result.ruleCreated
          ? `Rule created: ${result.transaction.merchant} → ${category.name}` +
              (result.similarUpdated ? ` (${result.similarUpdated} more updated)` : '')
          : `Moved to ${category.name}`,
      )
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  function updateFilter<K extends keyof typeof filters>(key: K, value: (typeof filters)[K]) {
    setFilters((f) => ({ ...f, [key]: value }))
    setPage(0)
  }

  const hasFilters = q !== '' || Object.values(filters).some((v) => v !== undefined && v !== '')
  const data = query.data

  return (
    <>
      <PageHeader title="Transactions" description="Search, filter and fix categories. Fixes can become rules." />

      <Card className="mb-4 flex-row flex-wrap items-center gap-2 p-3">
        <div className="relative min-w-48 flex-1">
          <Search className="absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Search merchant or description"
            className="pl-8"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value)
              setPage(0)
            }}
          />
        </div>
        <Input
          type="date"
          aria-label="From date"
          className="w-36"
          value={filters.from ?? ''}
          onChange={(e) => updateFilter('from', e.target.value || undefined)}
        />
        <Input
          type="date"
          aria-label="To date"
          className="w-36"
          value={filters.to ?? ''}
          onChange={(e) => updateFilter('to', e.target.value || undefined)}
        />
        <Select
          value={filters.categoryId ? String(filters.categoryId) : ALL}
          onValueChange={(v) => updateFilter('categoryId', v === ALL ? undefined : Number(v))}
        >
          <SelectTrigger className="w-44" aria-label="Category">
            <SelectValue placeholder="Category" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All categories</SelectItem>
            {categories.data?.map((c) => (
              <SelectItem key={c.id} value={String(c.id)}>
                {c.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select
          value={filters.direction ?? ALL}
          onValueChange={(v) => updateFilter('direction', v === ALL ? undefined : (v as Direction))}
        >
          <SelectTrigger className="w-32" aria-label="Direction">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>In &amp; out</SelectItem>
            <SelectItem value="DEBIT">Money out</SelectItem>
            <SelectItem value="CREDIT">Money in</SelectItem>
          </SelectContent>
        </Select>
        <Select
          value={filters.source ?? ALL}
          onValueChange={(v) => updateFilter('source', v === ALL ? undefined : (v as CategorySource))}
        >
          <SelectTrigger className="w-36" aria-label="Categorised by">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>Any source</SelectItem>
            <SelectItem value="RULE">By rule</SelectItem>
            <SelectItem value="CACHE">By cache</SelectItem>
            <SelectItem value="AI">By AI</SelectItem>
            <SelectItem value="USER">By you</SelectItem>
            <SelectItem value="NONE">Uncategorised</SelectItem>
          </SelectContent>
        </Select>
        {hasFilters && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setSearch('')
              setFilters({})
              setPage(0)
            }}
          >
            <X className="size-4" /> Clear
          </Button>
        )}
      </Card>

      {query.isError && <ErrorState error={query.error} onRetry={() => query.refetch()} />}

      {query.isPending && (
        <Card className="space-y-2 p-4">
          {Array.from({ length: 8 }, (_, i) => (
            <Skeleton key={i} className="h-9" />
          ))}
        </Card>
      )}

      {data && data.totalElements === 0 && (
        hasFilters ? (
          <EmptyState title="No matching transactions" description="Try widening the date range or clearing filters." />
        ) : (
          <EmptyState
            title="No transactions yet"
            description="Upload a statement to get started."
            action={
              <Button asChild>
                <Link to="/upload">Upload a statement</Link>
              </Button>
            }
          />
        )
      )}

      {data && data.totalElements > 0 && (
        <Card className="overflow-hidden p-0">
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-28">Date</TableHead>
                  <TableHead>Merchant</TableHead>
                  <TableHead className="w-60">Category</TableHead>
                  <TableHead className="w-32 text-right">Amount</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className={query.isFetching ? 'opacity-60' : undefined}>
                {data.content.map((t) => (
                  <TableRow key={t.id}>
                    <TableCell className="whitespace-nowrap text-muted-foreground">{formatDate(t.date)}</TableCell>
                    <TableCell className="max-w-xs">
                      <p className="truncate font-medium">{t.merchant}</p>
                      <p className="truncate text-xs text-muted-foreground" title={t.description}>
                        {t.description}
                      </p>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <Select
                          value={t.category ? String(t.category.id) : undefined}
                          onValueChange={(v) => {
                            const category = categories.data?.find((c) => c.id === Number(v))
                            if (category && category.id !== t.category?.id) setPending({ transaction: t, category })
                          }}
                        >
                          <SelectTrigger size="sm" className="w-44 border-transparent shadow-none hover:border-input" aria-label={`Category for ${t.merchant}`}>
                            <SelectValue placeholder="Choose…">
                              <CategoryBadge category={t.category} />
                            </SelectValue>
                          </SelectTrigger>
                          <SelectContent>
                            {categories.data?.map((c) => (
                              <SelectItem key={c.id} value={String(c.id)}>
                                <CategoryBadge category={c} />
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        <SourceTag source={t.categorySource} />
                      </div>
                    </TableCell>
                    <TableCell
                      className={`text-right font-medium whitespace-nowrap tabular-nums ${t.direction === 'CREDIT' ? 'text-emerald-600' : ''}`}
                    >
                      {t.direction === 'CREDIT' ? '+' : '−'}
                      {formatINRPrecise(t.amount)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <div className="flex items-center justify-between border-t px-4 py-3 text-sm text-muted-foreground">
            <span>
              {data.page * data.size + 1}–{Math.min((data.page + 1) * data.size, data.totalElements)} of {data.totalElements}
            </span>
            <div className="flex items-center gap-2">
              <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                <ChevronLeft className="size-4" /> Prev
              </Button>
              <span>
                Page {data.page + 1} of {data.totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                disabled={page + 1 >= data.totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Next <ChevronRight className="size-4" />
              </Button>
            </div>
          </div>
        </Card>
      )}

      <Dialog open={pending != null} onOpenChange={(open) => !open && !recategorize.isPending && setPending(null)}>
        <DialogContent>
          {pending && (
            <>
              <DialogHeader>
                <DialogTitle>Change category</DialogTitle>
                <DialogDescription>
                  Move <span className="font-medium text-foreground">{pending.transaction.merchant}</span> (
                  {formatINRPrecise(pending.transaction.amount)} on {formatDate(pending.transaction.date)}) to{' '}
                  <span className="font-medium text-foreground">{pending.category.name}</span>?
                </DialogDescription>
              </DialogHeader>
              <p className="text-sm text-muted-foreground">
                Creating a rule also recategorises your other {pending.transaction.merchant} transactions and every
                future import, without an AI call.
              </p>
              <DialogFooter className="gap-2 sm:gap-2">
                <Button
                  variant="outline"
                  disabled={recategorize.isPending}
                  onClick={() => recategorize.mutate({ ...pending, createRule: false })}
                >
                  Just this one
                </Button>
                <Button disabled={recategorize.isPending} onClick={() => recategorize.mutate({ ...pending, createRule: true })}>
                  {recategorize.isPending && <Loader2 className="size-4 animate-spin" />}
                  Always use for {pending.transaction.merchant}
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </>
  )
}
