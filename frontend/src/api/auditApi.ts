import http from './http'
import { AuditRecord } from '../types/audit.types'

export interface AuditTrailResponse {
  records: AuditRecord[]
  totalElements: number
}

export const getAuditTrail = async (params?: {
  clientId?: string
  page?: number
  size?: number
}): Promise<AuditTrailResponse> => {
  const res = await http.get('/audit/trail', { params })
  return res.data.data
}

export const getOrderAudit = async (orderId: string): Promise<AuditRecord[]> => {
  const res = await http.get(`/audit/orders/${orderId}`)
  return res.data.data
}
