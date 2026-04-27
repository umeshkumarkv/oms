import http from './http'
import { PatchRiskRuleRequest, RiskRule, RiskRuleAuditRecord } from '../types/risk.types'

// ── Mock data (stub — TODO: SCRUM-47 remove when risk-service endpoints are live) ──
// Rules sourced from CLAUDE.md section 9 — Risk Engine

const now = Date.now()
const daysAgo = (n: number) => new Date(now - n * 86_400_000).toISOString()

export const MOCK_RULES: RiskRule[] = [
  { ruleCode: 'SINGLE_ORDER_QTY_LIMIT',   ruleName: 'Single Order Qty Limit',      ruleCategory: 'FINANCIAL_LIMIT', description: 'Reject if order quantity exceeds configured limit',              enabled: true,  thresholdValue: '10000',   thresholdUnit: 'SHARES',     action: 'REJECT', priority: 1 },
  { ruleCode: 'ORDER_VALUE_LIMIT',         ruleName: 'Order Value Limit',           ruleCategory: 'FINANCIAL_LIMIT', description: 'Reject if order notional value exceeds configured limit',         enabled: true,  thresholdValue: '1000000', thresholdUnit: 'USD',        action: 'REJECT', priority: 2 },
  { ruleCode: 'CLIENT_ACTIVE_CHECK',       ruleName: 'Client Active Check',         ruleCategory: 'REDUNDANT',       description: 'Redundant — already enforced in Level 2 validation',             enabled: false, thresholdValue: null,      thresholdUnit: null,         action: 'REJECT', priority: 3 },
  { ruleCode: 'INSTRUMENT_TRADABLE_CHECK', ruleName: 'Instrument Tradable Check',   ruleCategory: 'REDUNDANT',       description: 'Redundant — already enforced in Level 2 validation',             enabled: false, thresholdValue: null,      thresholdUnit: null,         action: 'REJECT', priority: 4 },
  { ruleCode: 'MARKET_HOURS_CHECK',        ruleName: 'Market Hours Check',          ruleCategory: 'REDUNDANT',       description: 'Redundant — already enforced in Level 2 validation',             enabled: false, thresholdValue: null,      thresholdUnit: null,         action: 'REJECT', priority: 5 },
  { ruleCode: 'QUANTITY_POSITIVE_CHECK',   ruleName: 'Quantity Positive Check',     ruleCategory: 'REDUNDANT',       description: 'Redundant — already enforced in Level 1 validation',             enabled: false, thresholdValue: null,      thresholdUnit: null,         action: 'REJECT', priority: 6 },
  { ruleCode: 'LIMIT_PRICE_CHECK',         ruleName: 'Limit Price Check',           ruleCategory: 'REDUNDANT',       description: 'Redundant — already enforced in Level 1 validation',             enabled: false, thresholdValue: null,      thresholdUnit: null,         action: 'REJECT', priority: 7 },
  { ruleCode: 'CASH_BALANCE_CHECK',        ruleName: 'Cash Balance Check',          ruleCategory: 'REDUNDANT',       description: 'Redundant — already enforced in Level 2 validation',             enabled: false, thresholdValue: null,      thresholdUnit: null,         action: 'REJECT', priority: 8 },
  { ruleCode: 'HOLDINGS_CHECK',            ruleName: 'Holdings Check',              ruleCategory: 'REDUNDANT',       description: 'Redundant — already enforced in Level 2 validation',             enabled: false, thresholdValue: null,      thresholdUnit: null,         action: 'REJECT', priority: 9 },
  { ruleCode: 'ORDER_QTY_SPIKE_CHECK',     ruleName: 'Order Qty Spike Check',       ruleCategory: 'REDUNDANT',       description: 'Reject if quantity exceeds Nx average order quantity',            enabled: false, thresholdValue: '10',      thresholdUnit: 'MULTIPLIER', action: 'REJECT', priority: 10 },
  { ruleCode: 'ORDER_VALUE_SPIKE_CHECK',   ruleName: 'Order Value Spike Check',     ruleCategory: 'REDUNDANT',       description: 'Reject if notional exceeds Nx average order value',               enabled: false, thresholdValue: '10',      thresholdUnit: 'MULTIPLIER', action: 'REJECT', priority: 11 },
  { ruleCode: 'PRICE_DEVIATION_CHECK',     ruleName: 'Price Deviation Check',       ruleCategory: 'REDUNDANT',       description: 'Reject if limit price deviates more than X% from last price',     enabled: false, thresholdValue: '10',      thresholdUnit: 'PERCENT',    action: 'REJECT', priority: 12 },
  { ruleCode: 'NOTIONAL_VALUE_CHECK',      ruleName: 'Notional Value Check',        ruleCategory: 'REDUNDANT',       description: 'Reject fat-finger orders exceeding notional value limit',          enabled: false, thresholdValue: '1000000', thresholdUnit: 'USD',        action: 'REJECT', priority: 13 },
]

