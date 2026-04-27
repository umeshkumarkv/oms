import http from './http'
import { MarketHours } from '../types/market.types'

const computeLocalMarketHours = (): MarketHours => {
  const etNow = new Date(new Date().toLocaleString('en-US', { timeZone: 'America/New_York' }))
  const day  = etNow.getDay()
  const mins = etNow.getHours() * 60 + etNow.getMinutes()
  const marketOpen = day >= 1 && day <= 5 && mins >= 9 * 60 + 30 && mins < 16 * 60
  return { marketOpen, openTime: '09:30:00', closeTime: '16:00:00', timezone: 'America/New_York' }
}

export const getMarketHours = async (): Promise<MarketHours> => {
  try {
    const res = await http.get('/market/hours')
    return res.data.data
  } catch {
    return computeLocalMarketHours()
  }
}

export const getInstruments = (): Promise<null> => Promise.resolve(null)

export const getInstrument = (_symbol: string): Promise<null> => Promise.resolve(null)
