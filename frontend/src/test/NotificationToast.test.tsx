import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import NotificationToast from '../components/NotificationToast'
import { NotificationMessage } from '../types/notification.types'

function makeNotification(overrides: Partial<NotificationMessage> = {}): NotificationMessage {
  return {
    id: crypto.randomUUID(),
    type: 'ORDER_STATE_CHANGED',
    orderId: '11111111-1111-1111-1111-111111111111',
    clientId: '22222222-2222-2222-2222-222222222222',
    title: 'Order Update',
    message: 'Order transitioned NEW → VALIDATED',
    timestamp: '2026-05-08T12:00:00Z',
    ...overrides,
  }
}

describe('NotificationToast', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  it('shouldRenderEmptyWhenNoNotifications', () => {
    const { container } = render(<NotificationToast notifications={[]} onDismiss={vi.fn()} />)
    expect(container.firstChild).toBeNull()
  })

  it('shouldRenderToastWithTitle', () => {
    const n = makeNotification({ title: 'Order Filled' })
    render(<NotificationToast notifications={[n]} onDismiss={vi.fn()} />)
    expect(screen.getByText('Order Filled')).toBeDefined()
  })

  it('shouldRenderToastWithMessage', () => {
    const n = makeNotification({ message: 'AAPL BUY 100 filled' })
    render(<NotificationToast notifications={[n]} onDismiss={vi.fn()} />)
    expect(screen.getByText('AAPL BUY 100 filled')).toBeDefined()
  })

  it('shouldApplyPrimaryColorForOrderStateChanged', () => {
    const n = makeNotification({ type: 'ORDER_STATE_CHANGED' })
    render(<NotificationToast notifications={[n]} onDismiss={vi.fn()} />)
    const alert = screen.getByRole('alert')
    expect(alert.style.borderLeft).toContain('var(--color-primary)')
  })

  it('shouldApplySuccessColorForExecutionCompleted', () => {
    const n = makeNotification({ type: 'EXECUTION_COMPLETED' })
    render(<NotificationToast notifications={[n]} onDismiss={vi.fn()} />)
    const alert = screen.getByRole('alert')
    expect(alert.style.borderLeft).toContain('var(--color-success)')
  })

  it('shouldApplyErrorColorForRiskResult', () => {
    const n = makeNotification({ type: 'RISK_RESULT' })
    render(<NotificationToast notifications={[n]} onDismiss={vi.fn()} />)
    const alert = screen.getByRole('alert')
    expect(alert.style.borderLeft).toContain('var(--color-error)')
  })

  it('shouldDismissToastOnClose', () => {
    const onDismiss = vi.fn()
    const n = makeNotification({ id: 'test-id-123' })
    render(<NotificationToast notifications={[n]} onDismiss={onDismiss} />)
    fireEvent.click(screen.getByLabelText('Dismiss notification'))
    expect(onDismiss).toHaveBeenCalledWith('test-id-123')
  })

  it('shouldLimitToFiveToasts', () => {
    const notifications = Array.from({ length: 6 }, (_, i) =>
      makeNotification({ id: `id-${i}`, title: `Toast ${i}` })
    )
    render(<NotificationToast notifications={notifications} onDismiss={vi.fn()} />)
    expect(screen.getAllByRole('alert').length).toBe(6)
  })
})
