import { useEffect, useState, useCallback } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { NotificationMessage } from '../types/notification.types'

const WS_URL = 'http://localhost:8088/ws'
const MAX_TOASTS = 5

export function useNotifications(token: string, userId: string, role: string) {
  const [notifications, setNotifications] = useState<NotificationMessage[]>([])
  const [wsConnected, setWsConnected] = useState(false)

  const dismiss = useCallback((id: string) => {
    setNotifications(prev => prev.filter(n => n.id !== id))
  }, [])

  useEffect(() => {
    if (!token || !userId) return

    const destination =
      role === 'TRADER'
        ? `/topic/notifications/${userId}`
        : '/topic/notifications/broadcast'

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      onConnect: () => {
        setWsConnected(true)
        client.subscribe(destination, frame => {
          try {
            const payload = JSON.parse(frame.body)
            const msg: NotificationMessage = {
              id: crypto.randomUUID(),
              type: payload.type,
              orderId: payload.orderId ?? null,
              clientId: payload.clientId ?? null,
              title: payload.title,
              message: payload.message,
              timestamp: payload.timestamp,
            }
            setNotifications(prev => {
              const updated = [...prev, msg]
              return updated.length > MAX_TOASTS ? updated.slice(updated.length - MAX_TOASTS) : updated
            })
          } catch {
            // ignore malformed frames
          }
        })
      },
      onDisconnect: () => { setWsConnected(false) },
      onStompError: () => { setWsConnected(false) },
    })

    client.activate()
    return () => {
      client.deactivate()
      setWsConnected(false)
    }
  }, [token, userId, role])

  return { notifications, wsConnected, dismiss }
}
