import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useMarketSocket } from '../hooks/useMarketSocket'
import { marketDataWs } from '../services/websocketService'

vi.mock('../services/websocketService', () => ({
  marketDataWs: {
    connect:            vi.fn(),
    isConnected:        vi.fn(),
    onTick:             vi.fn(),
    onConnectionChange: vi.fn(),
    subscribe:          vi.fn(),
    unsubscribe:        vi.fn(),
  },
}))

describe('useMarketSocket', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(marketDataWs.onTick).mockReturnValue(() => {})
    vi.mocked(marketDataWs.onConnectionChange).mockReturnValue(() => {})
    vi.mocked(marketDataWs.isConnected).mockReturnValue(false)
  })

  it('shouldInitializeWsLiveToTrueWhenSingletonIsAlreadyAuthenticated', () => {
    vi.mocked(marketDataWs.isConnected).mockReturnValue(true)
    const { result } = renderHook(() => useMarketSocket([]))
    expect(result.current.wsLive).toBe(true)
  })

  it('shouldInitializeWsLiveToFalseWhenSingletonIsNotYetAuthenticated', () => {
    vi.mocked(marketDataWs.isConnected).mockReturnValue(false)
    const { result } = renderHook(() => useMarketSocket([]))
    expect(result.current.wsLive).toBe(false)
  })

  it('shouldTransitionWsLiveToTrueWhenOnConnectionChangeFiresTrue', () => {
    vi.mocked(marketDataWs.isConnected).mockReturnValue(false)
    let capturedHandler: ((connected: boolean) => void) | undefined
    vi.mocked(marketDataWs.onConnectionChange).mockImplementation((handler) => {
      capturedHandler = handler
      return () => {}
    })

    const { result } = renderHook(() => useMarketSocket([]))
    expect(result.current.wsLive).toBe(false)

    act(() => { capturedHandler!(true) })
    expect(result.current.wsLive).toBe(true)
  })

  it('shouldTransitionWsLiveToFalseWhenOnConnectionChangeFiresFalse', () => {
    vi.mocked(marketDataWs.isConnected).mockReturnValue(true)
    let capturedHandler: ((connected: boolean) => void) | undefined
    vi.mocked(marketDataWs.onConnectionChange).mockImplementation((handler) => {
      capturedHandler = handler
      return () => {}
    })

    const { result } = renderHook(() => useMarketSocket([]))
    expect(result.current.wsLive).toBe(true)

    act(() => { capturedHandler!(false) })
    expect(result.current.wsLive).toBe(false)
  })

  it('shouldUnsubscribeAllSymbolsOnUnmount', () => {
    vi.mocked(marketDataWs.isConnected).mockReturnValue(true)
    const { unmount } = renderHook(() => useMarketSocket(['AAPL', 'MSFT']))
    unmount()
    expect(vi.mocked(marketDataWs.unsubscribe)).toHaveBeenCalledWith('AAPL')
    expect(vi.mocked(marketDataWs.unsubscribe)).toHaveBeenCalledWith('MSFT')
  })
})
