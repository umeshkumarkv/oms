export type NotificationType = 'ORDER_STATE_CHANGED' | 'EXECUTION_COMPLETED' | 'RISK_RESULT'

export interface NotificationMessage {
  id: string          // client-side generated
  type: NotificationType
  orderId: string | null
  clientId: string | null
  title: string
  message: string
  timestamp: string   // ISO 8601 UTC
}
