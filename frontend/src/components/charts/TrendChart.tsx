import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { Dashboard } from '@/api/types'
import { formatINR, formatINRCompact, formatMonth } from '@/lib/format'
import { AXIS_TEXT, GRID, SERIES } from './palette'
import { TooltipCard } from './ChartTooltip'

const SPENT = SERIES.blue
const INCOME = SERIES.aqua

/** Money in vs out per month: two series on one shared ₹ axis. */
export function TrendChart({ data }: { data: Dashboard['monthly'] }) {
  const rows = data.map((m) => ({ ...m, label: formatMonth(m.month, true) }))
  return (
    <div>
      <div className="mb-2 flex gap-4 text-xs text-muted-foreground" aria-hidden>
        <LegendDot color={SPENT} label="Spent" />
        <LegendDot color={INCOME} label="Income" />
      </div>
      <div className="h-64">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={rows} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
            <CartesianGrid stroke={GRID} vertical={false} />
            <XAxis dataKey="label" tick={{ fill: AXIS_TEXT, fontSize: 12 }} axisLine={false} tickLine={false} />
            <YAxis
              tickFormatter={formatINRCompact}
              tick={{ fill: AXIS_TEXT, fontSize: 12 }}
              axisLine={false}
              tickLine={false}
              width={64}
            />
            <Tooltip
              cursor={{ stroke: AXIS_TEXT, strokeDasharray: '3 3' }}
              content={({ active, payload }) => {
                if (!active || !payload?.length) return null
                const m = payload[0].payload as (typeof rows)[number]
                return (
                  <TooltipCard
                    title={formatMonth(m.month)}
                    rows={[
                      { label: 'Spent', value: formatINR(m.spent), color: SPENT },
                      { label: 'Income', value: formatINR(m.income), color: INCOME },
                      ...(m.spentChangePercent == null
                        ? []
                        : [{ label: 'Spend vs prev.', value: `${m.spentChangePercent > 0 ? '+' : ''}${m.spentChangePercent}%` }]),
                    ]}
                  />
                )
              }}
            />
            <Line type="monotone" dataKey="spent" name="Spent" stroke={SPENT} strokeWidth={2} dot={{ r: 4 }} activeDot={{ r: 5 }} />
            <Line type="monotone" dataKey="income" name="Income" stroke={INCOME} strokeWidth={2} dot={{ r: 4 }} activeDot={{ r: 5 }} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

function LegendDot({ color, label }: { color: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className="h-0.5 w-4 rounded" style={{ backgroundColor: color }} />
      {label}
    </span>
  )
}
