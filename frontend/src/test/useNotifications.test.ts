import { renderHook, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'

// vi.hoisted: initialized before vi.mock hoisting runs
const store = vi.hoisted(() => ({
  subscribe: vi.fn(),
  activate: vi.fn(),
  deactivate: vi.fn(),
  config: null as Record<string, unknown> | null,
}))

vi.mock('@stomp/stompjs', () => ({
  // Use regular function (not arrow) so `this` is the new instance
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  Client: vi.fn().mockImplementation(function (this: any, config: Record<string, unknown>) {
    store.config = config
    this.subscribe = store.subscribe
    this.activate = store.activate
    this.deactivate = store.deactivate
  }),
}))

vi.mock('sockjs-client', () => ({ default: vi.fn() }))

import { useNotifications } from '../hooks/useNotifications'

describe('useNotifications', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    store.config = null
  })

  it('shouldInitializeWithEmptyNotifications', () => {
    const { result } = renderHook(() => useNotifications('token', 'user-id', 'TRADER'))
    expect(result.current.notifications).toEqual([])
  })

  it('shouldActivateStompClientOnMount', () => {
    renderHook(() => useNotifications('token', 'user-id', 'TRADER'))
    expect(store.activate).toHaveBeenCalledTimes(1)
  })

  it('shouldSubscribeToTraderDestinationForTraderRole', () => {
    renderHook(() => useNotifications('token', 'abc-123', 'TRADER'))
    act(() => { (store.config?.onConnect as (() => void) | undefined)?.() })
    expect(store.subscribe).toHaveBeenCalledWith(
      '/topic/notifications/abc-123',
      expect.any(Function)
    )
  })

  it('shouldSubscribeToBroadcastForOperationsRole', () => {
    renderHook(() => useNotifications('token', 'user-id', 'OPERATIONS'))
    act(() => { (store.config?.onConnect as (() => void) | undefined)?.() })
    expect(store.subscribe).toHaveBeenCalledWith(
      '/topic/notifications/broadcast',
      expect.any(Function)
    )
  })

  it('shouldAddNotificationWhenMessageReceived', () => {
    const { result } = renderHook(() => useNotifications('token', 'user-id', 'TRADER'))
    act(() => { (store.config?.onConnect as (() => void) | undefined)?.() })

    const frameHandler = store.subscribe.mock.calls[0][1] as (f: { body: string }) => void
    act(() => {
      frameHandler({
        body: JSON.stringify({
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

  it('shouldCapAtFiveNotifications', () => {
    const { result } = renderHook(() => useNotifications('token', 'user-id', 'TRADER'))
    act(() => { (store.config?.onConnect as (() => void) | undefined)?.() })

    const frameHandler = store.subscribe.mock.calls[0][1] as (f: { body: string }) => void
    act(() => {
      for (let i = 0; i < 6; i++) {
        frameHandler({
          body: JSON.stringify({
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

  it('shouldDismissById', () => {
    const { result } = renderHook(() => useNotifications('token', 'user-id', 'TRADER'))
    act(() => { (store.config?.onConnect as (() => void) | undefined)?.() })

    const frameHandler = store.subscribe.mock.calls[0][1] as (f: { body: string }) => void
    act(() => {
      frameHandler({
        body: JSON.stringify({
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

  it('shouldIgnoreMalformedFrames', () => {
    const { result } = renderHook(() => useNotifications('token', 'user-id', 'TRADER'))
    act(() => { (store.config?.onConnect as (() => void) | undefined)?.() })

    const frameHandler = store.subscribe.mock.calls[0][1] as (f: { body: string }) => void
    act(() => { frameHandler({ body: 'not-json' }) })

    expect(result.current.notifications.length).toBe(0)
  })
})
