import React, { useCallback, useEffect, useRef, useState } from 'react'
import { approveChange, getPendingChanges, rejectChange } from '../api/riskApi'
import { getCurrentUser } from '../api/apiClient'
import { AxiosApiError, PendingChange, PendingChangeStatus } from '../types/risk.types'
import StatusBadge from './StatusBadge'
import { formatShortDateTime } from '../utils/dateUtils'
import './PendingApprovalsPanel.css'

// ── Helpers ───────────────────────────────────────────────────────────────────

function getFromValue(change: PendingChange): string {
  if (change.changeType === 'UPDATE_THRESHOLD') return change.currentThreshold ?? '—'
  return change.currentEnabled !== null ? (change.currentEnabled ? 'Enabled' : 'Disabled') : '—'
}

function getToValue(change: PendingChange): string {
  if (change.changeType === 'ENABLE')           return 'Enabled'
  if (change.changeType === 'DISABLE')          return 'Disabled'
  if (change.changeType === 'UPDATE_THRESHOLD') return change.proposedThreshold ?? '—'
  return '—'
}

// ── Button class helper ───────────────────────────────────────────────────────

function btnCls(variant: 'primary' | 'danger' | 'secondary' | 'ghost', sm?: boolean): string {
  return ['pap-btn', `pap-btn--${variant}`, sm ? 'pap-btn--sm' : ''].filter(Boolean).join(' ')
}

// ── Styles ────────────────────────────────────────────────────────────────────

