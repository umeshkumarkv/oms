import http from './http'
import { Client, Portfolio, ActiveClient } from '../types/client.types'

export const getClient = async (clientId: string): Promise<Client> => {
  const res = await http.get(`/clients/${clientId}`)
  return res.data.data
}

export const getPortfolio = async (clientId: string): Promise<Portfolio> => {
  const res = await http.get(`/clients/${clientId}/portfolio`)
  return res.data.data
}

export const getClients = async (): Promise<Client[]> => {
  const res = await http.get('/clients')
  return res.data.data
}

export const getActiveClients = async (limit = 10): Promise<ActiveClient[]> => {
  const res = await http.get(`/clients/active?limit=${limit}`)
  return res.data.data
}
