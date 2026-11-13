import type { Category, CategorySource } from '@/api/types'

const SOURCE_LABEL: Record<CategorySource, string> = {
  RULE: 'Rule',
  CACHE: 'Cache',
  AI: 'AI',
  USER: 'You',
  NONE: '—',
}

/** Category name with its colour dot; the dot is decoration, the name carries identity. */
export function CategoryBadge({ category }: { category: Category | null }) {
  if (!category) return <span className="text-muted-foreground">—</span>
  return (
    <span className="inline-flex items-center gap-1.5 text-sm">
      <span className="size-2 shrink-0 rounded-full" style={{ backgroundColor: category.color }} aria-hidden />
      {category.name}
    </span>
  )
}

export function SourceTag({ source }: { source: CategorySource }) {
  const styles: Record<CategorySource, string> = {
    RULE: 'bg-slate-100 text-slate-700',
    CACHE: 'bg-sky-50 text-sky-700',
    AI: 'bg-violet-50 text-violet-700',
    USER: 'bg-emerald-50 text-emerald-700',
    NONE: 'bg-muted text-muted-foreground',
  }
  return (
    <span className={`rounded px-1.5 py-0.5 text-[11px] font-medium ${styles[source]}`} title="How this was categorised">
      {SOURCE_LABEL[source]}
    </span>
  )
}
