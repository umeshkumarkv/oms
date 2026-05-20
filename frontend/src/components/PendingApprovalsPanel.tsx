import React, { useCallback, useEffect, useRef, useState } from 'react'
import { approveChange, getPendingChanges, rejectChange } from '../api/riskApi'
import { getCurrentUser } from '../api/apiClient'
import { PendingChange, PendingChangeStatus } from '../types/risk.types'

// ── Styles ──────────────────────────────────────────────────────────────────

const s = {
  card:      { background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-sm)', padding: '20px', marginBottom: '16px' } as React.CSSProperties,
  cardTitle: { margin: '0 0 14px', fontSize: '14px', fontWeight: 700, color: 'var(--color-text-primary)', paddingBottom: '10px', borderBottom: '1px solid var(--color-border-light)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' } as React.CSSProperties,
  table:     { width: '100%', borderCollapse: 'collapse' as const, fontSize: '12px' } as React.CSSProperties,
  th:        { padding: '8px 10px', textAlign: 'left' as const, fontSize: '10px', fontWeight: 700, color: 'var(--color-text-secondary)', borderBottom: '1px solid var(--color-border-light)', background: 'var(--color-bg)', textTransform: 'uppercase' as const, letterSpacing: '0.03em', whiteSpace: 'nowrap' as const } as React.CSSProperties,
  td:        { padding: '9px 10px', borderBottom: '1px solid var(--color-border-light)', verticalAlign: 'middle' as const } as React.CSSProperties,

  btn: (variant: 'primary' | 'danger' | 'secondary' | 'ghost', disabled?: boolean) => {
    const base = { padding: '5px 11px', borderRadius: 'var(--radius-md)', fontSize: '12px', fontWeight: 600, cursor: disabled ? 'not-allowed' : 'pointer', border: 'none', transition: 'opacity 0.15s', opacity: disabled ? 0.5 : 1 }
    const map = {
      primary:   { background: 'var(--color-primary)',   color: 'var(--color-surface)' },
      danger:    { background: 'var(--color-error)',     color: 'var(--color-surface)' },
      secondary: { background: 'var(--color-surface)',   color: 'var(--color-text-primary)', border: '1px solid var(--color-border)' },
      ghost:     { background: 'transparent',            color: 'var(--color-text-secondary)' },
    }
    return { ...base, ...map[variant] } as React.CSSProperties
  },

  overlay:    { position: 'fixed' as const, inset: 0, background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 500 },
  modal:      { background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', padding: '24px', width: '440px', maxWidth: '90vw', boxShadow: 'var(--shadow-lg)' } as React.CSSProperties,
  modalTitle: { margin: '0 0 6px', fontSize: '16px', fontWeight: 700, color: 'var(--color-text-primary)' } as React.CSSProperties,
  modalSub:   { margin: '0 0 16px', fontSize: '13px', color: 'var(--color-text-secondary)' } as React.CSSProperties,
  modalFoot:  { display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' } as React.CSSProperties,
  fieldGroup: { marginBottom: '14px' } as React.CSSProperties,
  label:      { display: 'block', fontSize: '11px', fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: '5px', textTransform: 'uppercase' as const, letterSpacing: '0.04em' },
  textarea:   { width: '100%', padding: '8px 10px', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)', fontSize: '13px', boxSizing: 'border-box' as const, outline: 'none', resize: 'vertical' as const, minHeight: '72px' } as React.CSSProperties,
  toast: (type: 'success' | 'error') => ({
    position: 'fixed' as const, bottom: '24px', right: '24px',
    padding: '12px 18px', borderRadius: 'var(--radius-md)',
    background: type === 'success' ? 'var(--color-success)' : 'var(--color-error)',
    color: 'var(--color-surface)', fontSize: '13px', fontWeight: 500,
    boxShadow: 'var(--shadow-md)', zIndex: 9999, maxWidth: '360px',
  }),
}

// ── Status badge ─────────────────────────────────────────────────────────────

const StatusBadge: React.FC<{ status: PendingChangeStatus }> = ({ status }) => {
  const config: Record<PendingChangeStatus, { bg: string; color: string; label: string }> = {
    PENDING:  { bg: 'var(--color-warning-bg)',  color: 'var(--color-warning)',  label: 'Pending' },
    APPROVED: { bg: 'var(--color-success-bg)',  color: 'var(--color-success)',  label: 'Approved' },
    REJECTED: { bg: 'var(--color-error-bg)',    color: 'var(--color-error)',    label: 'Rejected' },
  }
  const { bg, color, label } = config[status]
  return (
    <span style={{ display: 'inline-block', padding: '2px 8px', borderRadius: 'var(--radius-sm)', fontSize: '10px', fontWeight: 700, background: bg, color }}>
      {label}
    </span>
  )
}

// ── Change type label ─────────────────────────────────────────────────────────

function changeTypeLabel(change: PendingChange): string {
  if (change.changeType === 'ENABLE')            return 'Enable'
  if (change.changeType === 'DISABLE')           return 'Disable'
  if (change.changeType === 'UPDATE_THRESHOLD')  return `Threshold → ${change.proposedThreshold ?? '—'}`
  return change.changeType
}

// ── Reject modal ─────────────────────────────────────────────────────────────

interface RejectModalProps {
  change: PendingChange
  onConfirm: (reason: string) => void
  onCancel: () => void
}

const RejectModal: React.FC<RejectModalProps> = ({ change, onConfirm, onCancel }) => {
  const [reason, setReason] = useState('')
  const valid = reason.trim().length > 0
  return (
    <div style={s.overlay} onClick={onCancel}>
      <div style={s.modal} onClick={e => e.stopPropagation()}>
        <h3 style={s.modalTitle}>Reject Change</h3>
        <p style={s.modalSub}>
          {change.ruleCode} — {changeTypeLabel(change)} (submitted by {change.makerUsername})
        </p>
        <div style={s.fieldGroup}>
          <label style={s.label}>Rejection Reason <span style={{ color: 'var(--color-error)' }}>*</span></label>
          <textarea
            style={{ ...s.textarea, borderColor: !valid && reason.length > 0 ? 'var(--color-error)' : 'var(--color-border)' }}
            placeholder="Provide a mandatory reason for rejection"
            value={reason}
            onChange={e => setReason(e.target.value)}
            autoFocus
          />
          {!valid && reason.length > 0 && (
            <div style={{ fontSize: '11px', color: 'var(--color-error)', marginTop: '4px' }}>Rejection reason is required.</div>
          )}
        </div>
        <div style={s.modalFoot}>
          <button style={s.btn('secondary')} onClick={onCancel}>Cancel</button>
          <button
            style={s.btn('danger', !valid)}
            disabled={!valid}
            onClick={() => valid && onConfirm(reason.trim())}
          >
            Reject Change
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Main component ────────────────────────────────────────────────────────────

interface PendingApprovalsPanelProps {
  onChangeReviewed?: () => void
}

const PendingApprovalsPanel: React.FC<PendingApprovalsPanelProps> = ({ onChangeReviewed }) => {
  const currentUser = getCurrentUser()
  const [changes,  setChanges]  = useState<PendingChange[]>([])
  const [loading,  setLoading]  = useState(true)
  const [rejectTarget, setRejectTarget] = useState<PendingChange | null>(null)
  const [rowErrors, setRowErrors] = useState<Record<string, string>>({})
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null)
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const showToast = useCallback((message: string, type: 'success' | 'error') => {
    if (toastTimer.current) clearTimeout(toastTimer.current)
    setToast({ message, type })
    toastTimer.current = setTimeout(() => setToast(null), 3500)
  }, [])

  const loadChanges = useCallback(async () => {
    setLoading(true)
    try {
      const data = await getPendingChanges()
      setChanges(data)
    } catch {
      showToast('Failed to load pending changes', 'error')
    } finally {
      setLoading(false)
    }
  }, [showToast])

  useEffect(() => { loadChanges() }, [loadChanges])

  const handleApprove = async (change: PendingChange) => {
    setRowErrors(prev => ({ ...prev, [change.id]: '' }))
    try {
      await approveChange(change.id)
      showToast(`${change.ruleCode} change approved and applied`, 'success')
      loadChanges()
      onChangeReviewed?.()
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } }).response?.status
      if (status === 403) {
        setRowErrors(prev => ({ ...prev, [change.id]: 'You cannot approve your own change' }))
      } else if (status === 409) {
        setRowErrors(prev => ({ ...prev, [change.id]: 'This change has already been reviewed' }))
        loadChanges()
      } else {
        showToast('Failed to approve change — please try again', 'error')
      }
    }
  }

  const handleRejectConfirm = async (reason: string) => {
    if (!rejectTarget) return
    const target = rejectTarget
    setRejectTarget(null)
    setRowErrors(prev => ({ ...prev, [target.id]: '' }))
    try {
      await rejectChange(target.id, reason)
      showToast(`${target.ruleCode} change rejected`, 'success')
      loadChanges()
      onChangeReviewed?.()
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } }).response?.status
      if (status === 409) {
        setRowErrors(prev => ({ ...prev, [target.id]: 'This change has already been reviewed' }))
        loadChanges()
      } else {
        showToast('Failed to reject change — please try again', 'error')
      }
    }
  }

  const fmtDate = (iso: string) =>
    new Date(iso).toLocaleString('en-US', { month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })

  return (
    <>
      {toast && <div style={s.toast(toast.type)}>{toast.message}</div>}
      {rejectTarget && (
        <RejectModal
          change={rejectTarget}
          onConfirm={handleRejectConfirm}
          onCancel={() => setRejectTarget(null)}
        />
      )}

      <div style={s.card}>
        <h2 style={s.cardTitle}>
          Pending Approvals
          <button style={{ ...s.btn('secondary'), fontSize: '12px', padding: '4px 10px' }} onClick={loadChanges}>
            ↻ Refresh
          </button>
        </h2>

        {loading ? (
          <div style={{ color: 'var(--color-text-muted)', fontSize: '13px', padding: '16px 0' }}>
            Loading pending changes…
          </div>
        ) : changes.length === 0 ? (
          <div style={{ color: 'var(--color-text-muted)', fontSize: '13px', padding: '16px 0' }}>
            No pending changes found.
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={s.table}>
              <thead>
                <tr>
                  {['Rule Code', 'Change', 'Submitted By', 'Submitted At', 'Status', 'Actions'].map(h => (
                    <th key={h} style={s.th}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {changes.map(change => {
                  const isReviewed = change.status !== 'PENDING'
                  const isSelfChange = currentUser?.userId === change.makerUserId
                  const rowError = rowErrors[change.id]
                  return (
                    <React.Fragment key={change.id}>
                      <tr style={{ opacity: isReviewed ? 0.7 : 1 }}>
                        <td style={s.td}>
                          <span style={{ fontFamily: 'monospace', fontSize: '11px', color: 'var(--color-primary)', fontWeight: 600 }}>
                            {change.ruleCode}
                          </span>
                        </td>
                        <td style={s.td}>
                          <span style={{ fontSize: '12px', color: 'var(--color-text-primary)' }}>
                            {changeTypeLabel(change)}
                          </span>
                        </td>
                        <td style={s.td}>
                          <span style={{ fontSize: '12px', color: 'var(--color-text-secondary)' }}>
                            {change.makerUsername}
                            {isSelfChange && (
                              <span style={{ marginLeft: '4px', fontSize: '10px', color: 'var(--color-text-muted)' }}>(you)</span>
                            )}
                          </span>
                        </td>
                        <td style={s.td}>
                          <span style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>
                            {fmtDate(change.submittedAt)}
                          </span>
                        </td>
                        <td style={s.td}>
                          <div>
                            <StatusBadge status={change.status} />
                            {isReviewed && change.reviewedAt && (
                              <div style={{ fontSize: '10px', color: 'var(--color-text-muted)', marginTop: '2px' }}>
                                {fmtDate(change.reviewedAt)}
                              </div>
                            )}
                            {change.status === 'REJECTED' && change.rejectionReason && (
                              <div style={{ fontSize: '10px', color: 'var(--color-error)', marginTop: '2px', maxWidth: '140px' }} title={change.rejectionReason}>
                                {change.rejectionReason.length > 40
                                  ? change.rejectionReason.slice(0, 40) + '…'
                                  : change.rejectionReason}
                              </div>
                            )}
                          </div>
                        </td>
                        <td style={s.td}>
                          {isReviewed ? (
                            <span style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>—</span>
                          ) : (
                            <div style={{ display: 'flex', gap: '6px', alignItems: 'center', flexWrap: 'wrap' }}>
                              <button
                                style={s.btn('primary')}
                                onClick={() => handleApprove(change)}
                              >
                                Approve
                              </button>
                              <button
                                style={s.btn('danger')}
                                onClick={() => setRejectTarget(change)}
                              >
                                Reject
                              </button>
                            </div>
                          )}
                        </td>
                      </tr>
                      {rowError && (
                        <tr>
                          <td colSpan={6} style={{ padding: '4px 10px 8px', borderBottom: '1px solid var(--color-border-light)' }}>
                            <div style={{ fontSize: '11px', color: 'var(--color-error)', background: 'var(--color-error-bg)', borderRadius: 'var(--radius-sm)', padding: '5px 10px' }}>
                              {rowError}
                            </div>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  )
}

export default PendingApprovalsPanel