const s = {
  overlay:    { position: 'fixed' as const, inset: 0, background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 500 } as React.CSSProperties,
  modal:      { background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', padding: '24px', width: '440px', maxWidth: '90vw', boxShadow: 'var(--shadow-lg)' } as React.CSSProperties,
  modalTitle: { margin: '0 0 6px', fontSize: '16px', fontWeight: 700, color: 'var(--color-text-primary)' } as React.CSSProperties,
  modalSub:   { margin: '0 0 16px', fontSize: '13px', color: 'var(--color-text-secondary)' } as React.CSSProperties,
  modalFoot:  { display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' } as React.CSSProperties,
  fieldGroup: { marginBottom: '14px' } as React.CSSProperties,
  label:      { display: 'block', fontSize: '11px', fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: '5px', textTransform: 'uppercase' as const, letterSpacing: '0.04em' } as React.CSSProperties,
  textarea:   { width: '100%', padding: '8px 10px', borderRadius: 'var(--radius-md)', fontSize: '13px', boxSizing: 'border-box' as const, outline: 'none', resize: 'vertical' as const, minHeight: '72px' } as React.CSSProperties,
  card:       { background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-sm)', padding: '20px', marginBottom: '16px' } as React.CSSProperties,
  cardTitle:  { margin: '0 0 14px', fontSize: '14px', fontWeight: 700, color: 'var(--color-text-primary)', paddingBottom: '10px', borderBottom: '1px solid var(--color-border-light)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' } as React.CSSProperties,
  table:      { width: '100%', borderCollapse: 'collapse' as const, fontSize: '12px' } as React.CSSProperties,
  th:         { padding: '8px 10px', textAlign: 'left' as const, fontSize: '10px', fontWeight: 700, color: 'var(--color-text-secondary)', borderBottom: '1px solid var(--color-border-light)', background: 'var(--color-bg)', textTransform: 'uppercase' as const, letterSpacing: '0.03em', whiteSpace: 'nowrap' as const, position: 'sticky' as const, top: 0, zIndex: 1 } as React.CSSProperties,
  td:         { padding: '9px 10px', borderBottom: '1px solid var(--color-border-light)', verticalAlign: 'middle' as const } as React.CSSProperties,
  toast:      (type: 'success' | 'error'): React.CSSProperties => ({ position: 'fixed', bottom: '24px', right: '24px', padding: '12px 18px', borderRadius: 'var(--radius-md)', color: 'var(--color-surface)', fontSize: '13px', fontWeight: 500, boxShadow: 'var(--shadow-md)', zIndex: 9999, maxWidth: '360px', background: type === 'success' ? 'var(--color-success)' : 'var(--color-error)' }),
}

// ── Reject modal ──────────────────────────────────────────────────────────────

interface RejectModalProps {
  change: PendingChange
  onConfirm: (reason: string) => void
  onCancel: () => void
}

const RejectModal: React.FC<RejectModalProps> = ({ change, onConfirm, onCancel }) => {
  const [reason, setReason] = useState('')
  const valid    = reason.trim().length > 0
  const hasError = !valid && reason.length > 0
  return (
    <div style={s.overlay} onClick={onCancel}>
      <div style={s.modal} onClick={e => e.stopPropagation()}>
        <h3 style={s.modalTitle}>Reject Change</h3>
        <p style={s.modalSub}>
          {change.ruleCode}: {getFromValue(change)} → {getToValue(change)}, submitted by {change.makerUsername}
        </p>
        <div style={s.fieldGroup}>
          <label style={s.label}>
            Rejection Reason <span style={{ color: 'var(--color-error)' }}>*</span>
          </label>
          <textarea
            style={{ ...s.textarea, border: `1px solid ${hasError ? 'var(--color-error)' : 'var(--color-border)'}` }}
            placeholder="Provide a mandatory reason for rejection"
            value={reason}
            onChange={e => setReason(e.target.value)}
            autoFocus
          />
          {hasError && (
            <div style={{ fontSize: '11px', color: 'var(--color-error)', marginTop: '4px' }}>
              Rejection reason is required.
            </div>
          )}
        </div>
        <div style={s.modalFoot}>
          <button className={btnCls('secondary')} onClick={onCancel}>Cancel</button>
          <button
            className={btnCls('danger')}
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
  const [changes,      setChanges]      = useState<PendingChange[]>([])
  const [loading,      setLoading]      = useState(true)
  const [rejectTarget, setRejectTarget] = useState<PendingChange | null>(null)
  const [rowErrors,    setRowErrors]    = useState<Record<string, string>>({})
  const [toast,        setToast]        = useState<{ message: string; type: 'success' | 'error' } | null>(null)
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
      const apiErr = err as AxiosApiError
      if (apiErr.response?.status === 403) {
        setRowErrors(prev => ({ ...prev, [change.id]: 'You cannot approve your own change' }))
      } else if (apiErr.response?.status === 409) {
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
      const apiErr = err as AxiosApiError
      if (apiErr.response?.status === 409) {
        setRowErrors(prev => ({ ...prev, [target.id]: 'This change has already been reviewed' }))
        loadChanges()
      } else {
        showToast('Failed to reject change — please try again', 'error')
      }
    }
  }

  const TABLE_HEADERS: string[] = ['Rule Code', 'Current Value', 'New Value', 'Submitted By', 'Submitted At', 'Status', 'Actions']

  return (
    <>
      {toast && (
        <div style={s.toast(toast.type)}>{toast.message}</div>
      )}
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
          <button className={btnCls('secondary', true)} onClick={loadChanges}>
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
          <div style={{ overflowX: 'auto', overflowY: 'auto', maxHeight: '400px' }}>
            <table style={s.table}>
              <thead>
                <tr>
                  {TABLE_HEADERS.map(h => (
                    <th key={h} style={s.th}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {changes.map(change => {
                  const isReviewed   = change.status !== 'PENDING'
                  const isSelfChange = currentUser?.userId === change.makerUserId
                  const rowError     = rowErrors[change.id]
                  return (
                    <React.Fragment key={change.id}>
                      <tr style={{ opacity: isReviewed ? 0.7 : 1 }}>
                        <td style={s.td}>
                          <span style={{ fontFamily: 'monospace', fontSize: '11px', color: 'var(--color-primary)', fontWeight: 600 }}>
                            {change.ruleCode}
                          </span>
                        </td>
                        <td style={s.td}>
                          <span style={{ fontSize: '12px', color: 'var(--color-text-secondary)' }}>
                            {getFromValue(change)}
                          </span>
                        </td>
                        <td style={s.td}>
                          <span style={{ fontSize: '12px', color: 'var(--color-text-primary)', fontWeight: 500 }}>
                            {getToValue(change)}
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
                            {formatShortDateTime(change.submittedAt)}
                          </span>
                        </td>
                        <td style={s.td}>
                          <div>
                            <StatusBadge status={change.status as PendingChangeStatus} />
                            {change.status === 'PENDING' && change.makerNote && (
                              <div
                                style={{ fontSize: '10px', color: 'var(--color-warning)', marginTop: '2px', maxWidth: '140px', cursor: 'help' }}
                                title={change.makerNote}
                              >
                                {change.makerNote.length > 40
                                  ? change.makerNote.slice(0, 40) + '…'
                                  : change.makerNote}
                              </div>
                            )}
                            {isReviewed && change.reviewedAt && (
                              <div style={{ fontSize: '10px', color: 'var(--color-text-muted)', marginTop: '2px' }}>
                                {formatShortDateTime(change.reviewedAt)}
                              </div>
                            )}
                            {change.status === 'REJECTED' && change.rejectionReason && (
                              <div
                                style={{ fontSize: '10px', color: 'var(--color-error)', marginTop: '2px', maxWidth: '140px', cursor: 'help' }}
                                title={change.rejectionReason}
                              >
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
                              <button className={btnCls('primary')} onClick={() => handleApprove(change)}>
                                Approve
                              </button>
                              <button className={btnCls('danger')} onClick={() => setRejectTarget(change)}>
                                Reject
                              </button>
                            </div>
                          )}
                        </td>
                      </tr>
                      {rowError && (
                        <tr>
                          <td colSpan={7} style={{ padding: '4px 10px 8px', borderBottom: '1px solid var(--color-border-light)' }}>
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
