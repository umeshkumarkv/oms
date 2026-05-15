import { useEffect, useRef, useState } from 'react'
import { useMarketSocket } from './useMarketSocket'

const STALE_THRESHOLD_MS = 5_000

interface OrderEntryBidAsk {
  bid:     string | null
  ask:     string | null
  isStale: boolean
}

/**
 * Provides real-time Bid/Ask prices for a single symbol in the Order Entry window.
 *
 * Subscribes to MARKET_DATA_TICK events via the existing useMarketSocket hook.
 * Detects stale data: sets isStale=true when the WebSocket connection drops or when
 * no tick arrives for the subscribed symbol within STALE_THRESHOLD_MS (5 seconds).
 * Resets stale state automatically when a new tick arrives or the symbol changes.
 * Never opens a second WebSocket connection — reuses the marketDataWs singleton.
 */
export function useOrderEntryBidAsk(symbol: string): OrderEntryBidAsk {
  const { ticks, wsLive } = useMarketSocket(symbol ? [symbol] : [])
  const [isStale, setIsStale] = useState(false)
  const lastTickAtRef         = useRef<number | null>(null)

  const tick = symbol ? ticks[symbol] : undefined

  // Effect 1 — reset stale and record tick timestamp on each new tick
  useEffect(() => {
    if (!tick) return
    lastTickAtRef.current = Date.now()
    setIsStale(false)
  }, [tick?.timestamp]) // eslint-disable-line react-hooks/exhaustive-deps

  // Effect 2 — stale countdown: start 5-second window on symbol change; check every second
  useEffect(() => {
    if (!symbol) {
      setIsStale(false)
      return
    }
    lastTickAtRef.current = Date.now() // begin window from subscription time
    setIsStale(false)

    const interval = setInterval(() => {
      if (
        lastTickAtRef.current !== null &&
        Date.now() - lastTickAtRef.current > STALE_THRESHOLD_MS
      ) {
        setIsStale(true)
      }
    }, 1_000)

    return () => clearInterval(interval)
  }, [symbol])

  // Effect 3 — immediate stale on WebSocket disconnection
  useEffect(() => {
    if (!wsLive && symbol) setIsStale(true)
  }, [wsLive, symbol])

  return {
    bid:     tick ? tick.bid.toFixed(2) : null,
    ask:     tick ? tick.ask.toFixed(2) : null,
    isStale: Boolean(symbol) && isStale,
  }
}