/**
 * Returns true if the current wall-clock time falls within US equity market hours:
 * Monday–Friday, 9:30 AM – 4:00 PM America/New_York (exclusive of 4:00 PM).
 *
 * Uses Intl.DateTimeFormat so it correctly handles EST/EDT transitions automatically.
 */
export function isMarketOpen(): boolean {
  const now   = new Date()
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/New_York',
    weekday:  'short',
    hour:     'numeric',
    minute:   'numeric',
    hour12:   false,
  }).formatToParts(now)

  const weekday = parts.find(p => p.type === 'weekday')?.value ?? ''
  const hour    = parseInt(parts.find(p => p.type === 'hour')?.value   ?? '0', 10)
  const minute  = parseInt(parts.find(p => p.type === 'minute')?.value ?? '0', 10)

  // hour12:false with Intl can return 24 for midnight — treat as 0
  const h = hour === 24 ? 0 : hour

  const isWeekday    = weekday !== 'Sat' && weekday !== 'Sun'
  const totalMinutes = h * 60 + minute
  return isWeekday && totalMinutes >= 9 * 60 + 30 && totalMinutes < 16 * 60
}