const MOCK_AUDIT: RiskRuleAuditRecord[] = [
  { id: 'rra-1', ruleCode: 'SINGLE_ORDER_QTY_LIMIT', fieldChanged: 'thresholdValue', oldValue: '5000',    newValue: '10000', changedBy: 'admin01', changeReason: 'Increased limit per Q1 risk committee review', changedAt: daysAgo(2) },
  { id: 'rra-2', ruleCode: 'ORDER_VALUE_LIMIT',       fieldChanged: 'enabled',        oldValue: 'false',   newValue: 'true',  changedBy: 'admin01', changeReason: 'Enabling for production go-live',              changedAt: daysAgo(5) },
  { id: 'rra-3', ruleCode: 'PRICE_DEVIATION_CHECK',   fieldChanged: 'thresholdValue', oldValue: '15',      newValue: '10',    changedBy: 'admin01', changeReason: 'Tightening fat finger controls per compliance', changedAt: daysAgo(7) },
]

// ── API functions ─────────────────────────────────────────────────────────────

// TODO: SCRUM-47 — replace stub fallback with real GET /api/v1/risk/rules
export const getRiskRules = async (): Promise<RiskRule[]> => {
  try {
    const res = await http.get('/risk/rules')
    return res.data?.data ?? MOCK_RULES
  } catch {
    return MOCK_RULES
  }
}

// TODO: SCRUM-47 — replace stub fallback with real PATCH /api/v1/risk/rules/{ruleCode}
export const patchRiskRule = async (ruleCode: string, body: PatchRiskRuleRequest): Promise<RiskRule> => {
  try {
    const res = await http.patch(`/risk/rules/${ruleCode}`, body)
    if (!res.data?.data) throw new Error('EMPTY_RESPONSE')
    return res.data.data
  } catch {
    // Simulate successful update in stub mode
    const rule = MOCK_RULES.find(r => r.ruleCode === ruleCode)
    if (!rule) throw new Error('RULE_NOT_FOUND')
    if (body.enabled !== undefined)        rule.enabled        = body.enabled
    if (body.thresholdValue !== undefined) rule.thresholdValue = body.thresholdValue
    return { ...rule }
  }
}

// TODO: SCRUM-47 — replace stub fallback with real GET /api/v1/risk/rules/audit
export const getRiskRuleAudit = async (): Promise<RiskRuleAuditRecord[]> => {
  try {
    const res = await http.get('/risk/rules/audit')
    return res.data?.data ?? MOCK_AUDIT
  } catch {
    return MOCK_AUDIT
  }
}

// TODO: SCRUM-27 — replace stub fallback with real POST /api/v1/admin/scenario
export const triggerScenario = async (scenarioId: string, symbol: string): Promise<void> => {
  try {
    await http.post('/admin/scenario', { scenarioId, symbol })
  } catch {
    // stub — no-op in demo mode
  }
}

// TODO: SCRUM-27 — replace stub fallback with real POST /api/v1/admin/reset
export const resetSystem = async (): Promise<void> => {
  try {
    await http.post('/admin/reset')
  } catch {
    // stub — no-op in demo mode
  }
}
