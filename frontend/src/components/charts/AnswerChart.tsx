import { Bar, BarChart, CartesianGrid, Cell, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { AskResponse } from '@/api/types'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatINR, formatINRCompact } from '@/lib/format'
import { AXIS_TEXT, GRID, OTHER_GRAY, SERIES } from './palette'
import { TooltipCard } from './ChartTooltip'

const MONEY_COLUMN = /amount|spent|spend|total|income|sum|avg|average|cost|balance|value/i
const PIE_ORDER = Object.values(SERIES)

type Cell = string | number | boolean | null

function isMoney(column: string) {
  return MONEY_COLUMN.test(column)
}

function formatValue(column: string, value: Cell) {
  if (typeof value === 'number') return isMoney(column) ? formatINR(value) : value.toLocaleString('en-IN')
  return value == null ? '—' : String(value)
}

function humanize(column: string) {
  return column.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase())
}

/** Picks the label column (first non-numeric) and value column (first numeric) of a result. */
function shape(answer: AskResponse) {
  const numericIndex = answer.columns.findIndex((_, i) => answer.rows.some((r) => typeof r[i] === 'number'))
  const labelIndex = answer.columns.findIndex((_, i) => i !== numericIndex)
  return { numericIndex, labelIndex }
}

export function AnswerTable({ answer }: { answer: AskResponse }) {
  return (
    <div className="max-h-80 overflow-auto rounded-lg border">
      <Table>
        <TableHeader>
          <TableRow>
            {answer.columns.map((c) => (
              <TableHead key={c}>{humanize(c)}</TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {answer.rows.map((row, i) => (
            <TableRow key={i}>
              {row.map((value, j) => (
                <TableCell key={j} className={typeof value === 'number' ? 'text-right tabular-nums' : 'max-w-xs truncate'}>
                  {formatValue(answer.columns[j], value as Cell)}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}

/** Renders an Ask-AI answer in the chart type the backend chose; falls back to a table when unsure. */
export function AnswerChart({ answer }: { answer: AskResponse }) {
  if (answer.rows.length === 0) {
    return <p className="text-sm text-muted-foreground">No matching transactions.</p>
  }
  const { numericIndex, labelIndex } = shape(answer)

  if (answer.chartType === 'number') {
    const value = answer.rows[0][0] as Cell
    return (
      <div>
        <p className="text-xs text-muted-foreground">{humanize(answer.columns[0])}</p>
        <p className="text-4xl font-semibold tracking-tight tabular-nums">{formatValue(answer.columns[0], value ?? 0)}</p>
      </div>
    )
  }

  if (answer.chartType === 'table' || numericIndex < 0 || labelIndex < 0) {
    return <AnswerTable answer={answer} />
  }

  const valueColumn = answer.columns[numericIndex]
  const data = answer.rows.map((r) => ({ label: String(r[labelIndex] ?? '—'), value: Number(r[numericIndex] ?? 0) }))
  const money = isMoney(valueColumn)
  const tick = (v: number) => (money ? formatINRCompact(v) : v.toLocaleString('en-IN'))
  const tooltip = (
    <Tooltip
      cursor={answer.chartType === 'bar' ? { fill: 'var(--muted)' } : { stroke: AXIS_TEXT, strokeDasharray: '3 3' }}
      content={({ active, payload }) => {
        if (!active || !payload?.length) return null
        const p = payload[0].payload as (typeof data)[number]
        return <TooltipCard rows={[{ label: p.label, value: formatValue(valueColumn, p.value) }]} />
      }}
    />
  )

  if (answer.chartType === 'pie') {
    const total = data.reduce((sum, d) => sum + d.value, 0)
    return (
      <div className="flex flex-col items-center gap-4 sm:flex-row">
        <div className="h-52 w-52 shrink-0">
          <ResponsiveContainer>
            <PieChart>
              <Pie data={data} dataKey="value" nameKey="label" innerRadius="55%" outerRadius="100%" stroke="var(--card)" strokeWidth={2} isAnimationActive={false}>
                {data.map((d, i) => (
                  <Cell key={d.label} fill={PIE_ORDER[i] ?? OTHER_GRAY} />
                ))}
              </Pie>
              {tooltip}
            </PieChart>
          </ResponsiveContainer>
        </div>
        <ul className="w-full space-y-1.5 text-sm">
          {data.map((d, i) => (
            <li key={d.label} className="flex justify-between gap-3">
              <span className="flex items-center gap-2">
                <span className="size-2.5 rounded-sm" style={{ backgroundColor: PIE_ORDER[i] ?? OTHER_GRAY }} />
                {d.label}
              </span>
              <span className="tabular-nums text-muted-foreground">
                {formatValue(valueColumn, d.value)} ({total ? ((100 * d.value) / total).toFixed(0) : 0}%)
              </span>
            </li>
          ))}
        </ul>
      </div>
    )
  }

  const axes = (
    <>
      <CartesianGrid stroke={GRID} vertical={false} />
      <XAxis dataKey="label" tick={{ fill: AXIS_TEXT, fontSize: 12 }} axisLine={false} tickLine={false} interval="preserveStartEnd" />
      <YAxis tickFormatter={tick} tick={{ fill: AXIS_TEXT, fontSize: 12 }} axisLine={false} tickLine={false} width={64} />
    </>
  )

  return (
    <div>
      <p className="mb-2 text-xs text-muted-foreground">{humanize(valueColumn)}</p>
      <div className="h-64">
        <ResponsiveContainer>
          {answer.chartType === 'line' ? (
            <LineChart data={data} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
              {axes}
              {tooltip}
              <Line type="monotone" dataKey="value" stroke={SERIES.blue} strokeWidth={2} dot={{ r: 4 }} activeDot={{ r: 5 }} />
            </LineChart>
          ) : (
            <BarChart data={data} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
              {axes}
              {tooltip}
              <Bar dataKey="value" fill={SERIES.blue} radius={[4, 4, 0, 0]} maxBarSize={48} />
            </BarChart>
          )}
        </ResponsiveContainer>
      </div>
    </div>
  )
}
