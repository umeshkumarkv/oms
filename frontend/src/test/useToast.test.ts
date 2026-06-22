import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useToast } from '../hooks/useToast'
import { TOAST_TYPE } from '../components/Toast'

describe('useToast', () => {
  beforeEach(() => { vi.useFakeTimers() })
  afterEach(() => { vi.useRealTimers() })

  it('shouldAddToastToArray', () => {
    const { result } = renderHook(() => useToast())

    act(() => {
      result.current.addToast('Something went wrong', TOAST_TYPE.ERROR)
    })

    expect(result.current.toasts).toHaveLength(1)
    expect(result.current.toasts[0].message).toBe('Something went wrong')
    expect(result.current.toasts[0].type).toBe(TOAST_TYPE.ERROR)
  })

  it('shouldAppendSecondToastWithoutOverwritingFirst', () => {
    const { result } = renderHook(() => useToast())

    act(() => {
      result.current.addToast('First error', TOAST_TYPE.ERROR)
      result.current.addToast('Second warning', TOAST_TYPE.WARNING)
    })

    expect(result.current.toasts).toHaveLength(2)
    expect(result.current.toasts[0].message).toBe('First error')
    expect(result.current.toasts[1].message).toBe('Second warning')
  })

  it('shouldAutoRemoveToastAfterDuration', () => {
    const { result } = renderHook(() => useToast())

    act(() => {
      result.current.addToast('Auto-dismiss me', TOAST_TYPE.WARNING)
    })
    expect(result.current.toasts).toHaveLength(1)

    act(() => { vi.runAllTimers() })

    expect(result.current.toasts).toHaveLength(0)
  })

  it('shouldRemoveOnlyExpiredToastWhenMultipleActive', () => {
    const { result } = renderHook(() => useToast())

    act(() => {
      result.current.addToast('Short-lived', TOAST_TYPE.ERROR, 1000)
      result.current.addToast('Long-lived', TOAST_TYPE.SUCCESS, 10_000)
    })
    expect(result.current.toasts).toHaveLength(2)

    act(() => { vi.advanceTimersByTime(1000) })

    expect(result.current.toasts).toHaveLength(1)
    expect(result.current.toasts[0].message).toBe('Long-lived')
  })
})
