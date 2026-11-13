import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Code2, Loader2, SendHorizontal, ShieldCheck, Sparkles, Table2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { PageHeader } from '@/components/common/PageHeader'
import { AnswerChart, AnswerTable } from '@/components/charts/AnswerChart'
import { aiApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import type { AskResponse } from '@/api/types'

const SUGGESTIONS = [
  'How much did I spend on Swiggy in October?',
  'Top 5 categories last 3 months',
  'Monthly spending trend this year',
  'What share of my spending goes to each category?',
  'My 10 biggest purchases',
  'How much do I spend on cabs per month?',
]

interface Exchange {
  id: number
  question: string
  answer?: AskResponse
  error?: string
}

export function AskAiPage() {
  const [question, setQuestion] = useState('')
  const [history, setHistory] = useState<Exchange[]>([])
  const bottomRef = useRef<HTMLDivElement>(null)

  const ask = useMutation({ mutationFn: (q: string) => aiApi.ask(q) })

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [history])

  function submit(text: string) {
    const q = text.trim()
    if (!q || ask.isPending) return
    const id = Date.now()
    setHistory((h) => [...h, { id, question: q }])
    setQuestion('')
    ask.mutate(q, {
      onSuccess: (answer) => setHistory((h) => h.map((e) => (e.id === id ? { ...e, answer } : e))),
      onError: (error) => setHistory((h) => h.map((e) => (e.id === id ? { ...e, error: errorMessage(error) } : e))),
    })
  }

  return (
    <>
      <PageHeader
        title="Ask AI"
        description="Ask about your spending in plain English. Answers come from your data via validated, read-only SQL."
      />

      {history.length === 0 && (
        <Card className="mb-4">
          <CardContent className="space-y-4">
            <div className="flex items-start gap-3">
              <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-accent text-accent-foreground">
                <Sparkles className="size-5" />
              </div>
              <div>
                <p className="font-medium">Try one of these</p>
                <p className="text-sm text-muted-foreground">
                  The AI writes a query against your transactions only; it&apos;s checked before it runs.
                </p>
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              {SUGGESTIONS.map((s) => (
                <Button key={s} variant="outline" size="sm" className="h-auto py-1.5 whitespace-normal" onClick={() => submit(s)}>
                  {s}
                </Button>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      <div className="space-y-4">
        {history.map((exchange) => (
          <div key={exchange.id} className="space-y-2">
            <div className="flex justify-end">
              <p className="max-w-[80%] rounded-2xl rounded-br-sm bg-primary px-4 py-2 text-sm text-primary-foreground">
                {exchange.question}
              </p>
            </div>
            {!exchange.answer && !exchange.error && (
              <p className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="size-4 animate-spin" /> Thinking: writing and checking a query…
              </p>
            )}
            {exchange.error && (
              <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm">
                {exchange.error}
              </p>
            )}
            {exchange.answer && <AnswerCard answer={exchange.answer} />}
          </div>
        ))}
        <div ref={bottomRef} />
      </div>

      <form
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          submit(question)
        }}
        className="sticky bottom-4 mt-6 flex gap-2 rounded-xl border bg-card p-2 shadow-sm"
      >
        <Input
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="e.g. How much did I spend on food last month?"
          maxLength={500}
          className="border-0 shadow-none focus-visible:ring-0"
          aria-label="Your question"
        />
        <Button type="submit" disabled={!question.trim() || ask.isPending}>
          {ask.isPending ? <Loader2 className="size-4 animate-spin" /> : <SendHorizontal className="size-4" />}
          Ask
        </Button>
      </form>
    </>
  )
}

function AnswerCard({ answer }: { answer: AskResponse }) {
  const [showSql, setShowSql] = useState(false)
  const [asTable, setAsTable] = useState(false)
  return (
    <Card>
      <CardContent className="space-y-4">
        {answer.explanation && <p className="text-sm">{answer.explanation}</p>}
        {asTable ? <AnswerTable answer={answer} /> : <AnswerChart answer={answer} />}
        <div className="flex flex-wrap items-center gap-2 border-t pt-3">
          <Button variant="ghost" size="sm" onClick={() => setShowSql((v) => !v)}>
            <Code2 className="size-4" /> {showSql ? 'Hide SQL' : 'Show SQL'}
          </Button>
          {answer.chartType !== 'table' && answer.chartType !== 'number' && (
            <Button variant="ghost" size="sm" onClick={() => setAsTable((v) => !v)}>
              <Table2 className="size-4" /> {asTable ? 'Show chart' : 'View as table'}
            </Button>
          )}
          <span className="ml-auto flex items-center gap-1 text-xs text-muted-foreground">
            <ShieldCheck className="size-3.5 text-emerald-600" /> Validated · read-only · {answer.rows.length} rows ·{' '}
            {(answer.latencyMs / 1000).toFixed(1)}s
          </span>
        </div>
        {showSql && (
          <pre className="rounded-lg bg-slate-950 p-3 font-mono text-xs leading-relaxed break-words whitespace-pre-wrap text-slate-100">
            <code>{answer.sql}</code>
          </pre>
        )}
      </CardContent>
    </Card>
  )
}
