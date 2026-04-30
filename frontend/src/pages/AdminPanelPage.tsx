import React, { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getRiskRuleAudit, getRiskRules, patchRiskRule, resetSystem, triggerScenario } from '../api/riskApi'
import { getCurrentUser } from '../api/authApi'
import { RiskRule, RiskRuleAuditRecord } from '../types/risk.types'

// ── Demo scenarios (must match application.yml in marketdata-service) ─────────
const DEMO_SCENARIOS = [
  { id: 'SCN-AAPL-DROP-5PCT', label: 'AAPL — Drop 5% over 10 ticks (~20s)',  symbol: 'AAPL' },
  { id: 'SCN-AAPL-RISE-5PCT', label: 'AAPL — Rise 5% over 10 ticks (~20s)',  symbol: 'AAPL' },
  { id: 'SCN-TSLA-DROP-5PCT', label: 'TSLA — Drop 5% over 10 ticks (~20s)',  symbol: 'TSLA' },
  { id: 'SCN-TSLA-RISE-5PCT', label: 'TSLA — Rise 5% over 10 ticks (~20s)',  symbol: 'TSLA' },
  { id: 'SCN-MSFT-RISE-3PCT', label: 'MSFT — Rise 3% over 6 ticks (~12s)',   symbol: 'MSFT' },
]

