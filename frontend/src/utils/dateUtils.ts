export function formatShortDateTime(iso: string): string {
  return new Date(iso).toLocaleString('en-US', {
    month:   'short',
    day:     '2-digit',
    hour:    '2-digit',
    minute:  '2-digit',
    hour12:  false,
  })
}

export function formatBlotterTimestamp(iso: string): string {
  const d = new Date(iso)
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'America/New_York',
    day:      '2-digit',
    month:    'short',
    year:     'numeric',
    hour:     '2-digit',
    minute:   '2-digit',
    second:   '2-digit',
    hour12:   false,
  }).formatToParts(d)
  const get = (type: string) => parts.find(p => p.type === type)?.value ?? ''
  return `${get('day')} ${get('month')} ${get('year')}, ${get('hour')}:${get('minute')}:${get('second')} ET`
}
