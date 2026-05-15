import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useIdleTimer } from '../hooks/useIdleTimer'

describe('useIdleTimer', () => {
  beforeEach(() => { vi.useFakeTimers() })
  afterEach(() => { vi.useRealTimers() })

  it('shouldStartCountdownAt60OnMount', () => {
    const { result } = renderHook(() => useIdleTimer(60))
    expect(result.current.secondsRemaining).toBe(60)
    expect(result.current.isExpired).toBe(false)
  })

  it('shouldDecrementSecondsEverySecond', () => {
    const { result } = renderHook(() => useIdleTimer(60))
    act(() => { vi.advanceTimersByTime(3000) })
    expect(result.current.secondsRemaining).toBe(57)
    expect(result.current.isExpired).toBe(false)
  })

  it('shouldSetIsExpiredToTrueWhenCountdownReachesZero', () => {
    const { result } = renderHook(() => useIdleTimer(60))
    act(() => { vi.advanceTimersByTime(60000) })
    expect(result.current.secondsRemaining).toBe(0)
    expect(result.current.isExpired).toBe(true)
  })

  it('shouldResetCountdownToFullOnResetTimer', () => {
    const { result } = renderHook(() => useIdleTimer(60))
    act(() => { vi.advanceTimersByTime(50000) })
    expect(result.current.secondsRemaining).toBe(10)
    act(() => { result.current.resetTimer() })
    expect(result.current.secondsRemaining).toBe(60)
    expect(result.current.isExpired).toBe(false)
  })

  it('shouldNotLeakIntervalAfterUnmount', () => {
    const clearIntervalSpy = vi.spyOn(globalThis, 'clearInterval')
    const { unmount } = renderHook(() => useIdleTimer(60))
    unmount()
    expect(clearIntervalSpy).toHaveBeenCalled()
    clearIntervalSpy.mockRestore()
  })
})
