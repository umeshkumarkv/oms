import http from './http'
import { Holiday, Instrument, MarketHours } from '../types/market.types'

export const getMarketHours = async (): Promise<MarketHours> => {
  const res = await http.get('/market/hours')
  return res.data.data
}

export const getInstruments = async (): Promise<Instrument[]> => {
  const res = await http.get('/market/instruments')
  return res.data.data
}

export const getInstrument = async (symbol: string): Promise<Instrument | null> => {
  const res = await http.get(`/market/${symbol}`)
  return res.data.data
}

export const getHolidays = async (): Promise<Holiday[]> => {
  const res = await http.get('/market/holidays')
  return res.data.data
}
