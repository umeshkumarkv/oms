export interface PriceTick {
  type: 'MARKET_DATA_TICK'
  symbol: string
  bidPrice: string
  askPrice: string
  lastPrice: string
  previousClose: string
  dataTimestamp: string
  // Fields added by SCRUM-82
  change?: string
  changePct?: string
  volume?: number
  dayHigh?: string
  dayLow?: string
}

export interface MarketStatusEvent {
  type: 'MARKET_OPEN' | 'MARKET_CLOSE'
  timestamp: string
}

type TickHandler = (tick: PriceTick) => void
type MarketStatusHandler = (event: MarketStatusEvent) => void
type ConnectionHandler = (connected: boolean) => void

class MarketDataWebSocketService {
  private ws: WebSocket | null = null
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private tickHandlers: TickHandler[] = []
  private marketStatusHandlers: MarketStatusHandler[] = []
  private connectionHandlers: ConnectionHandler[] = []
  private connected = false
  private authToken: string | null = null
  // Symbols to subscribe to once the connection is authenticated
  private pendingSubscriptions: Set<string> = new Set()

  setToken(token: string | null): void {
    this.authToken = token
  }

  connect(): void {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return
    }
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${protocol}://${window.location.host}/ws/v1`
    this.ws = new WebSocket(url)

    this.ws.onopen = () => {
      if (this.authToken) {
        this.ws!.send(JSON.stringify({ type: 'AUTH', token: `Bearer ${this.authToken}` }))
      }
    }

    this.ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data as string)
        if (msg.type === 'AUTH_OK') {
          this.connected = true
          this.connectionHandlers.forEach(h => h(true))
          // Flush buffered subscriptions that arrived before AUTH_OK
          this.pendingSubscriptions.forEach(symbol => {
            this.ws!.send(JSON.stringify({ action: 'subscribe', symbol }))
          })
          this.pendingSubscriptions.clear()
        } else if (msg.type === 'MARKET_DATA_TICK') {
          this.tickHandlers.forEach(h => h(msg as PriceTick))
        } else if (msg.type === 'MARKET_OPEN' || msg.type === 'MARKET_CLOSE') {
          this.marketStatusHandlers.forEach(h => h(msg as MarketStatusEvent))
        } else if (msg.type === 'PING') {
          this.ws!.send(JSON.stringify({ type: 'PONG' }))
        }
      } catch {
        // ignore malformed frames
      }
    }

    this.ws.onclose = () => {
      if (this.connected) {
        this.connected = false
        this.connectionHandlers.forEach(h => h(false))
      }
      this.reconnectTimer = setTimeout(() => this.connect(), 3000)
    }

    this.ws.onerror = () => {
      this.ws?.close()
    }
  }

  disconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.ws?.close()
    this.ws = null
    this.connected = false
  }

  isConnected(): boolean {
    return this.connected
  }

  /**
   * Subscribe to tick updates for a symbol.
   * If the connection is not yet authenticated the request is queued and
   * flushed automatically once AUTH_OK is received.
   */
  subscribe(symbol: string): void {
    if (this.connected && this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ action: 'subscribe', symbol }))
    } else {
      this.pendingSubscriptions.add(symbol)
    }
  }

  /**
   * Unsubscribe from tick updates for a symbol.
   * Also removes the symbol from the pending queue if not yet sent.
   */
  unsubscribe(symbol: string): void {
    this.pendingSubscriptions.delete(symbol)
    if (this.connected && this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ action: 'unsubscribe', symbol }))
    }
  }

  onTick(handler: TickHandler): () => void {
    this.tickHandlers.push(handler)
    return () => { this.tickHandlers = this.tickHandlers.filter(h => h !== handler) }
  }

  onMarketStatus(handler: MarketStatusHandler): () => void {
    this.marketStatusHandlers.push(handler)
    return () => { this.marketStatusHandlers = this.marketStatusHandlers.filter(h => h !== handler) }
  }

  onConnectionChange(handler: ConnectionHandler): () => void {
    this.connectionHandlers.push(handler)
    return () => { this.connectionHandlers = this.connectionHandlers.filter(h => h !== handler) }
  }
}

export const marketDataWs = new MarketDataWebSocketService()
