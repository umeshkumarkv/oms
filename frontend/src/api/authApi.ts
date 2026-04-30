import axios from 'axios'
export { getCurrentUser } from './apiClient'

export interface LoginResponse {
  token: string
  expiresAt: string
  role: string
  userId: string
  displayName: string
}

// Uses a plain axios instance (no auth token required for login)
const authHttp = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
})

export const login = async (username: string, password: string): Promise<LoginResponse> => {
  const res = await authHttp.post<{ data: LoginResponse }>('/auth/login', { username, password })
  return res.data.data
}
