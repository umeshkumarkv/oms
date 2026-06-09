import React, { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AlertTriangle, ChevronDown, ChevronUp, ClipboardList, Clock, Layers, User } from 'lucide-react'
import { getRiskRuleAudit, getRiskRules, patchRiskRule } from '../api/riskApi'
import { getCurrentUser } from '../api/authApi'
import { RiskRule, RiskRuleAuditRecord } from '../types/risk.types'
import PendingApprovalsPanel from '../components/PendingApprovalsPanel'

// ── Rule grouping constants ─────────────────────────────────────────────────────

const CATEGORY_TO_GROUP: Record<string, string> = {
  FAT_FINGER:     'FAT_FINGER',
  CLIENT:         'CLIENT',
  STATEFUL:       'CLIENT',
  PRE_TRADE:      'ORDER',
  FINANCIAL_LIMIT:'ORDER',
}

const REDUNDANT_CODE_TO_GROUP: Record<string, string> = {
  MARKET_HOURS_CHECK:      'MARKET',
  INSTRUMENT_TRADABLE_CHECK:'INSTRUMENT',
  CLIENT_ACTIVE_CHECK:     'CLIENT',
  CASH_BALANCE_CHECK:      'CLIENT',
  HOLDINGS_CHECK:          'CLIENT',
  QUANTITY_POSITIVE_CHECK: 'ORDER',
  LIMIT_PRICE_CHECK:       'ORDER',
}

function getRuleGroupKey(rule: RiskRule): string {
  if (rule.ruleCategory === 'REDUNDANT') {
    return REDUNDANT_CODE_TO_GROUP[rule.ruleCode] ?? 'ORDER'
  }
  return CATEGORY_TO_GROUP[rule.ruleCategory] ?? 'ORDER'
}

const RULE_GROUPS: Array<{ key: string; label: string; icon: React.ReactNode }> = [
  { key: 'MARKET',     label: 'Market Rules',               icon: <Clock size={14} /> },
  { key: 'CLIENT',     label: 'Client Rules',               icon: <User size={14} /> },
  { key: 'INSTRUMENT', label: 'Instrument Rules',           icon: <Layers size={14} /> },
  { key: 'ORDER',      label: 'Order Rules',                icon: <ClipboardList size={14} /> },
  { key: 'FAT_FINGER', label: 'Fat Finger / Risk Controls', icon: <AlertTriangle size={14} /> },
]