// ── Styles ─────────────────────────────────────────────────────────────────────
const s = {
  page:     { fontFamily: 'system-ui, sans-serif', padding: '20px', minHeight: 'calc(100vh - 52px)', background: '#f5f6fa' } as React.CSSProperties,
  header:   { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' } as React.CSSProperties,
  title:    { margin: 0, fontSize: '20px', fontWeight: 700, color: '#1a2236' } as React.CSSProperties,
  subtitle: { fontSize: '12px', color: '#9ca3af', marginTop: '3px' } as React.CSSProperties,

  grid:     { display: 'grid', gridTemplateColumns: '1.4fr 1fr', gap: '20px', alignItems: 'start' } as React.CSSProperties,

  card:     { background: '#fff', borderRadius: '10px', boxShadow: '0 1px 3px rgba(0,0,0,0.08)', padding: '20px', marginBottom: '16px' } as React.CSSProperties,
  cardTitle:{ margin: '0 0 14px', fontSize: '14px', fontWeight: 700, color: '#1a2236', paddingBottom: '10px', borderBottom: '1px solid #f0f0f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' } as React.CSSProperties,

  table:    { width: '100%', borderCollapse: 'collapse' as const, fontSize: '12px' } as React.CSSProperties,
  th:       { padding: '8px 10px', textAlign: 'left' as const, fontSize: '10px', fontWeight: 700, color: '#6b7280', borderBottom: '1px solid #f0f0f0', background: '#fafafa', textTransform: 'uppercase' as const, letterSpacing: '0.03em', whiteSpace: 'nowrap' as const } as React.CSSProperties,
  td:       { padding: '9px 10px', borderBottom: '1px solid #f8f8f8', verticalAlign: 'middle' as const } as React.CSSProperties,

  input:    { width: '100%', padding: '8px 10px', borderRadius: '6px', border: '1px solid #d1d5db', fontSize: '13px', boxSizing: 'border-box' as const, outline: 'none' } as React.CSSProperties,
  textarea: { width: '100%', padding: '8px 10px', borderRadius: '6px', border: '1px solid #d1d5db', fontSize: '13px', boxSizing: 'border-box' as const, outline: 'none', resize: 'vertical' as const, minHeight: '72px' } as React.CSSProperties,
  select:   { width: '100%', padding: '8px 10px', borderRadius: '6px', border: '1px solid #d1d5db', fontSize: '13px', background: '#fff', outline: 'none', cursor: 'pointer' } as React.CSSProperties,

  btn: (variant: 'primary' | 'danger' | 'secondary' | 'ghost', disabled?: boolean) => {
    const base = { padding: '7px 14px', borderRadius: '6px', fontSize: '13px', fontWeight: 600, cursor: disabled ? 'not-allowed' : 'pointer', border: 'none', transition: 'opacity 0.15s', opacity: disabled ? 0.5 : 1 }
    const map = {
      primary:   { background: '#6366f1', color: '#fff' },
      danger:    { background: '#dc2626', color: '#fff' },
      secondary: { background: '#fff',    color: '#374151', border: '1px solid #d1d5db' },
      ghost:     { background: 'transparent', color: '#6b7280' },
    }
    return { ...base, ...map[variant] } as React.CSSProperties
  },

  // Modal
  overlay:    { position: 'fixed' as const, inset: 0, background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 500 },
  modal:      { background: '#fff', borderRadius: '12px', padding: '24px', width: '440px', maxWidth: '90vw', boxShadow: '0 8px 40px rgba(0,0,0,0.2)' } as React.CSSProperties,
  modalTitle: { margin: '0 0 6px', fontSize: '16px', fontWeight: 700, color: '#1a2236' } as React.CSSProperties,
  modalSub:   { margin: '0 0 16px', fontSize: '13px', color: '#6b7280' } as React.CSSProperties,
  modalFoot:  { display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' } as React.CSSProperties,
  fieldGroup: { marginBottom: '14px' } as React.CSSProperties,
  label:      { display: 'block', fontSize: '11px', fontWeight: 600, color: '#6b7280', marginBottom: '5px', textTransform: 'uppercase' as const, letterSpacing: '0.04em' },
  hint:       { fontSize: '11px', color: '#9ca3af', marginTop: '4px' } as React.CSSProperties,

  // Toast
  toast: (type: 'success' | 'error') => ({
    position: 'fixed' as const, bottom: '24px', right: '24px',
    padding: '12px 18px', borderRadius: '8px',
    background: type === 'success' ? '#059669' : '#dc2626',
    color: '#fff', fontSize: '13px', fontWeight: 500,
    boxShadow: '0 4px 16px rgba(0,0,0,0.2)', zIndex: 9999,
    maxWidth: '360px',
  }),

  // Inline threshold edit
  thresholdCell: { display: 'flex', alignItems: 'center', gap: '6px' } as React.CSSProperties,
  thresholdValue: { fontSize: '12px', color: '#374151', cursor: 'pointer', padding: '2px 4px', borderRadius: '3px', borderBottom: '1px dashed #9ca3af' } as React.CSSProperties,
}

// ── Sub-components ─────────────────────────────────────────────────────────────

const Toggle: React.FC<{ checked: boolean; onChange: () => void }> = ({ checked, onChange }) => (
  <div
    onClick={onChange}
    role="switch"
    aria-checked={checked}
    style={{
      width: '38px', height: '20px', borderRadius: '10px',
      background: checked ? '#10b981' : '#d1d5db',
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
    background: action === 'REJECT' ? '#fee2e2' : '#fef3c7',
    color:      action === 'REJECT' ? '#dc2626' : '#d97706',
  }}>
    {action}
  </span>
)

const CategoryBadge: React.FC<{ cat: string }> = ({ cat }) => (
  <span style={{
    display: 'inline-block', padding: '2px 6px', borderRadius: '4px',
    fontSize: '10px', fontWeight: 600,
    background: cat === 'FINANCIAL_LIMIT' ? '#dbeafe' : '#f3f4f6',
    color:      cat === 'FINANCIAL_LIMIT' ? '#1d4ed8' : '#6b7280',
  }}>
    {cat === 'FINANCIAL_LIMIT' ? 'Financial' : 'Redundant'}
  </span>
)

// ── Confirm modal used for toggle changes ──────────────────────────────────────
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
          <label style={s.label}>Change Reason <span style={{ color: '#ef4444' }}>*</span></label>
          <textarea
            style={{ ...s.textarea, borderColor: reason.length > 0 && !valid ? '#ef4444' : '#d1d5db' }}
            placeholder="Describe why you are making this change (min 10 characters)"
            value={reason}
            onChange={e => setReason(e.target.value)}
            autoFocus
          />
          {reason.length > 0 && !valid && (
            <div style={{ fontSize: '11px', color: '#ef4444', marginTop: '4px' }}>
              Minimum 10 characters required ({reason.trim().length}/10)
            </div>
          )}
          {valid && (
            <div style={{ fontSize: '11px', color: '#059669', marginTop: '4px' }}>✓ Reason recorded</div>
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
            style={{ ...s.input, borderColor: value.length > 0 && !valueValid ? '#ef4444' : '#d1d5db' }}
            type="number"
            value={value}
            onChange={e => setValue(e.target.value)}
            autoFocus
          />
          <div style={s.hint}>Current value: {rule.thresholdValue ?? '—'} {rule.thresholdUnit ?? ''}</div>
        </div>
        <div style={s.fieldGroup}>
          <label style={s.label}>Change Reason <span style={{ color: '#ef4444' }}>*</span></label>
          <textarea
            style={{ ...s.textarea, borderColor: reason.length > 0 && !reasonValid ? '#ef4444' : '#d1d5db' }}
            placeholder="Describe why you are changing this threshold (min 10 characters)"
            value={reason}
            onChange={e => setReason(e.target.value)}
          />
          {reason.length > 0 && !reasonValid && (
            <div style={{ fontSize: '11px', color: '#ef4444', marginTop: '4px' }}>
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

// ── Confirmation modal (scenario / reset) ──────────────────────────────────────
interface ConfirmModalProps {
  title: string
  message: string
  confirmLabel: string
  danger?: boolean
  onConfirm: () => void
  onCancel: () => void
}

const ConfirmModal: React.FC<ConfirmModalProps> = ({ title, message, confirmLabel, danger, onConfirm, onCancel }) => (
  <div style={s.overlay} onClick={onCancel}>
    <div style={s.modal} onClick={e => e.stopPropagation()}>
      <h3 style={{ ...s.modalTitle, color: danger ? '#dc2626' : '#1a2236' }}>{title}</h3>
      <p style={{ fontSize: '13px', color: '#374151', lineHeight: 1.6, margin: '0 0 4px' }}>{message}</p>
      <div style={s.modalFoot}>
        <button style={s.btn('secondary')} onClick={onCancel}>Cancel</button>
        <button style={s.btn(danger ? 'danger' : 'primary')} onClick={onConfirm}>{confirmLabel}</button>
      </div>
    </div>
  </div>
)

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

  // Toggle modal
  const [toggleModal, setToggleModal] = useState<RiskRule | null>(null)

  // Threshold modal
  const [thresholdModal, setThresholdModal] = useState<RiskRule | null>(null)

  // Scenario state
  const [selectedScenario,  setSelectedScenario]  = useState(DEMO_SCENARIOS[0].id)
  const [scenarioConfirm,   setScenarioConfirm]   = useState(false)
  const [scenarioLoading,   setScenarioLoading]   = useState(false)

  // Reset state
  const [resetConfirm,  setResetConfirm]  = useState(false)
  const [resetLoading,  setResetLoading]  = useState(false)

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

  // ── Toggle handler ───────────────────────────────────────────────────────
  const handleToggleConfirm = async (reason: string) => {
    if (!toggleModal) return
    const rule     = toggleModal
    const newValue = !rule.enabled

    // Optimistic update
    setRules(prev => prev.map(r => r.ruleCode === rule.ruleCode ? { ...r, enabled: newValue } : r))
    setToggleModal(null)

    try {
      // TODO: SCRUM-47 — replace stub fallback in patchRiskRule with real PATCH /api/v1/risk/rules/{ruleCode}
      await patchRiskRule(rule.ruleCode, { enabled: newValue, changeReason: reason })
      showToast(`${rule.ruleCode} ${newValue ? 'enabled' : 'disabled'} successfully`, 'success')
      loadAudit()
    } catch {
      // Revert optimistic update
      setRules(prev => prev.map(r => r.ruleCode === rule.ruleCode ? { ...r, enabled: rule.enabled } : r))
      showToast(`Failed to update ${rule.ruleCode}`, 'error')
    }
  }

  // ── Threshold handler ─────────────────────────────────────────────────────
  const handleThresholdConfirm = async (newValue: string, reason: string) => {
    if (!thresholdModal) return
    const rule = thresholdModal
    const prev = rule.thresholdValue

    // Optimistic update
    setRules(r => r.map(x => x.ruleCode === rule.ruleCode ? { ...x, thresholdValue: newValue } : x))
    setThresholdModal(null)

    try {
      // TODO: SCRUM-47 — replace stub fallback in patchRiskRule with real PATCH /api/v1/risk/rules/{ruleCode}
      await patchRiskRule(rule.ruleCode, { thresholdValue: newValue, changeReason: reason })
      showToast(`${rule.ruleCode} threshold updated to ${newValue}`, 'success')
      loadAudit()
    } catch {
      setRules(r => r.map(x => x.ruleCode === rule.ruleCode ? { ...x, thresholdValue: prev } : x))
      showToast(`Failed to update threshold for ${rule.ruleCode}`, 'error')
    }
  }

  // ── Scenario handler ──────────────────────────────────────────────────────
  const handleScenarioTrigger = async () => {
    setScenarioConfirm(false)
    setScenarioLoading(true)
    const scn = DEMO_SCENARIOS.find(sc => sc.id === selectedScenario)!
    try {
      // TODO: SCRUM-27 — replace stub fallback in triggerScenario with real POST /api/v1/admin/scenario
      await triggerScenario(scn.id, scn.symbol)
      showToast(`Scenario ${scn.id} triggered for ${scn.symbol}`, 'success')
    } catch {
      showToast('Failed to trigger scenario', 'error')
    } finally {
      setScenarioLoading(false)
    }
  }

  // ── Reset handler ─────────────────────────────────────────────────────────
  const handleReset = async () => {
    setResetConfirm(false)
    setResetLoading(true)
    try {
      // TODO: SCRUM-27 — replace stub fallback in resetSystem with real POST /api/v1/admin/reset
      await resetSystem()
      showToast('System reset successfully — market data restored to seed values', 'success')
    } catch {
      showToast('System reset failed', 'error')
    } finally {
      setResetLoading(false)
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
      {scenarioConfirm && (
        <ConfirmModal
          title="Trigger Demo Scenario"
          message={`Start scenario: ${DEMO_SCENARIOS.find(sc => sc.id === selectedScenario)?.label}?`}
          confirmLabel="Trigger"
          onConfirm={handleScenarioTrigger}
          onCancel={() => setScenarioConfirm(false)}
        />
      )}
      {resetConfirm && (
        <ConfirmModal
          title="Reset System"
          message="This will restore all market data prices to seed values and clear any active price scenarios. This action cannot be undone."
          confirmLabel="Reset System"
          danger
          onConfirm={handleReset}
          onCancel={() => setResetConfirm(false)}
        />
      )}

      {/* Header */}
      <div style={s.header}>
        <div>
          <h1 style={s.title}>Admin Panel</h1>
          <div style={s.subtitle}>Risk rule management · User management · Demo controls</div>
        </div>
        <span style={{
          padding: '4px 12px', borderRadius: '6px', fontSize: '12px', fontWeight: 700,
          background: '#fef3c7', color: '#d97706', border: '1px solid #fcd34d',
        }}>
          ADMIN — {currentUser?.username ?? 'admin'}
        </span>
      </div>

      <div style={s.grid}>

        {/* ── LEFT COLUMN ── */}
        <div>

          {/* Risk Rules Card */}
          <div style={s.card}>
            <h2 style={s.cardTitle}>
              Risk Rule Configuration
              <button style={{ ...s.btn('secondary'), fontSize: '12px', padding: '4px 10px' }} onClick={loadRules}>
                ↻ Refresh
              </button>
            </h2>
            {rulesLoading ? (
              <div style={{ color: '#9ca3af', fontSize: '13px', padding: '16px 0' }}>Loading rules…</div>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table style={s.table}>
                  <thead>
                    <tr>
                      {['#', 'Rule Code', 'Category', 'Action', 'Threshold', 'Enabled'].map(h => (
                        <th key={h} style={s.th}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {rules.map(rule => (
                      <tr key={rule.ruleCode} style={{ opacity: rule.enabled ? 1 : 0.6 }}>
                        <td style={{ ...s.td, color: '#9ca3af', fontSize: '11px', width: '28px' }}>{rule.priority}</td>
                        <td style={s.td}>
                          <div style={{ fontWeight: 600, color: '#1a2236', fontSize: '12px', fontFamily: 'monospace' }}>{rule.ruleCode}</div>
                          <div style={{ fontSize: '11px', color: '#9ca3af', marginTop: '1px' }}>{rule.description}</div>
                        </td>
                        <td style={s.td}><CategoryBadge cat={rule.ruleCategory} /></td>
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
                            <span style={{ color: '#9ca3af', fontSize: '12px' }}>—</span>
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
              <div style={{ color: '#9ca3af', fontSize: '13px', padding: '16px 0' }}>Loading audit log…</div>
            ) : auditLog.length === 0 ? (
              <div style={{ color: '#9ca3af', fontSize: '13px', padding: '16px 0' }}>No changes recorded yet.</div>
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
                      <td style={s.td}><span style={{ fontFamily: 'monospace', fontSize: '11px', color: '#6366f1' }}>{rec.ruleCode}</span></td>
                      <td style={s.td}><span style={{ fontSize: '11px', color: '#374151' }}>{rec.fieldChanged}</span></td>
                      <td style={s.td}>
                        <span style={{ fontSize: '11px', color: '#6b7280' }}>{rec.oldValue}</span>
                        <span style={{ color: '#9ca3af', margin: '0 4px' }}>→</span>
                        <span style={{ fontSize: '11px', fontWeight: 600, color: '#059669' }}>{rec.newValue}</span>
                      </td>
                      <td style={s.td}><span style={{ fontSize: '11px', color: '#374151' }}>{rec.changedBy}</span></td>
                      <td style={{ ...s.td, maxWidth: '180px' }}>
                        <span style={{ fontSize: '11px', color: '#6b7280' }} title={rec.changeReason}>
                          {rec.changeReason.length > 40 ? rec.changeReason.slice(0, 40) + '…' : rec.changeReason}
                        </span>
                      </td>
                      <td style={s.td}><span style={{ fontSize: '11px', color: '#9ca3af' }}>{fmtDate(rec.changedAt)}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

        </div>

        {/* ── RIGHT COLUMN ── */}
        <div>

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
            <div style={{ color: '#9ca3af', fontSize: '13px', padding: '12px 0' }}>
              User management API not available in this iteration.
            </div>
          </div>

          {/* Demo Controls Card */}
          <div style={s.card}>
            <h2 style={s.cardTitle}>Demo Controls</h2>

            {/* Scenario trigger */}
            <div style={{ marginBottom: '20px' }}>
              <div style={{ fontSize: '13px', fontWeight: 600, color: '#374151', marginBottom: '4px' }}>
                Trigger Price Scenario
              </div>
              <div style={{ fontSize: '12px', color: '#6b7280', marginBottom: '10px' }}>
                Force a scripted price movement on a symbol for demonstration purposes.
              </div>
              <select
                style={{ ...s.select, marginBottom: '8px' }}
                value={selectedScenario}
                onChange={e => setSelectedScenario(e.target.value)}
              >
                {DEMO_SCENARIOS.map(scn => (
                  <option key={scn.id} value={scn.id}>{scn.label}</option>
                ))}
              </select>
              <button
                style={s.btn('primary', scenarioLoading)}
                disabled={scenarioLoading}
                onClick={() => setScenarioConfirm(true)}
              >
                {scenarioLoading ? 'Triggering…' : '▶ Trigger Scenario'}
              </button>
              {/* TODO: SCRUM-27 — wire to real POST /api/v1/admin/scenario */}
            </div>

            <div style={{ borderTop: '1px solid #f0f0f0', marginBottom: '20px' }} />

            {/* System reset */}
            <div>
              <div style={{ fontSize: '13px', fontWeight: 600, color: '#374151', marginBottom: '4px' }}>
                System Reset
              </div>
              <div style={{ fontSize: '12px', color: '#6b7280', marginBottom: '10px' }}>
                Restore all market data prices to seed values and clear active scenarios. Use after a demo session.
              </div>
              <button
                style={s.btn('danger', resetLoading)}
                disabled={resetLoading}
                onClick={() => setResetConfirm(true)}
              >
                {resetLoading ? 'Resetting…' : '⚠ Reset System'}
              </button>
              {/* TODO: SCRUM-27 — wire to real POST /api/v1/admin/reset */}
            </div>

          </div>

        </div>
      </div>

    </div>
  )
}

export default AdminPanelPage
