import http from './http'
import { AuditRecord } from '../types/audit.types'

export interface AuditTrailResponse {
  records: AuditRecord[]
  totalElements: number
}

export const getAuditTrail = (params?: {
  clientId?: string
  page?: number
  size?: number
}): Promise<AuditTrailResponse> =>
  http.get('/audit/trail', { params }).then((r) => r.data.data)

export const getOrderAudit = (orderId: string): Promise<AuditRecord[]> =>
  http.get(`/audit/orders/${orderId}`).then((r) => r.data.data)