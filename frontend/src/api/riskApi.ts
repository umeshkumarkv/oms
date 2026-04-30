import http from './http'
import { PatchRiskRuleRequest, RiskRule, RiskRuleAuditRecord } from '../types/risk.types'

export const getRiskRules = async (): Promise<RiskRule[]> => {
  const res = await http.get('/risk/rules')
  return res.data.data
}

export const patchRiskRule = async (ruleCode: string, body: PatchRiskRuleRequest): Promise<RiskRule> => {
  const res = await http.patch(`/risk/rules/${ruleCode}`, body)
  return res.data.data
}

export const getRiskRuleAudit = async (): Promise<RiskRuleAuditRecord[]> => {
  const res = await http.get('/risk/rules/audit')
  return res.data.data
}

export const triggerScenario = async (scenarioId: string, symbol: string): Promise<void> => {
  await http.post('/admin/scenario', { scenarioId, symbol })
}

export const resetSystem = async (): Promise<void> => {
  await http.post('/admin/reset')
}
