import http from './http'
import { Client, Portfolio } from '../types/client.types'

export const getClient = (clientId: string): Promise<Client> =>
  http.get(`/clients/${clientId}`).then((r) => r.data.data)

export const getPortfolio = (clientId: string): Promise<Portfolio> =>
  http.get(`/clients/${clientId}/portfolio`).then((r) => r.data.data)