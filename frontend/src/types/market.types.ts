export interface Instrument {
  symbol: string
  bidPrice: string
  askPrice: string
  lastPrice: string
  open?: string
  tradable: boolean
  halted: boolean
  minQuantity: number
  maxQuantity: number
  dataTimestamp: string
  // Fields added by SCRUM-82
  change?: string
  changePct?: string
  volume?: number
  dayHigh?: string
  dayLow?: string
  previousClose?: string
  marketStatus?: string
}

export interface MarketHours {
  marketOpen: boolean
  openTime: string
  closeTime: string
  timezone: string
  nextOpenDate?: string
}

export interface Holiday {
  holidayDate: string  // YYYY-MM-DD
  holidayName: string
}

export interface MarketConfig {
  pollingIntervalMs: number
  marketOpen: boolean
  marketHours: {
    start: string
    end: string
    timezone: string
  }
  dataSource: 'WEBSOCKET' | 'POLLING'
}

export interface TickEntry {
  time: string    // LocalDateTime ISO string e.g. "2026-05-08T09:30:01"
  price: string
  bid: string
  ask: string
  volume: number
}

export interface TickHistoryResponse {
  symbol: string
  from: string
  to: string
  ticks: TickEntry[]
}

export interface MoverEntry {
  symbol: string
  percentChange: string
}

export interface MarketMovers {
  gainers: MoverEntry[]
  losers: MoverEntry[]
}

export interface IndexEntry {
  name: string
  currentValue: string
  percentChange: string
}