// ── Styles ─────────────────────────────────────────────────────────────────────
const s = {
  page:     { fontFamily: 'system-ui, sans-serif', padding: '20px', minHeight: 'calc(100vh - 52px)', background: 'var(--color-bg)' } as React.CSSProperties,
  header:   { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' } as React.CSSProperties,
  title:    { margin: 0, fontSize: '20px', fontWeight: 700, color: 'var(--color-text-primary)' } as React.CSSProperties,
  subtitle: { fontSize: '12px', color: 'var(--color-text-muted)', marginTop: '3px' } as React.CSSProperties,

  card:     { background: 'var(--color-surface)', borderRadius: '10px', boxShadow: 'var(--shadow-sm)', padding: '20px', marginBottom: '16px' } as React.CSSProperties,
  cardTitle:{ margin: '0 0 14px', fontSize: '14px', fontWeight: 700, color: 'var(--color-text-primary)', paddingBottom: '10px', borderBottom: '1px solid var(--color-border-light)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' } as React.CSSProperties,

  table:    { width: '100%', borderCollapse: 'collapse' as const, fontSize: '12px' } as React.CSSProperties,
  th:       { padding: '8px 10px', textAlign: 'left' as const, fontSize: '10px', fontWeight: 700, color: 'var(--color-text-secondary)', borderBottom: '1px solid var(--color-border-light)', background: 'var(--color-bg)', textTransform: 'uppercase' as const, letterSpacing: '0.03em', whiteSpace: 'nowrap' as const } as React.CSSProperties,
  td:       { padding: '9px 10px', borderBottom: '1px solid var(--color-border-light)', verticalAlign: 'middle' as const } as React.CSSProperties,

  input:    { width: '100%', padding: '8px 10px', borderRadius: '6px', border: '1px solid var(--color-border)', fontSize: '13px', boxSizing: 'border-box' as const, outline: 'none' } as React.CSSProperties,
  textarea: { width: '100%', padding: '8px 10px', borderRadius: '6px', border: '1px solid var(--color-border)', fontSize: '13px', boxSizing: 'border-box' as const, outline: 'none', resize: 'vertical' as const, minHeight: '72px' } as React.CSSProperties,

  btn: (variant: 'primary' | 'danger' | 'secondary' | 'ghost', disabled?: boolean) => {
    const base = { padding: '7px 14px', borderRadius: '6px', fontSize: '13px', fontWeight: 600, cursor: disabled ? 'not-allowed' : 'pointer', border: 'none', transition: 'opacity 0.15s', opacity: disabled ? 0.5 : 1 }
    const map = {
      primary:   { background: 'var(--color-primary)', color: 'var(--color-surface)' },
      danger:    { background: 'var(--color-error)',   color: 'var(--color-surface)' },
      secondary: { background: 'var(--color-surface)', color: 'var(--color-text-primary)', border: '1px solid var(--color-border)' },
      ghost:     { background: 'transparent',          color: 'var(--color-text-secondary)' },
    }
    return { ...base, ...map[variant] } as React.CSSProperties
  },

  // Modal (used by ToggleModal and ThresholdModal)
  overlay:    { position: 'fixed' as const, inset: 0, background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 500 },
  modal:      { background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', padding: '24px', width: '440px', maxWidth: '90vw', boxShadow: 'var(--shadow-lg)' } as React.CSSProperties,
  modalTitle: { margin: '0 0 6px', fontSize: '16px', fontWeight: 700, color: 'var(--color-text-primary)' } as React.CSSProperties,
  modalSub:   { margin: '0 0 16px', fontSize: '13px', color: 'var(--color-text-secondary)' } as React.CSSProperties,
  modalFoot:  { display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' } as React.CSSProperties,
  fieldGroup: { marginBottom: '14px' } as React.CSSProperties,
  label:      { display: 'block', fontSize: '11px', fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: '5px', textTransform: 'uppercase' as const, letterSpacing: '0.04em' },
  hint:       { fontSize: '11px', color: 'var(--color-text-muted)', marginTop: '4px' } as React.CSSProperties,

  // Toast
  toast: (type: 'success' | 'error') => ({
    position: 'fixed' as const, bottom: '24px', right: '24px',
    padding: '12px 18px', borderRadius: '8px',
    background: type === 'success' ? 'var(--color-success)' : 'var(--color-error)',
    color: 'var(--color-surface)', fontSize: '13px', fontWeight: 500,
    boxShadow: '0 4px 16px rgba(0,0,0,0.2)', zIndex: 9999,
    maxWidth: '360px',
  }),

  // Inline threshold edit
  thresholdCell:  { display: 'flex', alignItems: 'center', gap: '6px' } as React.CSSProperties,
  thresholdValue: { fontSize: '12px', color: 'var(--color-text-primary)', cursor: 'pointer', padding: '2px 4px', borderRadius: '3px', borderBottom: '1px dashed var(--color-text-muted)' } as React.CSSProperties,
}

// ── Sub-components ─────────────────────────────────────────────────────────────

const Toggle: React.FC<{ checked: boolean; onChange: () => void }> = ({ checked, onChange }) => (
  <div
    onClick={onChange}
    role="switch"
    aria-checked={checked}
    style={{
      width: '38px', height: '20px', borderRadius: '10px',
      background: checked ? 'var(--color-success)' : 'var(--color-border)',
      position: 'relative', cursor: 'pointer', transition: 'background 0.2s', flexShrink: 0,
    }}
  >
    <div style={{
      position: 'absolute', top: '2px',
      left: checked ? '20px' : '2px',
      width: '16px', height: '16px', borderRadius: '50%', background: '#fff',
      transition: 'left 0.2s', boxShadow: '0 1px 3px rgba(0,0,0,0.25)',
    }} />
  </div>
)

const ActionBadge: React.FC<{ action: string }> = ({ action }) => (
  <span style={{
    display: 'inline-block', padding: '2px 6px', borderRadius: '4px',
    fontSize: '10px', fontWeight: 700,
    background: action === 'REJECT' ? 'var(--color-error-bg)'   : 'var(--color-warning-bg)',
    color:      action === 'REJECT' ? 'var(--color-error)'      : 'var(--color-warning)',
  }}>
    {action}
  </span>
)

// ── Confirm modal for toggle changes ──────────────────────────────────────────
interface ToggleModalProps {
  rule: RiskRule
  onConfirm: (reason: string) => void
  onCancel: () => void
}

const ToggleModal: React.FC<ToggleModalProps> = ({ rule, onConfirm, onCancel }) => {
  const [reason, setReason] = useState('')
  const action = rule.enabled ? 'Disable' : 'Enable'
  const valid  = reason.trim().length >= 10
  return (
    <div style={s.overlay} onClick={onCancel}>
      <div style={s.modal} onClick={e => e.stopPropagation()}>
        <h3 style={s.modalTitle}>{action} {rule.ruleCode}?</h3>
        <p style={s.modalSub}>{rule.description}</p>
        <div style={s.fieldGroup}>
          <label style={s.label}>Change Reason <span style={{ color: 'var(--color-error)' }}>*</span></label>
          <textarea
            style={{ ...s.textarea, borderColor: reason.length > 0 && !valid ? 'var(--color-error)' : 'var(--color-border)' }}
            placeholder="Describe why you are making this change (min 10 characters)"
            value={reason}
            onChange={e => setReason(e.target.value)}
            autoFocus
          />
          {reason.length > 0 && !valid && (
            <div style={{ fontSize: '11px', color: 'var(--color-error)', marginTop: '4px' }}>
              Minimum 10 characters required ({reason.trim().length}/10)
            </div>
          )}
          {valid && (
            <div style={{ fontSize: '11px', color: 'var(--color-success)', marginTop: '4px' }}>✓ Reason recorded</div>
          )}
        </div>
        <div style={s.modalFoot}>
          <button style={s.btn('secondary')} onClick={onCancel}>Cancel</button>
          <button
            style={s.btn(action === 'Enable' ? 'primary' : 'danger', !valid)}
            disabled={!valid}
            onClick={() => valid && onConfirm(reason.trim())}
          >
            {action} Rule
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Threshold edit modal ───────────────────────────────────────────────────────
interface ThresholdModalProps {
  rule: RiskRule
  onConfirm: (newValue: string, reason: string) => void
  onCancel: () => void
}

const ThresholdModal: React.FC<ThresholdModalProps> = ({ rule, onConfirm, onCancel }) => {
  const [value,  setValue]  = useState(rule.thresholdValue ?? '')
  const [reason, setReason] = useState('')
  const valueValid  = value.trim().length > 0 && !isNaN(Number(value))
  const reasonValid = reason.trim().length >= 10
  const canSave     = valueValid && reasonValid
  return (
    <div style={s.overlay} onClick={onCancel}>
      <div style={s.modal} onClick={e => e.stopPropagation()}>
        <h3 style={s.modalTitle}>Edit Threshold — {rule.ruleCode}</h3>
        <p style={s.modalSub}>{rule.description}</p>
        <div style={s.fieldGroup}>
          <label style={s.label}>New Threshold Value {rule.thresholdUnit ? `(${rule.thresholdUnit})` : ''}</label>
          <input
            style={{ ...s.input, borderColor: value.length > 0 && !valueValid ? 'var(--color-error)' : 'var(--color-border)' }}
            type="number"
            value={value}
            onChange={e => setValue(e.target.value)}
            autoFocus
          />
          <div style={s.hint}>Current value: {rule.thresholdValue ?? '—'} {rule.thresholdUnit ?? ''}</div>
        </div>
        <div style={s.fieldGroup}>
          <label style={s.label}>Change Reason <span style={{ color: 'var(--color-error)' }}>*</span></label>
          <textarea
            style={{ ...s.textarea, borderColor: reason.length > 0 && !reasonValid ? 'var(--color-error)' : 'var(--color-border)' }}
            placeholder="Describe why you are changing this threshold (min 10 characters)"
            value={reason}
            onChange={e => setReason(e.target.value)}
          />
          {reason.length > 0 && !reasonValid && (
            <div style={{ fontSize: '11px', color: 'var(--color-error)', marginTop: '4px' }}>
              Minimum 10 characters required ({reason.trim().length}/10)
            </div>
          )}
        </div>
        <div style={s.modalFoot}>
          <button style={s.btn('secondary')} onClick={onCancel}>Cancel</button>
          <button
            style={s.btn('primary', !canSave)}
            disabled={!canSave}
            onClick={() => canSave && onConfirm(value.trim(), reason.trim())}
          >
            Save Threshold
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Main Component ─────────────────────────────────────────────────────────────
const AdminPanelPage: React.FC = () => {
  const navigate = useNavigate()
  const currentUser = getCurrentUser()

  // Role guard — only enforce when a JWT is present (auth-service stub issues no token yet)
  useEffect(() => {
    if (currentUser !== null && currentUser.role !== 'ADMIN') navigate('/dashboard', { replace: true })
  }, [navigate, currentUser?.role])

  // Risk rules state
  const [rules,        setRules]        = useState<RiskRule[]>([])
  const [auditLog,     setAuditLog]     = useState<RiskRuleAuditRecord[]>([])
  const [rulesLoading, setRulesLoading] = useState(true)
  const [auditLoading, setAuditLoading] = useState(true)

  // Group collapse state — all expanded by default
  const [expandedGroups, setExpandedGroups] = useState<Record<string, boolean>>(
    { MARKET: true, CLIENT: true, INSTRUMENT: true, ORDER: true, FAT_FINGER: true }
  )
  const allExpanded = Object.values(expandedGroups).every(Boolean)
  const toggleGroup = (key: string) =>
    setExpandedGroups(prev => ({ ...prev, [key]: !prev[key] }))
  const toggleAll = () => {
    const next = !allExpanded
    setExpandedGroups({ MARKET: next, CLIENT: next, INSTRUMENT: next, ORDER: next, FAT_FINGER: next })
  }

  // Toggle modal
  const [toggleModal, setToggleModal] = useState<RiskRule | null>(null)

  // Threshold modal
  const [thresholdModal, setThresholdModal] = useState<RiskRule | null>(null)

  // Toast
  const [toast,    setToast]    = useState<{ message: string; type: 'success' | 'error' } | null>(null)
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const showToast = useCallback((message: string, type: 'success' | 'error') => {
    if (toastTimer.current) clearTimeout(toastTimer.current)
    setToast({ message, type })
    toastTimer.current = setTimeout(() => setToast(null), 3500)
  }, [])

  // ── Load data ────────────────────────────────────────────────────────────
  const loadRules = useCallback(async () => {
    setRulesLoading(true)
    // TODO: SCRUM-47 — replace stub fallback in getRiskRules with real GET /api/v1/risk/rules
    const data = await getRiskRules()
    setRules(data)
    setRulesLoading(false)
  }, [])

  const loadAudit = useCallback(async () => {
    setAuditLoading(true)
    // TODO: SCRUM-47 — replace stub fallback in getRiskRuleAudit with real GET /api/v1/risk/rules/audit
    const data = await getRiskRuleAudit()
    setAuditLog(data)
    setAuditLoading(false)
  }, [])

  useEffect(() => { loadRules(); loadAudit() }, [loadRules, loadAudit])

  // ── Pending approvals panel ref for reloading ────────────────────────────
  const [pendingRefreshKey, setPendingRefreshKey] = useState(0)
  const reloadPending = useCallback(() => setPendingRefreshKey(k => k + 1), [])

  // ── Toggle handler ───────────────────────────────────────────────────────
  const handleToggleConfirm = async (reason: string) => {
    if (!toggleModal) return
    const rule     = toggleModal
    const newValue = !rule.enabled
    setToggleModal(null)

    try {
      await patchRiskRule(rule.ruleCode, { enabled: newValue, changeReason: reason })
      showToast(`${rule.ruleCode} change submitted — awaiting checker approval`, 'success')
      reloadPending()
    } catch {
      showToast(`Failed to submit change for ${rule.ruleCode}`, 'error')
    }
  }

  // ── Threshold handler ─────────────────────────────────────────────────────
  const handleThresholdConfirm = async (newValue: string, reason: string) => {
    if (!thresholdModal) return
    const rule = thresholdModal
    setThresholdModal(null)

    try {
      await patchRiskRule(rule.ruleCode, { thresholdValue: newValue, changeReason: reason })
      showToast(`${rule.ruleCode} threshold change submitted — awaiting checker approval`, 'success')
      reloadPending()
    } catch {
      showToast(`Failed to submit threshold change for ${rule.ruleCode}`, 'error')
    }
  }

  const fmtThreshold = (rule: RiskRule): string => {
    if (!rule.thresholdValue) return '—'
    const unit = rule.thresholdUnit
    if (unit === 'USD')        return `$${Number(rule.thresholdValue).toLocaleString()}`
    if (unit === 'PERCENT')    return `${rule.thresholdValue}%`
    if (unit === 'MULTIPLIER') return `${rule.thresholdValue}×`
    if (unit === 'SHARES')     return `${Number(rule.thresholdValue).toLocaleString()} shares`
    return rule.thresholdValue
  }

  const fmtDate = (iso: string) =>
    new Date(iso).toLocaleString('en-US', { month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })

  // ── Render ────────────────────────────────────────────────────────────────
  if (currentUser !== null && currentUser.role !== 'ADMIN') return null

  return (
    <div style={s.page}>

      {/* Toast */}
      {toast && <div style={s.toast(toast.type)}>{toast.message}</div>}

      {/* Modals */}
      {toggleModal && (
        <ToggleModal
          rule={toggleModal}
          onConfirm={handleToggleConfirm}
          onCancel={() => setToggleModal(null)}
        />
      )}
      {thresholdModal && (
        <ThresholdModal
          rule={thresholdModal}
          onConfirm={handleThresholdConfirm}
          onCancel={() => setThresholdModal(null)}
        />
      )}

      {/* Header */}
      <div style={s.header}>
        <div>
          <h1 style={s.title}>Admin Panel</h1>
          <div style={s.subtitle}>Risk rule management · User management</div>
        </div>
        <span style={{
          padding: '4px 12px', borderRadius: '6px', fontSize: '12px', fontWeight: 700,
          background: 'var(--color-warning-bg)', color: 'var(--color-warning)',
          border: '1px solid var(--color-warning)',
        }}>
          ADMIN — {currentUser?.username ?? 'admin'}
        </span>
      </div>

      {/* Risk Rules Card */}
      <div style={s.card}>
        <h2 style={s.cardTitle}>
          Risk Rule Configuration
          <button style={{ ...s.btn('secondary'), fontSize: '12px', padding: '4px 10px' }} onClick={loadRules}>
            ↻ Refresh
          </button>
        </h2>
        {rulesLoading ? (
          <div style={{ color: 'var(--color-text-muted)', fontSize: '13px', padding: '16px 0' }}>Loading rules…</div>
        ) : (
          <div>
            {/* Expand All / Collapse All */}
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '10px' }}>
              <button
                style={{ ...s.btn('ghost'), fontSize: '12px', padding: '3px 8px', display: 'flex', alignItems: 'center', gap: '4px' }}
                onClick={toggleAll}
              >
                {allExpanded ? <ChevronUp size={13} /> : <ChevronDown size={13} />}
                {allExpanded ? 'Collapse All' : 'Expand All'}
              </button>
            </div>

            {/* Grouped rule sections */}
            {RULE_GROUPS.map(group => {
              const groupRules = rules.filter(r => getRuleGroupKey(r) === group.key)
              if (groupRules.length === 0) return null
              const isExpanded = expandedGroups[group.key]
              return (
                <div key={group.key} style={{ marginBottom: '12px', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)', overflow: 'hidden' }}>
                  {/* Group header */}
                  <div
                    role="button"
                    aria-expanded={isExpanded}
                    onClick={() => toggleGroup(group.key)}
                    style={{
                      display: 'flex', alignItems: 'center', gap: '8px',
                      padding: '9px 14px', cursor: 'pointer',
                      background: 'var(--color-border-light)',
                      borderBottom: isExpanded ? '1px solid var(--color-border)' : 'none',
                      userSelect: 'none',
                    }}
                  >
                    <span style={{ color: 'var(--color-text-secondary)', display: 'flex', alignItems: 'center' }}>
                      {group.icon}
                    </span>
                    <span style={{ fontSize: '12px', fontWeight: 700, color: 'var(--color-text-primary)', flex: 1 }}>
                      {group.label}
                    </span>
                    <span style={{
                      fontSize: '10px', fontWeight: 700, padding: '1px 7px', borderRadius: 'var(--radius-pill)',
                      background: 'var(--color-primary-light)', color: 'var(--color-primary)',
                    }}>
                      {groupRules.length}
                    </span>
                    <span style={{ color: 'var(--color-text-muted)', display: 'flex', alignItems: 'center' }}>
                      {isExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                    </span>
                  </div>

                  {/* Collapsible rule table */}
                  {isExpanded && (
                    <div style={{ overflowX: 'auto' }}>
                      <table style={s.table}>
                        <thead>
                          <tr>
                            {['#', 'Rule Code', 'Action', 'Threshold', 'Enabled'].map(h => (
                              <th key={h} style={s.th}>{h}</th>
                            ))}
                          </tr>
                        </thead>
                        <tbody>
                          {groupRules.map(rule => (
                            <tr key={rule.ruleCode} style={{ opacity: rule.enabled ? 1 : 0.6 }}>
                              <td style={{ ...s.td, color: 'var(--color-text-muted)', fontSize: '11px', width: '28px' }}>{rule.priority}</td>
                              <td style={s.td}>
                                <div style={{ fontWeight: 600, color: 'var(--color-text-primary)', fontSize: '12px', fontFamily: 'monospace' }}>{rule.ruleCode}</div>
                                <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', marginTop: '1px' }}>{rule.description}</div>
                              </td>
                              <td style={s.td}><ActionBadge action={rule.action} /></td>
                              <td style={s.td}>
                                {rule.thresholdValue ? (
                                  <span
                                    style={s.thresholdValue}
                                    title="Click to edit threshold"
                                    onClick={() => setThresholdModal(rule)}
                                  >
                                    {fmtThreshold(rule)} ✎
                                  </span>
                                ) : (
                                  <span style={{ color: 'var(--color-text-muted)', fontSize: '12px' }}>—</span>
                                )}
                              </td>
                              <td style={s.td}>
                                <Toggle
                                  checked={rule.enabled}
                                  onChange={() => setToggleModal(rule)}
                                />
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* Pending Approvals Panel */}
      <PendingApprovalsPanel key={pendingRefreshKey} onChangeReviewed={() => { loadRules(); loadAudit() }} />

      {/* Risk Audit Log Card */}
      <div style={s.card}>
        <h2 style={s.cardTitle}>
          Rule Change Audit Log
          {/* TODO: SCRUM-47 — replace stub fallback in getRiskRuleAudit with real GET /api/v1/risk/rules/audit */}
          <button style={{ ...s.btn('secondary'), fontSize: '12px', padding: '4px 10px' }} onClick={loadAudit}>
            ↻ Refresh
          </button>
        </h2>
        {auditLoading ? (
          <div style={{ color: 'var(--color-text-muted)', fontSize: '13px', padding: '16px 0' }}>Loading audit log…</div>
        ) : auditLog.length === 0 ? (
          <div style={{ color: 'var(--color-text-muted)', fontSize: '13px', padding: '16px 0' }}>No changes recorded yet.</div>
        ) : (
          <table style={s.table}>
            <thead>
              <tr>
                {['Rule', 'Field', 'Change', 'Changed By', 'Reason', 'When'].map(h => (
                  <th key={h} style={s.th}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {auditLog.map(rec => (
                <tr key={rec.id}>
                  <td style={s.td}><span style={{ fontFamily: 'monospace', fontSize: '11px', color: 'var(--color-primary)' }}>{rec.ruleCode}</span></td>
                  <td style={s.td}><span style={{ fontSize: '11px', color: 'var(--color-text-primary)' }}>{rec.fieldChanged}</span></td>
                  <td style={s.td}>
                    <span style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>{rec.oldValue}</span>
                    <span style={{ color: 'var(--color-text-muted)', margin: '0 4px' }}>→</span>
                    <span style={{ fontSize: '11px', fontWeight: 600, color: 'var(--color-success)' }}>{rec.newValue}</span>
                  </td>
                  <td style={s.td}><span style={{ fontSize: '11px', color: 'var(--color-text-primary)' }}>{rec.changedBy}</span></td>
                  <td style={{ ...s.td, maxWidth: '180px' }}>
                    <span style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }} title={rec.changeReason}>
                      {rec.changeReason.length > 40 ? rec.changeReason.slice(0, 40) + '…' : rec.changeReason}
                    </span>
                  </td>
                  <td style={s.td}><span style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>{fmtDate(rec.changedAt)}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* User Management Card */}
      <div style={s.card}>
        <h2 style={s.cardTitle}>
          User Management
          {/* TODO: SCRUM-16 — wire Create User to real POST /api/v1/auth/users */}
          <button
            style={{ ...s.btn('primary'), fontSize: '12px', padding: '4px 10px' }}
            onClick={() => showToast('User creation not available — Iteration 1', 'error')}
          >
            + Create User
          </button>
        </h2>
        <div style={{ color: 'var(--color-text-muted)', fontSize: '13px', padding: '12px 0' }}>
          User management API not available in this iteration.
        </div>
      </div>

    </div>
  )
}

export default AdminPanelPage
