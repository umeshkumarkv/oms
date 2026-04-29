export interface Instrument {
  symbol: string
  bidPrice: string
  askPrice: string
  lastPrice: string
  tradable: boolean
  halted: boolean
  minQuantity: number
  maxQuantity: number
  dataTimestamp: string
}

export interface MarketHours {
  marketOpen: boolean
  openTime: string
  closeTime: string
  timezone: string
}

export interface Holiday {
  holidayDate: string  // YYYY-MM-DD
  holidayName: string
}

export interface MarketTickEvent {
  type: 'MARKET_DATA_TICK'
  symbol: string
  lastPrice: string
  bidPrice: string
  askPrice: string
  change: string
  changePct: string
  volume: number
  timestamp: string
}

export interface MarketStatusEvent {
  type: 'MARKET_OPEN' | 'MARKET_CLOSE'
  marketStatus: 'OPEN' | 'CLOSED'
  timestamp: string
}

export type WebSocketEvent = MarketTickEvent | MarketStatusEvent
