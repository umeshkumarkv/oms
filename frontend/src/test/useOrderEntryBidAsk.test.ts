import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useOrderEntryBidAsk } from '../hooks/useOrderEntryBidAsk'
import type { TickData } from '../hooks/useMarketSocket'

// ── Mock useMarketSocket ───────────────────────────────────────────────────────
const mockUseMarketSocket = vi.fn()
vi.mock('../hooks/useMarketSocket', () => ({
  useMarketSocket: (...args: unknown[]) => mockUseMarketSocket(...args),
}))

// ── Fixtures ──────────────────────────────────────────────────────────────────
const NO_TICKS = { ticks: {} as Record<string, TickData>, wsLive: true }

const withTick = (
  symbol: string,
  bid: number,
  ask: number,
  timestamp: string,
): { ticks: Record<string, TickData>; wsLive: boolean } => ({
  ticks: {
    [symbol]: {
      symbol,
      price:         (bid + ask) / 2,
      bid,
      ask,
      timestamp,
      previousClose: null,
      dayHigh:       null,
      dayLow:        null,
      volume:        null,
      change:        null,
      changePct:     null,
    },
  },
  wsLive: true,
})

// ── Tests ──────────────────────────────────────────────────────────────────────
describe('useOrderEntryBidAsk', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseMarketSocket.mockReturnValue(NO_TICKS)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('shouldReturnNullBidAskAndIsStaleFalseWhenNoSymbolProvided', () => {
    const { result } = renderHook(() => useOrderEntryBidAsk(''))
    expect(result.current.bid).toBeNull()
    expect(result.current.ask).toBeNull()
    expect(result.current.isStale).toBe(false)
  })

  it('shouldPassEmptyArrayToUseMarketSocketWhenSymbolIsEmpty', () => {
    renderHook(() => useOrderEntryBidAsk(''))
    expect(mockUseMarketSocket).toHaveBeenCalledWith([])
  })

  it('shouldReturnNullBidAskBeforeFirstTickArrives', () => {
    mockUseMarketSocket.mockReturnValue(NO_TICKS)
    const { result } = renderHook(() => useOrderEntryBidAsk('AAPL'))
    expect(result.current.bid).toBeNull()
    expect(result.current.ask).toBeNull()
  })

  it('shouldReturnFormattedBidAskWhenTickReceived', () => {
    mockUseMarketSocket.mockReturnValue(
      withTick('AAPL', 174.9, 175.1, '2026-05-15T10:00:01Z'),
    )
    const { result } = renderHook(() => useOrderEntryBidAsk('AAPL'))
    expect(result.current.bid).toBe('174.90')
    expect(result.current.ask).toBe('175.10')
  })

  it('shouldSetIsStaleFalseWhenTickArrivesWhileStale', () => {
    vi.useFakeTimers()
    mockUseMarketSocket.mockReturnValue(NO_TICKS)
    const { result, rerender } = renderHook(
      ({ symbol }) => useOrderEntryBidAsk(symbol),
      { initialProps: { symbol: 'AAPL' } },
    )
    act(() => { vi.advanceTimersByTime(6001) })
    expect(result.current.isStale).toBe(true)

    mockUseMarketSocket.mockReturnValue(
      withTick('AAPL', 174.9, 175.1, '2026-05-15T10:00:05Z'),
    )
    act(() => { rerender({ symbol: 'AAPL' }) })
    expect(result.current.isStale).toBe(false)
  })

  it('shouldSetIsStaleAfter5sWithNoTick', () => {
    vi.useFakeTimers()
    mockUseMarketSocket.mockReturnValue(NO_TICKS)
    const { result } = renderHook(() => useOrderEntryBidAsk('AAPL'))
    expect(result.current.isStale).toBe(false)
    act(() => { vi.advanceTimersByTime(6001) })
    expect(result.current.isStale).toBe(true)
  })

  it('shouldClearStaleWhenNewTickArrivesAfterStale', () => {
    vi.useFakeTimers()
    mockUseMarketSocket.mockReturnValue(NO_TICKS)
    const { result, rerender } = renderHook(
      ({ symbol }) => useOrderEntryBidAsk(symbol),
      { initialProps: { symbol: 'AAPL' } },
    )
    act(() => { vi.advanceTimersByTime(6001) })
    expect(result.current.isStale).toBe(true)

    mockUseMarketSocket.mockReturnValue(
      withTick('AAPL', 174.9, 175.1, '2026-05-15T10:00:10Z'),
    )
    act(() => { rerender({ symbol: 'AAPL' }) })
    expect(result.current.isStale).toBe(false)
  })

  it('shouldSetIsStaleImmediatelyWhenWsGoesDown', () => {
    mockUseMarketSocket.mockReturnValue({ ticks: {}, wsLive: true })
    const { result, rerender } = renderHook(
      ({ symbol }) => useOrderEntryBidAsk(symbol),
      { initialProps: { symbol: 'AAPL' } },
    )
    expect(result.current.isStale).toBe(false)

    mockUseMarketSocket.mockReturnValue({ ticks: {}, wsLive: false })
    act(() => { rerender({ symbol: 'AAPL' }) })
    expect(result.current.isStale).toBe(true)
  })

  it('shouldResetStaleWhenSymbolChanges', () => {
    vi.useFakeTimers()
    mockUseMarketSocket.mockReturnValue(NO_TICKS)
    const { result, rerender } = renderHook(
      ({ symbol }) => useOrderEntryBidAsk(symbol),
      { initialProps: { symbol: 'AAPL' } },
    )
    act(() => { vi.advanceTimersByTime(6001) })
    expect(result.current.isStale).toBe(true)

    act(() => { rerender({ symbol: 'MSFT' }) })
    expect(result.current.isStale).toBe(false)
  })

  it('shouldCallUseMarketSocketWithNewSymbolWhenSymbolChanges', () => {
    mockUseMarketSocket.mockReturnValue(NO_TICKS)
    const { rerender } = renderHook(
      ({ symbol }) => useOrderEntryBidAsk(symbol),
      { initialProps: { symbol: 'AAPL' } },
    )
    expect(mockUseMarketSocket).toHaveBeenLastCalledWith(['AAPL'])

    rerender({ symbol: 'MSFT' })
    expect(mockUseMarketSocket).toHaveBeenLastCalledWith(['MSFT'])
  })

  it('shouldNotSetIsStaleWhenSymbolClearedToEmpty', () => {
    vi.useFakeTimers()
    mockUseMarketSocket.mockReturnValue(NO_TICKS)
    const { result, rerender } = renderHook(
      ({ symbol }) => useOrderEntryBidAsk(symbol),
      { initialProps: { symbol: 'AAPL' } },
    )
    act(() => { rerender({ symbol: '' }) })
    act(() => { vi.advanceTimersByTime(6001) })
    expect(result.current.isStale).toBe(false)
  })

  // ── LTP (Last Traded Price) — SCRUM-127 ───────────────────────────────────────

  it('shouldReturnNullLtpBeforeFirstTickArrives', () => {
    mockUseMarketSocket.mockReturnValue(NO_TICKS)
    const { result } = renderHook(() => useOrderEntryBidAsk('AAPL'))
    expect(result.current.ltp).toBeNull()
  })

  it('shouldReturnFormattedLtpWhenTickReceived', () => {
    // withTick sets price = (bid + ask) / 2 = (174.9 + 175.1) / 2 = 175.0
    mockUseMarketSocket.mockReturnValue(
      withTick('AAPL', 174.9, 175.1, '2026-05-15T10:00:01Z'),
    )
    const { result } = renderHook(() => useOrderEntryBidAsk('AAPL'))
    expect(result.current.ltp).toBe('175.00')
  })

  it('shouldUpdateLtpWhenNewTickArrives', () => {
    mockUseMarketSocket.mockReturnValue(NO_TICKS)
    const { result, rerender } = renderHook(
      ({ symbol }) => useOrderEntryBidAsk(symbol),
      { initialProps: { symbol: 'AAPL' } },
    )
    expect(result.current.ltp).toBeNull()

    // withTick price = (200.0 + 202.5) / 2 = 201.25
    mockUseMarketSocket.mockReturnValue(
      withTick('AAPL', 200.0, 202.5, '2026-05-15T10:00:05Z'),
    )
    act(() => { rerender({ symbol: 'AAPL' }) })
    expect(result.current.ltp).toBe('201.25')
  })

  it('shouldReturnNullLtpWhenSymbolIsEmpty', () => {
    mockUseMarketSocket.mockReturnValue(NO_TICKS)
    const { result } = renderHook(() => useOrderEntryBidAsk(''))
    expect(result.current.ltp).toBeNull()
  })
})
