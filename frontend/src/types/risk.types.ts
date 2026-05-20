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

export type PendingChangeStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type PendingChangeType   = 'ENABLE' | 'DISABLE' | 'UPDATE_THRESHOLD'

export interface PendingChange {
  id:                string
  ruleCode:          string
  changeType:        PendingChangeType
  proposedEnabled:   boolean | null
  proposedThreshold: string | null
  makerUserId:       string
  makerUsername:     string
  status:            PendingChangeStatus
  makerNote:         string | null
  rejectionReason:   string | null
  checkerUserId:     string | null
  submittedAt:       string
  reviewedAt:        string | null
}
