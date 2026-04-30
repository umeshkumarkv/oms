import http from './http'
import { Client, Portfolio } from '../types/client.types'

// Seed client UUIDs — these are the actual database IDs from V2__seed.sql.
// There is no GET /api/v1/clients list endpoint; this array enables the UI to
// enumerate clients by fetching each one individually.
const SEED_CLIENT_IDS = [
  'a1000000-0000-0000-0000-000000000001',
  'a2000000-0000-0000-0000-000000000002',
  'a3000000-0000-0000-0000-000000000003',
  'a4000000-0000-0000-0000-000000000004',
  'a5000000-0000-0000-0000-000000000005',
]

export const getClient = async (clientId: string): Promise<Client> => {
  const res = await http.get(`/clients/${clientId}`)
  return res.data.data
}

export const getPortfolio = async (clientId: string): Promise<Portfolio> => {
  const res = await http.get(`/clients/${clientId}/portfolio`)
  return res.data.data
}

// Fetches all seed clients in parallel. Used to populate client selector dropdowns.
export const getClients = async (): Promise<Client[]> => {
  const results = await Promise.all(SEED_CLIENT_IDS.map(id => getClient(id)))
  return results
}
