import http from './http'

export interface WatchlistEntry {
  symbol: string
  addedAt: string
}

export const getWatchlist = async (): Promise<WatchlistEntry[]> => {
  const res = await http.get('/watchlist')
  return res.data.data
}

export const addToWatchlist = async (symbol: string): Promise<WatchlistEntry> => {
  const res = await http.post(`/watchlist/${symbol}`)
  return res.data.data
}

export const removeFromWatchlist = async (symbol: string): Promise<void> => {
  await http.delete(`/watchlist/${symbol}`)
}
