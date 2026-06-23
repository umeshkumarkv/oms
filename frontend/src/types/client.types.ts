export type ClientStatus = 'ACTIVE' | 'BLOCKED'
export type KycStatus = 'VERIFIED' | 'PENDING' | 'EXPIRED'
export type OrderSide = 'BUY' | 'SELL'
export type FillType = 'PARTIAL' | 'FULL'

export interface Client {
  clientId: string
  name: string
  status: ClientStatus
  tradingEnabled: boolean
  kycStatus: KycStatus
}

export interface Holding {
  symbol: string
  quantity: number
  avgPrice: string
  currentPrice: string
  marketValue: string
}

export interface Portfolio {
  cashBalance: string
  holdings: Holding[]
}

export interface ActiveClient {
  clientId:         string
  clientName:       string
  turnoverToday:    string
  availableCash:    string
  cashLimitWarning: boolean
}

// Parsed from AuditRecord.metadata for entityType=EXECUTION
export interface FillActivity {
  auditId: string
  orderId: string
  symbol: string
  side: OrderSide
  filledQty: number
  fillPrice: string
  fillValue: string
  fillType: FillType
  timestamp: string
}