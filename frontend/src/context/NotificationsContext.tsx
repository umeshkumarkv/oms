import React, { createContext, useContext } from 'react'
import { useAuth } from './AuthContext'
import { useNotifications } from '../hooks/useNotifications'
import { NotificationMessage } from '../types/notification.types'

interface NotificationsContextValue {
  notifications: NotificationMessage[]
  wsConnected: boolean
  dismiss: (id: string) => void
}

const NotificationsContext = createContext<NotificationsContextValue | null>(null)

export const NotificationsProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user } = useAuth()
  const { notifications, wsConnected, dismiss } = useNotifications(
    user?.token ?? '',
    user?.userId ?? '',
    user?.role ?? ''
  )

  return (
    <NotificationsContext.Provider value={{ notifications, wsConnected, dismiss }}>
      {children}
    </NotificationsContext.Provider>
  )
}

export const useNotificationsContext = (): NotificationsContextValue => {
  const ctx = useContext(NotificationsContext)
  if (!ctx) throw new Error('useNotificationsContext must be used within NotificationsProvider')
  return ctx
}
