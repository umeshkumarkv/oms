export type OrderSide = 'BUY' | 'SELL' | 'SHORT_SELL'
export type OrderType = 'MARKET' | 'LIMIT'
export type TimeInForce = 'DAY' | 'GTD'
export type OrderStatus =
  | 'NEW'
  | 'VALIDATED'
  | 'RISK_CHECK_PASSED'
  | 'RISK_REJECTED'
  | 'ACCEPTED'
  | 'SENT_TO_EXECUTION'
  | 'PARTIALLY_FILLED'
  | 'FILLED'
  | 'CANCELLED'
  | 'REJECTED'
  | 'EXPIRED'
export type ExpiryApplied = 'DEFAULT' | 'TRADER'

export interface SubmitOrderRequest {
  clientOrderId: string
  clientId: string
  symbol: string
  side: OrderSide
  orderType: OrderType
  tif: TimeInForce
  quantity: number
  limitPrice?: string
  gtdDate?: string
  routingDestination?: string
}

export interface OrderSummary {
  id: string
  clientOrderId: string
  clientId: string
  symbol: string
  side: OrderSide
  orderType: OrderType
  timeInForce: TimeInForce
  quantity: number
  limitPrice?: string
  status: OrderStatus
  expiryDate?: string
  createdAt: string
  filledQuantity?: number
  avgFillPrice?: string
  routingDestination?: string
}

export type FillType = 'PARTIAL' | 'FULL'

export interface Execution {
  executionId: string
  orderId: string
  symbol: string
  side: OrderSide
  executedQty: number
  executedPrice: string
  fillType: FillType
  fillValue: string
  remainingQuantity: number
  executionVenue: string
  executedAt: string
}

export interface SubmitOrderResponse {
  orderId: string
  status: OrderStatus
  expiryDate?: string
  expiryApplied?: ExpiryApplied
}