import React from 'react'
import { PendingChangeStatus } from '../types/risk.types'

const STATUS_CONFIG: Record<PendingChangeStatus, { bg: string; color: string; label: string }> = {
  PENDING:  { bg: 'var(--color-warning-bg)', color: 'var(--color-warning)', label: 'Pending'  },
  APPROVED: { bg: 'var(--color-success-bg)', color: 'var(--color-success)', label: 'Approved' },
  REJECTED: { bg: 'var(--color-error-bg)',   color: 'var(--color-error)',   label: 'Rejected' },
}

interface StatusBadgeProps {
  status: PendingChangeStatus
}

const StatusBadge: React.FC<StatusBadgeProps> = ({ status }) => {
  const { bg, color, label } = STATUS_CONFIG[status]
  return (
    <span style={{
      display: 'inline-block', padding: '2px 8px',
      borderRadius: 'var(--radius-sm)', fontSize: '10px', fontWeight: 700,
      background: bg, color,
    }}>
      {label}
    </span>
  )
}

export default StatusBadge
