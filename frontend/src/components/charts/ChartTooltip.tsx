import type { ReactNode } from 'react'

interface Row {
  label: string
  value: string
  color?: string
}

/** Shared tooltip body: text in ink colours, a colour swatch carries series identity. */
export function TooltipCard({ title, rows }: { title?: ReactNode; rows: Row[] }) {
  return (
    <div className="rounded-lg border bg-popover px-3 py-2 text-xs shadow-md">
      {title && <p className="mb-1 font-medium text-foreground">{title}</p>}
      {rows.map((row) => (
        <div key={row.label} className="flex items-center justify-between gap-4">
          <span className="flex items-center gap-1.5 text-muted-foreground">
            {row.color && <span className="size-2 rounded-full" style={{ backgroundColor: row.color }} />}
            {row.label}
          </span>
          <span className="font-medium tabular-nums text-foreground">{row.value}</span>
        </div>
      ))}
    </div>
  )
}
