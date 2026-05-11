import http from './http'
import { Holiday, Instrument, MarketConfig, MarketHours, TickHistoryResponse } from '../types/market.types'

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

export const getMarketConfig = async (): Promise<MarketConfig> => {
  const res = await http.get('/market/config')
  return res.data.data
}

export const getTickHistory = async (symbol: string): Promise<TickHistoryResponse> => {
  const res = await http.get(`/market/ticks/history`, { params: { symbol } })
  return res.data.data
}
