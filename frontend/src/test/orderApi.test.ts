import { vi, describe, it, expect, beforeEach } from 'vitest'
import { submitOrder } from '../api/orderApi'

vi.mock('../api/http', () => ({ default: { post: vi.fn() } }))

describe('orderApi.submitOrder', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('includes fillOrKill in payload when provided', async () => {
    const http = (await import('../api/http')).default
    ;(http.post as any).mockResolvedValue({ data: { data: { orderId: 'abc' } } })

    const payload = {
      clientOrderId: 'UI-1',
      clientId: 'c1',
      symbol: 'AAPL',
      side: 'BUY',
      orderType: 'MARKET',
      tif: 'DAY',
      quantity: 1,
      fillOrKill: true,
    }

    const res = await submitOrder(payload as any)

    expect(http.post).toHaveBeenCalled()
    const [url, body] = (http.post as any).mock.calls[0]
    expect(url).toBe('/orders')
    expect(body.fillOrKill).toBe(true)
  })
})
