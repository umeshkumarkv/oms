export function formatShortDateTime(iso: string): string {
  return new Date(iso).toLocaleString('en-US', {
    month:   'short',
    day:     '2-digit',
    hour:    '2-digit',
    minute:  '2-digit',
    hour12:  false,
  })
}
