import http from './http'
import { Holiday, Instrument, MarketHours } from '../types/market.types'

// ── Mock data ────────────────────────────────────────────────────────────────

const MOCK_INSTRUMENTS: Instrument[] = [
  { symbol: 'AAPL',  bidPrice: '178.20', askPrice: '178.50', lastPrice: '178.35', tradable: true,  halted: false, minQuantity: 1, maxQuantity: 10000, dataTimestamp: new Date().toISOString() },
  { symbol: 'MSFT',  bidPrice: '414.80', askPrice: '415.20', lastPrice: '415.00', tradable: true,  halted: false, minQuantity: 1, maxQuantity: 10000, dataTimestamp: new Date().toISOString() },
  { symbol: 'GOOGL', bidPrice: '172.10', askPrice: '172.40', lastPrice: '172.25', tradable: true,  halted: false, minQuantity: 1, maxQuantity: 10000, dataTimestamp: new Date().toISOString() },
  { symbol: 'TSLA',  bidPrice: '245.50', askPrice: '245.90', lastPrice: '245.70', tradable: true,  halted: false, minQuantity: 1, maxQuantity: 10000, dataTimestamp: new Date().toISOString() },
  { symbol: 'NVDA',  bidPrice: '869.80', askPrice: '870.20', lastPrice: '870.00', tradable: true,  halted: false, minQuantity: 1, maxQuantity: 5000,  dataTimestamp: new Date().toISOString() },
  { symbol: 'META',  bidPrice: '504.60', askPrice: '505.10', lastPrice: '504.85', tradable: true,  halted: false, minQuantity: 1, maxQuantity: 10000, dataTimestamp: new Date().toISOString() },
  { symbol: 'AMZN',  bidPrice: '182.30', askPrice: '182.60', lastPrice: '182.45', tradable: true,  halted: false, minQuantity: 1, maxQuantity: 10000, dataTimestamp: new Date().toISOString() },
  { symbol: 'JPM',   bidPrice: '194.70', askPrice: '195.10', lastPrice: '194.90', tradable: true,  halted: false, minQuantity: 1, maxQuantity: 10000, dataTimestamp: new Date().toISOString() },
  { symbol: 'BAC',   bidPrice: '37.90',  askPrice: '38.10',  lastPrice: '38.00',  tradable: true,  halted: false, minQuantity: 1, maxQuantity: 50000, dataTimestamp: new Date().toISOString() },
  { symbol: 'GS',    bidPrice: '447.20', askPrice: '447.80', lastPrice: '447.50', tradable: true,  halted: false, minQuantity: 1, maxQuantity: 5000,  dataTimestamp: new Date().toISOString() },
  { symbol: 'NFLX',  bidPrice: '628.40', askPrice: '629.00', lastPrice: '628.70', tradable: true,  halted: false, minQuantity: 1, maxQuantity: 5000,  dataTimestamp: new Date().toISOString() },
  { symbol: 'AMD',   bidPrice: '155.10', askPrice: '155.40', lastPrice: '155.25', tradable: true,  halted: false, minQuantity: 1, maxQuantity: 20000, dataTimestamp: new Date().toISOString() },
  { symbol: 'INTC',  bidPrice: '30.80',  askPrice: '31.00',  lastPrice: '30.90',  tradable: true,  halted: false, minQuantity: 1, maxQuantity: 50000, dataTimestamp: new Date().toISOString() },
  { symbol: 'PYPL',  bidPrice: '62.30',  askPrice: '62.60',  lastPrice: '62.45',  tradable: true,  halted: false, minQuantity: 1, maxQuantity: 20000, dataTimestamp: new Date().toISOString() },
  { symbol: 'HALT',  bidPrice: '45.00',  askPrice: '45.20',  lastPrice: '45.10',  tradable: true,  halted: true,  minQuantity: 1, maxQuantity: 10000, dataTimestamp: new Date().toISOString() },
]

const computeLocalMarketHours = (): MarketHours => {
  const etNow = new Date(new Date().toLocaleString('en-US', { timeZone: 'America/New_York' }))
  const day  = etNow.getDay()
  const mins = etNow.getHours() * 60 + etNow.getMinutes()
  const marketOpen = day >= 1 && day <= 5 && mins >= 9 * 60 + 30 && mins < 16 * 60
  return { marketOpen, openTime: '09:30:00', closeTime: '16:00:00', timezone: 'America/New_York' }
}

// ── Exports ──────────────────────────────────────────────────────────────────

export const getMarketHours = async (): Promise<MarketHours> => {
  try {
    const res = await http.get('/market/hours')
    return res.data.data
  } catch {
    return computeLocalMarketHours()
  }
}

export const getInstruments = async (): Promise<Instrument[]> => {
  try {
    const res = await http.get('/market/instruments')
    return res.data.data
  } catch {
    return MOCK_INSTRUMENTS
  }
}

export const getInstrument = async (symbol: string): Promise<Instrument | null> => {
  try {
    const res = await http.get(`/market/${symbol}`)
    return res.data.data
  } catch {
    return MOCK_INSTRUMENTS.find((i) => i.symbol === symbol) ?? null
  }
}

// 2026 US equity market holidays
const MOCK_HOLIDAYS: Holiday[] = [
  { holidayDate: '2026-01-01', holidayName: "New Year's Day" },
  { holidayDate: '2026-01-19', holidayName: 'Martin Luther King Jr. Day' },
  { holidayDate: '2026-02-16', holidayName: "Presidents' Day" },
  { holidayDate: '2026-04-03', holidayName: 'Good Friday' },
  { holidayDate: '2026-05-25', holidayName: 'Memorial Day' },
  { holidayDate: '2026-07-03', holidayName: 'Independence Day (observed)' },
  { holidayDate: '2026-09-07', holidayName: 'Labor Day' },
  { holidayDate: '2026-11-26', holidayName: 'Thanksgiving Day' },
  { holidayDate: '2026-11-27', holidayName: 'Day after Thanksgiving' },
  { holidayDate: '2026-12-25', holidayName: 'Christmas Day' },
]

export const getHolidays = async (): Promise<Holiday[]> => {
  try {
    const res = await http.get('/market/holidays')
    return res.data.data
  } catch {
    return MOCK_HOLIDAYS
  }
}
