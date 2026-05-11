import { useEffect, useRef, useState } from 'react'
import { marketDataWs } from '../services/websocketService'

export interface TickData {
  symbol:    string
  price:     number
  bid:       number
  ask:       number
  timestamp: string
}

interface UseMarketSocketResult {
  ticks:  Record<string, TickData>
  wsLive: boolean
}

/**
 * Connects to the market-data WebSocket and manages per-symbol subscriptions.
 *
 * - Subscribes to all symbols in `symbols` on mount / when the list changes.
 * - Unsubscribes from symbols removed from the list.
 * - Returns `ticks` — a map of symbol → latest TickData.
 * - Returns `wsLive` — true while the WS connection is authenticated.
 *
 * Symbol subscriptions survive re-renders; the effect only fires when the
 * joined symbol string actually changes (avoids thrashing on every render).
 */
export function useMarketSocket(symbols: string[]): UseMarketSocketResult {
  const [ticks,  setTicks]  = useState<Record<string, TickData>>({})
  const [wsLive, setWsLive] = useState(false)

  // Track which symbols we have currently subscribed
  const subscribedRef = useRef<string[]>([])

  // Manage subscriptions whenever the symbol list changes
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    const prev = new Set(subscribedRef.current)
    const next = new Set(symbols)

    // Unsubscribe symbols no longer in the list
    prev.forEach(s => { if (!next.has(s)) marketDataWs.unsubscribe(s) })
    // Subscribe new symbols
    next.forEach(s => { if (!prev.has(s)) marketDataWs.subscribe(s) })

    subscribedRef.current = [...next]
  }, [symbols.join(',')])  // eslint-disable-line react-hooks/exhaustive-deps

  // Wire up tick + connection handlers once on mount
  useEffect(() => {
    marketDataWs.connect()

    const offTick = marketDataWs.onTick(tick => {
      setTicks(prev => ({
        ...prev,
        [tick.symbol]: {
          symbol:    tick.symbol,
          price:     Number(tick.lastPrice),
          bid:       Number(tick.bidPrice),
          ask:       Number(tick.askPrice),
          timestamp: tick.dataTimestamp,
        },
      }))
    })

    const offConn = marketDataWs.onConnectionChange(setWsLive)

    return () => {
      offTick()
      offConn()
      // Unsubscribe all when hook unmounts
      subscribedRef.current.forEach(s => marketDataWs.unsubscribe(s))
      subscribedRef.current = []
    }
  }, [])

  return { ticks, wsLive }
}
