import http from './http'
import { PatchRiskRuleRequest, PendingChange, RiskRule, RiskRuleAuditRecord } from '../types/risk.types'

export const getRiskRules = async (): Promise<RiskRule[]> => {
  const res = await http.get('/risk/rules')
  return res.data.data
}

export const patchRiskRule = async (ruleCode: string, body: PatchRiskRuleRequest): Promise<PendingChange> => {
  const res = await http.patch(`/risk/rules/${ruleCode}`, body)
  return res.data.data
}

export const getRiskRuleAudit = async (): Promise<RiskRuleAuditRecord[]> => {
  const res = await http.get('/risk/rules/audit')
  return res.data.data
}

export const getPendingChanges = async (status?: string): Promise<PendingChange[]> => {
  const url = status ? `/risk/rules/pending-changes?status=${status}` : '/risk/rules/pending-changes'
  const res = await http.get(url)
  return res.data.data
}

export const approveChange = async (changeId: string): Promise<PendingChange> => {
  const res = await http.patch(`/risk/rules/pending-changes/${changeId}/approve`)
  return res.data.data
}

export const rejectChange = async (changeId: string, rejectionReason: string): Promise<PendingChange> => {
  const res = await http.patch(`/risk/rules/pending-changes/${changeId}/reject`, { rejectionReason })
  return res.data.data
}
