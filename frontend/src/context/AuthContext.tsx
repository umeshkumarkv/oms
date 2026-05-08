import React, { createContext, useCallback, useContext, useEffect, useReducer } from 'react'
import { useNavigate } from 'react-router-dom'
import { setApiToken, setCurrentUser } from '../api/apiClient'
import { marketDataWs } from '../services/websocketService'

export interface AuthUser {
  token: string
  userId: string
  username: string
  role: string
  displayName: string
}

interface AuthState {
  user: AuthUser | null
  sessionExpired: boolean
}

type AuthAction =
  | { type: 'LOGIN'; payload: AuthUser }
  | { type: 'LOGOUT' }
  | { type: 'SESSION_EXPIRED' }
  | { type: 'CLEAR_SESSION_EXPIRED' }

const STORAGE_KEY = 'oms-auth-user'

function loadStoredUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as AuthUser) : null
  } catch {
    return null
  }
}

const initialState: AuthState = { user: loadStoredUser(), sessionExpired: false }

function authReducer(state: AuthState, action: AuthAction): AuthState {
  switch (action.type) {
    case 'LOGIN':
      return { user: action.payload, sessionExpired: false }
    case 'LOGOUT':
      return { user: null, sessionExpired: false }
    case 'SESSION_EXPIRED':
      return { user: null, sessionExpired: true }
    case 'CLEAR_SESSION_EXPIRED':
      return { ...state, sessionExpired: false }
    default:
      return state
  }
}

interface AuthContextValue {
  user: AuthUser | null
  sessionExpired: boolean
  login: (user: AuthUser) => void
  logout: () => void
  clearSessionExpired: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [state, dispatch] = useReducer(authReducer, initialState)
  const navigate = useNavigate()

  // Restore API client state from persisted session on mount
  useEffect(() => {
    const stored = loadStoredUser()
    if (stored) {
      setApiToken(stored.token)
      setCurrentUser(stored)
      marketDataWs.setToken(stored.token)
    }
  }, [])

  const login = useCallback((user: AuthUser) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(user))
    setApiToken(user.token)
    setCurrentUser(user)
    marketDataWs.setToken(user.token)
    dispatch({ type: 'LOGIN', payload: user })
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY)
    setApiToken(null)
    setCurrentUser(null)
    marketDataWs.setToken(null)
    dispatch({ type: 'LOGOUT' })
    navigate('/')
  }, [navigate])

  const clearSessionExpired = useCallback(() => {
    dispatch({ type: 'CLEAR_SESSION_EXPIRED' })
  }, [])

  // Listen for session-expired events dispatched by the Axios response interceptor
  useEffect(() => {
    const handler = () => {
      localStorage.removeItem(STORAGE_KEY)
      setApiToken(null)
      setCurrentUser(null)
      marketDataWs.setToken(null)
      dispatch({ type: 'SESSION_EXPIRED' })
      navigate('/', { state: { sessionExpired: true } })
    }
    window.addEventListener('auth:session-expired', handler)
    return () => window.removeEventListener('auth:session-expired', handler)
  }, [navigate])

  return (
    <AuthContext.Provider value={{ user: state.user, sessionExpired: state.sessionExpired, login, logout, clearSessionExpired }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
