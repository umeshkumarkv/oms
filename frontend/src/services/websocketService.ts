import { WebSocketEvent } from '../types/market.types'

const WS_URL = 'ws://localhost:8080/ws/v1'

type EventHandler = (event: WebSocketEvent) => void
type VoidHandler = () => void

class WebSocketService {
  private ws: WebSocket | null = null
  private messageHandlers: Set<EventHandler> = new Set()
  private connectHandlers: Set<VoidHandler> = new Set()
  private disconnectHandlers: Set<VoidHandler> = new Set()
  private reconnectDelay = 1000
  private reconnecting = false
  private shouldReconnect = true

  connect(): void {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return
    }
    this.shouldReconnect = true
    this.openConnection()
  }

  disconnect(): void {
    this.shouldReconnect = false
    this.reconnecting = false
    this.reconnectDelay = 1000
    this.ws?.close()
    this.ws = null
  }

  onMessage(handler: EventHandler): () => void {
    this.messageHandlers.add(handler)
    return () => this.messageHandlers.delete(handler)
  }

  onConnect(handler: VoidHandler): () => void {
    this.connectHandlers.add(handler)
    return () => this.connectHandlers.delete(handler)
  }

  onDisconnect(handler: VoidHandler): () => void {
    this.disconnectHandlers.add(handler)
    return () => this.disconnectHandlers.delete(handler)
  }

  private openConnection(): void {
    try {
      this.ws = new WebSocket(WS_URL)
    } catch {
      this.scheduleReconnect()
      return
    }

    this.ws.onopen = () => {
      const token = localStorage.getItem('token')
      if (token) {
        this.ws!.send(JSON.stringify({ type: 'AUTH', token: `Bearer ${token}` }))
      }
      this.reconnectDelay = 1000
      this.reconnecting = false
      this.connectHandlers.forEach((fn) => fn())
    }

    this.ws.onmessage = (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data as string) as WebSocketEvent
        // AUTH_OK is not a WebSocketEvent — ignore it silently
        if (!('type' in data) || data.type === ('AUTH_OK' as string)) return
        this.messageHandlers.forEach((fn) => fn(data))
      } catch {
        // malformed message — ignore
      }
    }

    this.ws.onclose = () => {
      this.disconnectHandlers.forEach((fn) => fn())
      if (this.shouldReconnect) {
        this.scheduleReconnect()
      }
    }

    this.ws.onerror = () => {
      // onclose fires after onerror — reconnect handled there
      this.ws?.close()
    }
  }

  private scheduleReconnect(): void {
    if (this.reconnecting || !this.shouldReconnect) return
    this.reconnecting = true
    const delay = this.reconnectDelay
    this.reconnectDelay = Math.min(this.reconnectDelay * 2, 30_000)
    setTimeout(() => {
      this.reconnecting = false
      if (this.shouldReconnect) {
        this.openConnection()
      }
    }, delay)
  }
}

export const websocketService = new WebSocketService()
