export type RuleAction   = 'REJECT' | 'WARN'
export type RuleCategory =
  | 'FINANCIAL_LIMIT'
  | 'FAT_FINGER'
  | 'PRE_TRADE'
  | 'CLIENT'
  | 'STATEFUL'
  | 'REDUNDANT'

export interface RiskRule {
  ruleCode:       string
  ruleName:       string
  ruleCategory:   RuleCategory
  description:    string
  enabled:        boolean
  thresholdValue: string | null
  thresholdUnit:  string | null
  action:         RuleAction
  priority:       number
}

export interface PatchRiskRuleRequest {
  enabled?:        boolean
  thresholdValue?: string
  changeReason:    string
}

export interface RiskRuleAuditRecord {
  id:           string
  ruleCode:     string
  fieldChanged: string
  oldValue:     string
  newValue:     string
  changedBy:    string
  changeReason: string
  changedAt:    string
}
