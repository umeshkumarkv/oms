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
  nextOpenDate?: string
}

export interface Holiday {
  holidayDate: string  // YYYY-MM-DD
  holidayName: string
}
