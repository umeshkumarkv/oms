export type ClientStatus = 'ACTIVE' | 'BLOCKED'
export type KycStatus = 'VERIFIED' | 'PENDING' | 'EXPIRED'

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
  reservedCash: string
  buyingPower: string
  holdings: Holding[]
}