import { useRef, useState, type DragEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, FileText, Loader2, UploadCloud, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { PageHeader } from '@/components/common/PageHeader'
import { ErrorState } from '@/components/common/States'
import { statementApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import type { ImportSummary } from '@/api/types'
import { formatDate, formatPercent } from '@/lib/format'
import { cn } from '@/lib/utils'

const ACCEPT = '.csv,.pdf,text/csv,application/pdf'
const MAX_BYTES = 10 * 1024 * 1024

export function UploadPage() {
  const queryClient = useQueryClient()
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const [progress, setProgress] = useState(0)
  const [localError, setLocalError] = useState<string | null>(null)
  const history = useQuery({ queryKey: ['statements'], queryFn: statementApi.list })

  const upload = useMutation({
    mutationFn: (file: File) => statementApi.upload(file, setProgress),
    onSuccess: () => {
      // Everything derived from transactions is now stale.
      for (const key of ['statements', 'transactions', 'dashboard', 'subscriptions']) {
        void queryClient.invalidateQueries({ queryKey: [key] })
      }
    },
  })

  function start(file: File | undefined) {
    if (!file) return
    setLocalError(null)
    if (!/\.(csv|pdf)$/i.test(file.name)) {
      setLocalError('Please choose a .csv or .pdf statement.')
      return
    }
    if (file.size > MAX_BYTES) {
      setLocalError('That file is larger than 10 MB.')
      return
    }
    setProgress(0)
    upload.mutate(file)
  }

  function onDrop(e: DragEvent) {
    e.preventDefault()
    setDragging(false)
    start(e.dataTransfer.files[0])
  }

  const uploading = upload.isPending
  return (
    <>
      <PageHeader
        title="Upload a statement"
        description="CSV or text-based PDF from any Indian bank. Columns are detected automatically."
      />

      <Card
        onDragOver={(e) => {
          e.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={cn(
          'flex flex-col items-center justify-center border-2 border-dashed px-6 py-12 text-center transition-colors',
          dragging ? 'border-primary bg-accent' : 'border-border',
        )}
      >
        {uploading ? (
          <div className="w-full max-w-sm space-y-3">
            <Loader2 className="mx-auto size-8 animate-spin text-primary" />
            <p className="font-medium">{progress < 100 ? `Uploading… ${progress}%` : 'Parsing and categorising…'}</p>
            <Progress value={progress < 100 ? progress : 100} className={progress >= 100 ? 'animate-pulse' : ''} />
            <p className="text-xs text-muted-foreground">
              New merchants are sent to the AI in batches; this can take a few seconds the first time.
            </p>
          </div>
        ) : (
          <>
            <UploadCloud className="mb-3 size-10 text-primary" />
            <p className="font-medium">Drag &amp; drop your statement here</p>
            <p className="mt-1 text-sm text-muted-foreground">or</p>
            <Button className="mt-3" onClick={() => inputRef.current?.click()}>
              Choose file
            </Button>
            <p className="mt-3 text-xs text-muted-foreground">
              Try the files in <code className="rounded bg-muted px-1">sample-data/</code>. Max 10 MB.
            </p>
          </>
        )}
        <input
          ref={inputRef}
          type="file"
          accept={ACCEPT}
          className="hidden"
          onChange={(e) => {
            start(e.target.files?.[0])
            e.target.value = ''
          }}
        />
      </Card>

      {(localError || upload.isError) && (
        <div role="alert" className="mt-4 flex items-start gap-2 rounded-lg border border-destructive/30 bg-destructive/5 p-4 text-sm">
          <XCircle className="mt-0.5 size-4 shrink-0 text-destructive" />
          <span>{localError ?? errorMessage(upload.error, 'Upload failed')}</span>
        </div>
      )}

      {upload.data && <ImportResult summary={upload.data} />}

      <Card className="mt-6">
        <CardHeader>
          <CardTitle>Upload history</CardTitle>
        </CardHeader>
        <CardContent>
          {history.isError && <ErrorState error={history.error} onRetry={() => history.refetch()} />}
          {history.isPending && <p className="text-sm text-muted-foreground">Loading…</p>}
          {history.data?.length === 0 && <p className="text-sm text-muted-foreground">No statements uploaded yet.</p>}
          {!!history.data?.length && (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>File</TableHead>
                    <TableHead>Period</TableHead>
                    <TableHead className="text-right">Imported</TableHead>
                    <TableHead className="text-right">Duplicates</TableHead>
                    <TableHead className="text-right">Failed</TableHead>
                    <TableHead>Uploaded</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {history.data.map((s) => (
                    <TableRow key={s.id}>
                      <TableCell className="flex items-center gap-2 font-medium">
                        <FileText className="size-4 text-muted-foreground" /> {s.fileName}
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-muted-foreground">
                        {formatDate(s.periodStart)} – {formatDate(s.periodEnd)}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">{s.imported}</TableCell>
                      <TableCell className="text-right tabular-nums">{s.duplicates}</TableCell>
                      <TableCell className="text-right tabular-nums">{s.failed}</TableCell>
                      <TableCell className="whitespace-nowrap text-muted-foreground">
                        {new Date(s.uploadedAt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>
    </>
  )
}

function ImportResult({ summary }: { summary: ImportSummary }) {
  const c = summary.categorization
  const total = c.byRule + c.byCache + c.byAi + c.uncategorised
  const segments = [
    { label: 'Rules', value: c.byRule, className: 'bg-[#2a78d6]' },
    { label: 'Cache', value: c.byCache, className: 'bg-[#1baf7a]' },
    { label: 'AI', value: c.byAi, className: 'bg-[#4a3aa7]' },
    { label: 'Uncategorised', value: c.uncategorised, className: 'bg-stone-300' },
  ]
  return (
    <Card className="mt-4">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <CheckCircle2 className="size-5 text-emerald-600" /> Imported {summary.fileName}
        </CardTitle>
        <CardDescription>
          {formatDate(summary.periodStart)} – {formatDate(summary.periodEnd)}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="grid grid-cols-3 gap-3">
          <Stat label="New transactions" value={summary.imported} />
          <Stat label="Duplicates skipped" value={summary.duplicates} />
          <Stat label="Rows failed" value={summary.failed} tone={summary.failed ? 'warn' : undefined} />
        </div>

        {total > 0 && (
          <div>
            <div className="mb-2 flex justify-between text-sm">
              <span className="font-medium">How they were categorised</span>
              <span className="text-muted-foreground">{formatPercent(c.withoutAiPercent)} without an AI call</span>
            </div>
            <div className="flex h-2.5 gap-0.5 overflow-hidden rounded-full">
              {segments
                .filter((s) => s.value > 0)
                .map((s) => (
                  <div key={s.label} className={s.className} style={{ width: `${(100 * s.value) / total}%` }} title={`${s.label}: ${s.value}`} />
                ))}
            </div>
            <ul className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
              {segments.map((s) => (
                <li key={s.label} className="flex items-center gap-1.5">
                  <span className={`size-2 rounded-sm ${s.className}`} /> {s.label}: {s.value}
                </li>
              ))}
            </ul>
          </div>
        )}

        {summary.failedRows.length > 0 && (
          <details className="rounded-lg bg-muted/50 p-3 text-sm">
            <summary className="cursor-pointer font-medium">Rows that could not be read ({summary.failed})</summary>
            <ul className="mt-2 space-y-1 text-muted-foreground">
              {summary.failedRows.map((f) => (
                <li key={f.line}>
                  Line {f.line}: {f.reason}
                </li>
              ))}
            </ul>
          </details>
        )}

        <div className="flex gap-2">
          <Button asChild>
            <Link to="/">View dashboard</Link>
          </Button>
          <Button variant="outline" asChild>
            <Link to="/transactions">Review transactions</Link>
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

function Stat({ label, value, tone }: { label: string; value: number; tone?: 'warn' }) {
  return (
    <div className="rounded-lg bg-muted/50 p-3">
      <p className={cn('text-2xl font-semibold tabular-nums', tone === 'warn' && 'text-amber-600')}>{value}</p>
      <p className="text-xs text-muted-foreground">{label}</p>
    </div>
  )
}
