import http from './http'
import { Client, Portfolio } from '../types/client.types'

// ── Mock data ────────────────────────────────────────────────────────────────

const MOCK_CLIENTS: Record<string, { client: Client; portfolio: Portfolio }> = {
  'a1000000-0000-0000-0000-000000000001': {
    client:    { clientId: 'a1000000-0000-0000-0000-000000000001', name: 'John Smith',     status: 'ACTIVE',   tradingEnabled: true,  kycStatus: 'VERIFIED' },
    portfolio: { cashBalance: '500000.00', reservedCash: '17500.00', buyingPower: '482500.00', holdings: [
      { symbol: 'AAPL', quantity: 100, avgPrice: '150.00', currentPrice: '178.35', marketValue: '17835.00' },
      { symbol: 'MSFT', quantity: 50,  avgPrice: '400.00', currentPrice: '415.00', marketValue: '20750.00' },
    ]},
  },
  'a2000000-0000-0000-0000-000000000002': {
    client:    { clientId: 'a2000000-0000-0000-0000-000000000002', name: 'Sarah Johnson',  status: 'ACTIVE',   tradingEnabled: true,  kycStatus: 'VERIFIED' },
    portfolio: { cashBalance: '50000.00',  reservedCash: '0.00',     buyingPower: '50000.00',  holdings: [
      { symbol: 'TSLA', quantity: 20, avgPrice: '170.00', currentPrice: '245.70', marketValue: '4914.00' },
    ]},
  },
  'a3000000-0000-0000-0000-000000000003': {
    client:    { clientId: 'a3000000-0000-0000-0000-000000000003', name: 'Mike Davis',     status: 'ACTIVE',   tradingEnabled: false, kycStatus: 'VERIFIED' },
    portfolio: { cashBalance: '200000.00', reservedCash: '0.00',     buyingPower: '200000.00', holdings: [] },
  },
  'a4000000-0000-0000-0000-000000000004': {
    client:    { clientId: 'a4000000-0000-0000-0000-000000000004', name: 'Emily Chen',     status: 'BLOCKED',  tradingEnabled: true,  kycStatus: 'VERIFIED' },
    portfolio: { cashBalance: '100000.00', reservedCash: '1600.00',  buyingPower: '98400.00',  holdings: [
      { symbol: 'GOOGL', quantity: 10, avgPrice: '160.00', currentPrice: '172.25', marketValue: '1722.50' },
    ]},
  },
  'a5000000-0000-0000-0000-000000000005': {
    client:    { clientId: 'a5000000-0000-0000-0000-000000000005', name: 'Robert Wilson',  status: 'ACTIVE',   tradingEnabled: true,  kycStatus: 'EXPIRED' },
    portfolio: { cashBalance: '75000.00',  reservedCash: '0.00',     buyingPower: '75000.00',  holdings: [] },
  },
}

// ── Exports ──────────────────────────────────────────────────────────────────

export const getClient = async (clientId: string): Promise<Client> => {
  try {
    const res = await http.get(`/clients/${clientId}`)
    return res.data.data
  } catch {
    const mock = MOCK_CLIENTS[clientId]
    if (mock) return mock.client
    throw new Error('Client not found')
  }
}

export const getPortfolio = async (clientId: string): Promise<Portfolio> => {
  try {
    const res = await http.get(`/clients/${clientId}/portfolio`)
    return res.data.data
  } catch {
    const mock = MOCK_CLIENTS[clientId]
    if (mock) return mock.portfolio
    throw new Error('Portfolio not found')
  }
}
