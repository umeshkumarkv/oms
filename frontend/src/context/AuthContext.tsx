import React, { createContext, useCallback, useContext, useEffect, useReducer } from 'react'
import { useNavigate } from 'react-router-dom'
import { setApiToken } from '../api/apiClient'
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

const initialState: AuthState = { user: null, sessionExpired: false }

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

  const login = useCallback((user: AuthUser) => {
    setApiToken(user.token)
    marketDataWs.setToken(user.token)
    dispatch({ type: 'LOGIN', payload: user })
  }, [])

  const logout = useCallback(() => {
    setApiToken(null)
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
      setApiToken(null)
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
