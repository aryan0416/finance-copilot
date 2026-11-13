import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'
import type { Dashboard } from '@/api/types'
import { formatINR } from '@/lib/format'
import { OTHER_GRAY } from './palette'
import { TooltipCard } from './ChartTooltip'

const MAX_SLICES = 6

/**
 * Spend share by category. Slices beyond the top six fold into "Other" (never a generated hue); each
 * category keeps its own stored colour so it looks the same on every page. The legend lists names and
 * amounts, so identity never depends on colour alone.
 */
export function CategoryDonut({ data }: { data: Dashboard['categories'] }) {
  const top = data.slice(0, MAX_SLICES)
  const rest = data.slice(MAX_SLICES)
  const slices = [
    ...top.map((c) => ({ name: c.name, amount: c.amount, color: c.color })),
    ...(rest.length
      ? [{ name: `Other (${rest.length})`, amount: rest.reduce((sum, c) => sum + c.amount, 0), color: OTHER_GRAY }]
      : []),
  ]
  const total = slices.reduce((sum, s) => sum + s.amount, 0)

  return (
    <div className="flex flex-col items-center gap-4 sm:flex-row">
      <div className="relative h-48 w-48 shrink-0">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={slices}
              dataKey="amount"
              nameKey="name"
              innerRadius="62%"
              outerRadius="100%"
              paddingAngle={1}
              stroke="var(--card)"
              strokeWidth={2}
              isAnimationActive={false}
            >
              {slices.map((s) => (
                <Cell key={s.name} fill={s.color} />
              ))}
            </Pie>
            <Tooltip
              content={({ active, payload }) => {
                if (!active || !payload?.length) return null
                const s = payload[0].payload as (typeof slices)[number]
                return (
                  <TooltipCard
                    rows={[{ label: s.name, value: `${formatINR(s.amount)} · ${((100 * s.amount) / total).toFixed(0)}%`, color: s.color }]}
                  />
                )
              }}
            />
          </PieChart>
        </ResponsiveContainer>
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-xs text-muted-foreground">Total</span>
          <span className="text-sm font-semibold tabular-nums">{formatINR(total)}</span>
        </div>
      </div>
      <ul className="w-full space-y-1.5 text-sm">
        {slices.map((s) => (
          <li key={s.name} className="flex items-center justify-between gap-3">
            <span className="flex min-w-0 items-center gap-2">
              <span className="size-2.5 shrink-0 rounded-sm" style={{ backgroundColor: s.color }} />
              <span className="truncate">{s.name}</span>
            </span>
            <span className="shrink-0 tabular-nums text-muted-foreground">
              {formatINR(s.amount)} <span className="text-xs">({((100 * s.amount) / total).toFixed(0)}%)</span>
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}
