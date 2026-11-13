/**
 * Validated categorical palette (fixed order, never cycled; see the dataviz reference palette).
 * Charts assign series to slots by entity, not rank, so a filter never repaints survivors.
 */
export const SERIES = {
  blue: '#2a78d6',
  orange: '#eb6834',
  aqua: '#1baf7a',
  yellow: '#eda100',
  magenta: '#e87ba4',
  green: '#008300',
  violet: '#4a3aa7',
  red: '#e34948',
} as const

/** Folded "Other" bucket: neutral, never a generated hue. */
export const OTHER_GRAY = '#a8a29e'

export const GRID = '#e5e7eb'
export const AXIS_TEXT = '#64748b'
