import http from './http'
import { AuditRecord } from '../types/audit.types'

export interface AuditTrailResponse {
  records: AuditRecord[]
  totalElements: number
}

// ── Mock audit data (stub — TODO: SCRUM-49 remove when audit-service is live) ──
const now = Date.now()
const mins = (n: number) => new Date(now - n * 60_000).toISOString()
const hrs  = (n: number) => new Date(now - n * 60 * 60_000).toISOString()

const MOCK_AUDIT: Record<string, AuditRecord[]> = {
  // TSLA FILLED — b3
  'b3000000-0000-0000-0000-000000000003': [
    { id: 'a3-1', entityType: 'ORDER', entityId: 'b3000000-0000-0000-0000-000000000003', previousState: undefined,            newState: 'NEW',               actorId: 'user-john-001', actorType: 'USER',   eventTimestamp: mins(92) },
    { id: 'a3-2', entityType: 'ORDER', entityId: 'b3000000-0000-0000-0000-000000000003', previousState: 'NEW',               newState: 'VALIDATED',          actorId: 'OMS',           actorType: 'SYSTEM', eventTimestamp: mins(91) },
    { id: 'a3-3', entityType: 'ORDER', entityId: 'b3000000-0000-0000-0000-000000000003', previousState: 'VALIDATED',         newState: 'RISK_CHECK_PASSED',  actorId: 'RISK_ENGINE',   actorType: 'SYSTEM', eventTimestamp: mins(91) },
    { id: 'a3-4', entityType: 'ORDER', entityId: 'b3000000-0000-0000-0000-000000000003', previousState: 'RISK_CHECK_PASSED', newState: 'ACCEPTED',           actorId: 'OMS',           actorType: 'SYSTEM', eventTimestamp: mins(90) },
    { id: 'a3-5', entityType: 'ORDER', entityId: 'b3000000-0000-0000-0000-000000000003', previousState: 'ACCEPTED',          newState: 'SENT_TO_EXECUTION',  actorId: 'OMS',           actorType: 'SYSTEM', eventTimestamp: mins(90) },
    { id: 'a3-6', entityType: 'ORDER', entityId: 'b3000000-0000-0000-0000-000000000003', previousState: 'SENT_TO_EXECUTION', newState: 'FILLED',             actorId: 'EXECUTION_ENGINE', actorType: 'SYSTEM', eventTimestamp: mins(88), metadata: 'fillType=FULL executedQty=20 executedPrice=245.70' },
  ],
  // MSFT SENT_TO_EXECUTION — b2
  'b2000000-0000-0000-0000-000000000002': [
    { id: 'a2-1', entityType: 'ORDER', entityId: 'b2000000-0000-0000-0000-000000000002', previousState: undefined,            newState: 'NEW',               actorId: 'user-john-001', actorType: 'USER',   eventTimestamp: mins(27) },
    { id: 'a2-2', entityType: 'ORDER', entityId: 'b2000000-0000-0000-0000-000000000002', previousState: 'NEW',               newState: 'VALIDATED',          actorId: 'OMS',           actorType: 'SYSTEM', eventTimestamp: mins(26) },
    { id: 'a2-3', entityType: 'ORDER', entityId: 'b2000000-0000-0000-0000-000000000002', previousState: 'VALIDATED',         newState: 'RISK_CHECK_PASSED',  actorId: 'RISK_ENGINE',   actorType: 'SYSTEM', eventTimestamp: mins(26) },
    { id: 'a2-4', entityType: 'ORDER', entityId: 'b2000000-0000-0000-0000-000000000002', previousState: 'RISK_CHECK_PASSED', newState: 'ACCEPTED',           actorId: 'OMS',           actorType: 'SYSTEM', eventTimestamp: mins(25) },
    { id: 'a2-5', entityType: 'ORDER', entityId: 'b2000000-0000-0000-0000-000000000002', previousState: 'ACCEPTED',          newState: 'SENT_TO_EXECUTION',  actorId: 'OMS',           actorType: 'SYSTEM', eventTimestamp: mins(25) },
  ],
  // GOOGL RISK_CHECK_PASSED — b4
  'b4000000-0000-0000-0000-000000000004': [
    { id: 'a4-1', entityType: 'ORDER', entityId: 'b4000000-0000-0000-0000-000000000004', previousState: undefined,    newState: 'NEW',              actorId: 'user-john-001', actorType: 'USER',   eventTimestamp: mins(47) },
    { id: 'a4-2', entityType: 'ORDER', entityId: 'b4000000-0000-0000-0000-000000000004', previousState: 'NEW',        newState: 'VALIDATED',        actorId: 'OMS',           actorType: 'SYSTEM', eventTimestamp: mins(46) },
    { id: 'a4-3', entityType: 'ORDER', entityId: 'b4000000-0000-0000-0000-000000000004', previousState: 'VALIDATED',  newState: 'RISK_CHECK_PASSED', actorId: 'RISK_ENGINE', actorType: 'SYSTEM', eventTimestamp: mins(45) },
  ],
  // NVDA VALIDATED — b5
  'b5000000-0000-0000-0000-000000000005': [
    { id: 'a5-1', entityType: 'ORDER', entityId: 'b5000000-0000-0000-0000-000000000005', previousState: undefined, newState: 'NEW',       actorId: 'user-sarah-002', actorType: 'USER',   eventTimestamp: mins(6) },
    { id: 'a5-2', entityType: 'ORDER', entityId: 'b5000000-0000-0000-0000-000000000005', previousState: 'NEW',     newState: 'VALIDATED', actorId: 'OMS',            actorType: 'SYSTEM', eventTimestamp: mins(5) },
  ],
  // META EXPIRED — b6
  'b6000000-0000-0000-0000-000000000006': [
    { id: 'a6-1', entityType: 'ORDER', entityId: 'b6000000-0000-0000-0000-000000000006', previousState: undefined,            newState: 'NEW',              actorId: 'user-mike-003', actorType: 'USER',   eventTimestamp: hrs(8) },
    { id: 'a6-2', entityType: 'ORDER', entityId: 'b6000000-0000-0000-0000-000000000006', previousState: 'NEW',               newState: 'VALIDATED',        actorId: 'OMS',           actorType: 'SYSTEM', eventTimestamp: hrs(8) },
    { id: 'a6-3', entityType: 'ORDER', entityId: 'b6000000-0000-0000-0000-000000000006', previousState: 'VALIDATED',         newState: 'RISK_CHECK_PASSED', actorId: 'RISK_ENGINE',  actorType: 'SYSTEM', eventTimestamp: hrs(8) },
    { id: 'a6-4', entityType: 'ORDER', entityId: 'b6000000-0000-0000-0000-000000000006', previousState: 'RISK_CHECK_PASSED', newState: 'ACCEPTED',          actorId: 'OMS',          actorType: 'SYSTEM', eventTimestamp: hrs(8) },
    { id: 'a6-5', entityType: 'ORDER', entityId: 'b6000000-0000-0000-0000-000000000006', previousState: 'ACCEPTED',          newState: 'SENT_TO_EXECUTION', actorId: 'OMS',          actorType: 'SYSTEM', eventTimestamp: hrs(8) },
    { id: 'a6-6', entityType: 'ORDER', entityId: 'b6000000-0000-0000-0000-000000000006', previousState: 'SENT_TO_EXECUTION', newState: 'EXPIRED',           actorId: 'SCHEDULER',    actorType: 'SYSTEM', eventTimestamp: hrs(0) },
  ],
  // AAPL FILLED — b7
  'b7000000-0000-0000-0000-000000000007': [
    { id: 'a7-1', entityType: 'ORDER', entityId: 'b7000000-0000-0000-0000-000000000007', previousState: undefined,            newState: 'NEW',               actorId: 'user-john-001',    actorType: 'USER',   eventTimestamp: hrs(3) },
    { id: 'a7-2', entityType: 'ORDER', entityId: 'b7000000-0000-0000-0000-000000000007', previousState: 'NEW',               newState: 'VALIDATED',          actorId: 'OMS',              actorType: 'SYSTEM', eventTimestamp: hrs(3) },
    { id: 'a7-3', entityType: 'ORDER', entityId: 'b7000000-0000-0000-0000-000000000007', previousState: 'VALIDATED',         newState: 'RISK_CHECK_PASSED',  actorId: 'RISK_ENGINE',      actorType: 'SYSTEM', eventTimestamp: hrs(3) },
    { id: 'a7-4', entityType: 'ORDER', entityId: 'b7000000-0000-0000-0000-000000000007', previousState: 'RISK_CHECK_PASSED', newState: 'ACCEPTED',           actorId: 'OMS',              actorType: 'SYSTEM', eventTimestamp: hrs(3) },
    { id: 'a7-5', entityType: 'ORDER', entityId: 'b7000000-0000-0000-0000-000000000007', previousState: 'ACCEPTED',          newState: 'SENT_TO_EXECUTION',  actorId: 'OMS',              actorType: 'SYSTEM', eventTimestamp: hrs(3) },
    { id: 'a7-6', entityType: 'ORDER', entityId: 'b7000000-0000-0000-0000-000000000007', previousState: 'SENT_TO_EXECUTION', newState: 'FILLED',             actorId: 'EXECUTION_ENGINE', actorType: 'SYSTEM', eventTimestamp: hrs(3), metadata: 'fillType=FULL executedQty=50 executedPrice=178.35' },
  ],
  // MSFT ACCEPTED — b8
  'b8000000-0000-0000-0000-000000000008': [
    { id: 'a8-1', entityType: 'ORDER', entityId: 'b8000000-0000-0000-0000-000000000008', previousState: undefined,            newState: 'NEW',              actorId: 'user-emily-004', actorType: 'USER',   eventTimestamp: mins(62) },
    { id: 'a8-2', entityType: 'ORDER', entityId: 'b8000000-0000-0000-0000-000000000008', previousState: 'NEW',               newState: 'VALIDATED',        actorId: 'OMS',            actorType: 'SYSTEM', eventTimestamp: mins(61) },
    { id: 'a8-3', entityType: 'ORDER', entityId: 'b8000000-0000-0000-0000-000000000008', previousState: 'VALIDATED',         newState: 'RISK_CHECK_PASSED', actorId: 'RISK_ENGINE',   actorType: 'SYSTEM', eventTimestamp: mins(61) },
    { id: 'a8-4', entityType: 'ORDER', entityId: 'b8000000-0000-0000-0000-000000000008', previousState: 'RISK_CHECK_PASSED', newState: 'ACCEPTED',          actorId: 'OMS',           actorType: 'SYSTEM', eventTimestamp: mins(60) },
  ],
  // AMD PARTIALLY_FILLED — b9
  'b9000000-0000-0000-0000-000000000009': [
    { id: 'a9-1', entityType: 'ORDER', entityId: 'b9000000-0000-0000-0000-000000000009', previousState: undefined,            newState: 'NEW',               actorId: 'user-mike-003',    actorType: 'USER',   eventTimestamp: hrs(2) },
    { id: 'a9-2', entityType: 'ORDER', entityId: 'b9000000-0000-0000-0000-000000000009', previousState: 'NEW',               newState: 'VALIDATED',          actorId: 'OMS',              actorType: 'SYSTEM', eventTimestamp: hrs(2) },
    { id: 'a9-3', entityType: 'ORDER', entityId: 'b9000000-0000-0000-0000-000000000009', previousState: 'VALIDATED',         newState: 'RISK_CHECK_PASSED',  actorId: 'RISK_ENGINE',      actorType: 'SYSTEM', eventTimestamp: hrs(2) },
    { id: 'a9-4', entityType: 'ORDER', entityId: 'b9000000-0000-0000-0000-000000000009', previousState: 'RISK_CHECK_PASSED', newState: 'ACCEPTED',           actorId: 'OMS',              actorType: 'SYSTEM', eventTimestamp: hrs(2) },
    { id: 'a9-5', entityType: 'ORDER', entityId: 'b9000000-0000-0000-0000-000000000009', previousState: 'ACCEPTED',          newState: 'SENT_TO_EXECUTION',  actorId: 'OMS',              actorType: 'SYSTEM', eventTimestamp: hrs(2) },
    { id: 'a9-6', entityType: 'ORDER', entityId: 'b9000000-0000-0000-0000-000000000009', previousState: 'SENT_TO_EXECUTION', newState: 'PARTIALLY_FILLED',   actorId: 'EXECUTION_ENGINE', actorType: 'SYSTEM', eventTimestamp: hrs(2), metadata: 'fillType=PARTIAL executedQty=25 executedPrice=154.90' },
  ],
  // NFLX RISK_REJECTED — b0
  'b0000000-0000-0000-0000-000000000010': [
    { id: 'a0-1', entityType: 'ORDER', entityId: 'b0000000-0000-0000-0000-000000000010', previousState: undefined,   newState: 'NEW',           actorId: 'user-rob-005', actorType: 'USER',   eventTimestamp: mins(17) },
    { id: 'a0-2', entityType: 'ORDER', entityId: 'b0000000-0000-0000-0000-000000000010', previousState: 'NEW',       newState: 'VALIDATED',     actorId: 'OMS',          actorType: 'SYSTEM', eventTimestamp: mins(16) },
    { id: 'a0-3', entityType: 'ORDER', entityId: 'b0000000-0000-0000-0000-000000000010', previousState: 'VALIDATED', newState: 'RISK_REJECTED', actorId: 'RISK_ENGINE',  actorType: 'SYSTEM', eventTimestamp: mins(15), metadata: 'ruleCode=ORDER_VALUE_LIMIT notional=1886.10 limit=1000.00' },
  ],
  // AAPL NEW — b1
  'b1000000-0000-0000-0000-000000000001': [
    { id: 'a1-1', entityType: 'ORDER', entityId: 'b1000000-0000-0000-0000-000000000001', previousState: undefined, newState: 'NEW', actorId: 'user-john-001', actorType: 'USER', eventTimestamp: mins(10) },
  ],
}

export const getAuditTrail = (params?: {
  clientId?: string
  page?: number
  size?: number
}): Promise<AuditTrailResponse> =>
  http.get('/audit/trail', { params }).then((r) => r.data.data)

// TODO: SCRUM-49 — replace stub fallback with real audit-service response
export const getOrderAudit = async (orderId: string): Promise<AuditRecord[]> => {
  try {
    const res = await http.get(`/audit/orders/${orderId}`)
    return res.data.data
  } catch {
    return MOCK_AUDIT[orderId] ?? []
  }
}
