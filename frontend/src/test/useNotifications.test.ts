import { renderHook, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'

// Capture callbacks passed to fetchEventSource so tests can invoke them
const store = vi.hoisted(() => ({
  onopen:    null as ((response: Response) => Promise<void>) | null,
  onmessage: null as ((event: { data: string }) => void) | null,
  onclose:   null as (() => void) | null,
  onerror:   null as ((error: Error) => void) | null,
  called:    false,
}))

vi.mock('@microsoft/fetch-event-source', () => ({
  fetchEventSource: vi.fn().mockImplementation(
    (_url: string, options: {
      onopen?:    (response: Response) => Promise<void>
      onmessage?: (event: { data: string }) => void
      onclose?:   () => void
      onerror?:   (error: Error) => void
    }) => {
      store.called    = true
      store.onopen    = options.onopen    ?? null
      store.onmessage = options.onmessage ?? null
      store.onclose   = options.onclose   ?? null
      store.onerror   = options.onerror   ?? null
      return new Promise(() => { /* never resolves — simulates open SSE stream */ })
    }
  ),
}))

import { useNotifications } from '../hooks/useNotifications'

describe('useNotifications', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    store.called    = false
    store.onopen    = null
    store.onmessage = null
    store.onclose   = null
    store.onerror   = null
  })

  it('shouldInitializeWithEmptyNotificationsAndNotConnected', () => {
    const { result } = renderHook(() => useNotifications('token', 'user-id', 'TRADER'))
    expect(result.current.notifications).toEqual([])
    expect(result.current.sseConnected).toBe(false)
  })

  it('shouldCallFetchEventSourceWithCorrectUrlAndAuthHeader', () => {
    renderHook(() => useNotifications('my-token', 'user-id', 'TRADER'))
    expect(store.called).toBe(true)
  })

  it('shouldNotCallFetchEventSourceWhenTokenIsEmpty', () => {
    renderHook(() => useNotifications('', 'user-id', 'TRADER'))
    expect(store.called).toBe(false)
  })

  it('shouldNotCallFetchEventSourceWhenUserIdIsEmpty', () => {
    renderHook(() => useNotifications('token', '', 'TRADER'))
    expect(store.called).toBe(false)
  })

  it('shouldSetSseConnectedTrueOnSuccessfulOpen', async () => {
    const { result } = renderHook(() => useNotifications('token', 'user-id', 'TRADER'))
    await act(async () => {
      await store.onopen?.(new Response(null, { status: 200 }))
    })
    expect(result.current.sseConnected).toBe(true)
  })

  it('shouldAddNotificationWhenMessageReceived', async () => {
    const { result } = renderHook(() => useNotifications('token', 'user-id', 'TRADER'))
    await act(async () => {
      await store.onopen?.(new Response(null, { status: 200 }))
    })
    act(() => {
      store.onmessage?.({
        data: JSON.stringify({
          type: 'ORDER_STATE_CHANGED',
          orderId: 'ord-1',
          clientId: 'cli-1',
          title: 'Order Update',
          message: 'NEW → VALIDATED',
          timestamp: '2026-05-08T12:00:00Z',
        }),
      })
    })
    expect(result.current.notifications.length).toBe(1)
    expect(result.current.notifications[0].title).toBe('Order Update')
  })

  it('shouldIgnoreKeepaliveComment', async () => {
    const { result } = renderHook(() => useNotifications('token', 'user-id', 'TRADER'))
    await act(async () => {
      await store.onopen?.(new Response(null, { status: 200 }))
    })
    act(() => { store.onmessage?.({ data: ':keepalive' }) })
    expect(result.current.notifications.length).toBe(0)
    expect(result.current.sseConnected).toBe(true)
  })

  it('shouldIgnoreEmptyData', () => {
    const { result } = renderHook(() => useNotifications('token', 'user-id', 'TRADER'))
    act(() => { store.onmessage?.({ data: '' }) })
    expect(result.current.notifications.length).toBe(0)
  })

  it('shouldCapNotificationsAtMaxToasts', () => {
    const { result } = renderHook(() => useNotifications('token', 'user-id', 'TRADER'))
    act(() => {
      for (let i = 0; i < 6; i++) {
        store.onmessage?.({
          data: JSON.stringify({
            type: 'ORDER_STATE_CHANGED',
            orderId: null,
            clientId: null,
            title: `Toast ${i}`,
            message: `Message ${i}`,
            timestamp: '2026-05-08T12:00:00Z',
          }),
        })
      }
    })
    expect(result.current.notifications.length).toBe(5)
    expect(result.current.notifications[0].title).toBe('Toast 1')
  })

  it('shouldIgnoreMalformedFrames', () => {
    const { result } = renderHook(() => useNotifications('token', 'user-id', 'TRADER'))
    act(() => { store.onmessage?.({ data: 'not-json' }) })
    expect(result.current.notifications.length).toBe(0)
  })

  it('shouldSetSseConnectedFalseOnClose', async () => {
    const { result } = renderHook(() => useNotifications('token', 'user-id', 'TRADER'))
    await act(async () => {
      await store.onopen?.(new Response(null, { status: 200 }))
    })
    act(() => { store.onclose?.() })
    expect(result.current.sseConnected).toBe(false)
  })

  it('shouldSetSseConnectedFalseOnError', async () => {
    const { result } = renderHook(() => useNotifications('token', 'user-id', 'TRADER'))
    await act(async () => {
      await store.onopen?.(new Response(null, { status: 200 }))
    })
    act(() => { store.onerror?.(new Error('connection error')) })
    expect(result.current.sseConnected).toBe(false)
  })

  it('shouldAbortControllerOnUnmount', () => {
    const abortSpy = vi.spyOn(AbortController.prototype, 'abort')
    const { unmount } = renderHook(() => useNotifications('token', 'user-id', 'TRADER'))
    unmount()
    expect(abortSpy).toHaveBeenCalledTimes(1)
  })

  it('shouldDismissById', () => {
    const { result } = renderHook(() => useNotifications('token', 'user-id', 'TRADER'))
    act(() => {
      store.onmessage?.({
        data: JSON.stringify({
          type: 'EXECUTION_COMPLETED',
          orderId: null,
          clientId: null,
          title: 'Filled',
          message: 'AAPL filled',
          timestamp: '2026-05-08T12:00:00Z',
        }),
      })
    })
    const id = result.current.notifications[0].id
    act(() => { result.current.dismiss(id) })
    expect(result.current.notifications.length).toBe(0)
  })
})
