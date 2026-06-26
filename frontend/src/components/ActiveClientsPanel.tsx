import React from 'react'
import { ActiveClient } from '../types/client.types'

// ── Helpers ───────────────────────────────────────────────────────────────────

function formatCompact(val: string): string {
  const n = parseFloat(val)
  if (n >= 1_000_000) return `$${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000)     return `$${(n / 1_000).toFixed(1)}K`
  return `$${n.toFixed(2)}`
}

// ── Styles ────────────────────────────────────────────────────────────────────

const s = {
  colHead: {
    display: 'grid',
    gridTemplateColumns: '48px 1fr 140px 140px',
    fontSize: '11px', fontWeight: 700, textTransform: 'uppercase' as const,
    letterSpacing: '0.05em', color: 'var(--color-text-secondary)',
    padding: '10px 20px', borderBottom: '1px solid var(--color-border-light)',
  } as React.CSSProperties,

  headCell: (align: 'left' | 'right' | 'center') => ({
    textAlign: align,
  } as React.CSSProperties),

  row: (warn: boolean) => ({
    display: 'grid',
    gridTemplateColumns: '48px 1fr 140px 140px',
    padding: '12px 20px',
    borderBottom: '1px solid var(--color-border-light)',
    fontSize: '13px',
    alignItems: 'center',
    background:  warn ? 'var(--color-warning-bg)' : 'transparent',
    borderLeft:  warn ? '3px solid var(--color-warning)' : '3px solid transparent',
  } as React.CSSProperties),

  rank: {
    textAlign: 'center' as const,
    fontWeight: 700,
    color: 'var(--color-text-secondary)',
    fontSize: '12px',
  } as React.CSSProperties,

  name: {
    fontWeight: 600,
    color: 'var(--color-text-primary)',
  } as React.CSSProperties,

  money: {
    textAlign: 'right' as const,
    fontVariantNumeric: 'tabular-nums' as const,
    color: 'var(--color-text-primary)',
  } as React.CSSProperties,

  empty: {
    padding: '36px 24px',
    textAlign: 'center' as const,
    color: 'var(--color-text-muted)',
    fontSize: '13px',
  } as React.CSSProperties,

  skeletonRow: {
    display: 'grid',
    gridTemplateColumns: '48px 1fr 140px 140px',
    padding: '12px 20px',
    gap: '12px',
    borderBottom: '1px solid var(--color-border-light)',
    alignItems: 'center',
  } as React.CSSProperties,
}

// ── Props ─────────────────────────────────────────────────────────────────────

interface Props {
  clients: ActiveClient[] | null   // null = loading
  error:   boolean
}

// ── Component ─────────────────────────────────────────────────────────────────

const ActiveClientsPanel: React.FC<Props> = ({ clients, error }) => {

  if (error) {
    return <div style={s.empty}>Unable to load active clients</div>
  }

  if (clients === null) {
    return (
      <div>
        {[1, 2, 3].map((i) => (
          <div key={i} style={s.skeletonRow}>
            <div className="skeleton" style={{ height: '16px', width: '24px', margin: '0 auto' }} />
            <div className="skeleton" style={{ height: '16px' }} />
            <div className="skeleton" style={{ height: '16px' }} />
            <div className="skeleton" style={{ height: '16px' }} />
          </div>
        ))}
      </div>
    )
  }

  if (clients.length === 0) {
    return <div style={s.empty}>No active clients today</div>
  }

  return (
    <div>
      {/* Column headers */}
      <div style={s.colHead}>
        <div style={s.headCell('center')}>#</div>
        <div style={s.headCell('left')}>Client Name</div>
        <div style={s.headCell('right')}>Today's Turnover</div>
        <div style={s.headCell('right')}>Available Cash</div>
      </div>

      {/* Data rows */}
      {clients.map((c, idx) => (
        <div key={c.clientId} style={s.row(c.cashLimitWarning)}>
          <div style={s.rank}>{idx + 1}</div>
          <div style={s.name}>{c.clientName}</div>
          <div style={s.money}>{formatCompact(c.turnoverToday)}</div>
          <div style={s.money}>{formatCompact(c.availableCash)}</div>
        </div>
      ))}
    </div>
  )
}

export default ActiveClientsPanel
