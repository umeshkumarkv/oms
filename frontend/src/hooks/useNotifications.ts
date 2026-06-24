import { useEffect, useState, useCallback } from 'react'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { NotificationMessage } from '../types/notification.types'

const SSE_URL = '/api/v1/notifications/stream'
const MAX_TOASTS = 5

export function useNotifications(token: string, userId: string, _role: string) {
  const [notifications, setNotifications] = useState<NotificationMessage[]>([])
  const [sseConnected, setSseConnected] = useState(false)

  const dismiss = useCallback((id: string) => {
    setNotifications(prev => prev.filter(n => n.id !== id))
  }, [])

  useEffect(() => {
    if (!token || !userId) return

    const controller = new AbortController()

    fetchEventSource(SSE_URL, {
      headers: { Authorization: `Bearer ${token}` },
      signal: controller.signal,
      onopen: async (response) => {
        if (response.ok) setSseConnected(true)
      },
      onmessage: (event) => {
        if (!event.data || event.data === ':keepalive') return
        try {
          const payload = JSON.parse(event.data)
          if (!payload.title && !payload.message) return
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
            return updated.length > MAX_TOASTS
              ? updated.slice(updated.length - MAX_TOASTS)
              : updated
          })
        } catch {
          // ignore malformed frames
        }
      },
      onclose: () => { setSseConnected(false) },
      onerror: () => { setSseConnected(false) },
    })

    return () => {
      controller.abort()
      setSseConnected(false)
    }
  }, [token, userId])

  return { notifications, sseConnected, dismiss }
}
