import http from './http'

export interface AuthUser {
  userId: string
  username: string
  role: string
}

export interface LoginResponse {
  token: string
  userId: string
  username: string
  role: string
}

export const login = async (username: string, password: string): Promise<LoginResponse> => {
  const res = await http.post('/auth/login', { username, password })
  const data: LoginResponse = res.data.data
  localStorage.setItem('token', data.token)
  return data
}

export const logout = async (): Promise<void> => {
  try {
    await http.post('/auth/logout')
  } finally {
    localStorage.removeItem('token')
  }
}

export const me = async (): Promise<AuthUser> => {
  const res = await http.get('/auth/me')
  return res.data.data
}

// Decode the JWT payload from localStorage without a full validation library.
// Returns null if no token is present or the payload cannot be parsed.
export const getCurrentUser = (): AuthUser | null => {
  const token = localStorage.getItem('token')
  if (!token) return null
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return {
      userId:   payload.userId   ?? '',
      username: payload.username ?? '',
      role:     payload.role     ?? 'TRADER',
    }
  } catch {
    return null
  }
}
