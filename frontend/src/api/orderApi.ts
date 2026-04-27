import http from './http'
import { OrderSummary, SubmitOrderRequest, SubmitOrderResponse } from '../types/order.types'

export interface OrdersPage {
  orders: OrderSummary[]
  page: number
  pageSize: number
  totalCount: number
  totalPages: number
}

export interface GetOrdersParams {
  status?: string
  clientId?: string
  tif?: string
  page?: number
  pageSize?: number
}

const OPEN_STATUSES = [
  'NEW', 'VALIDATED', 'RISK_CHECK_PASSED', 'ACCEPTED',
  'SENT_TO_EXECUTION', 'PARTIALLY_FILLED',
]

const now = new Date()
const tomorrow = new Date(now.getTime() + 24 * 60 * 60 * 1000).toISOString()
const in2Days  = new Date(now.getTime() + 2  * 24 * 60 * 60 * 1000).toISOString()
const in90Days = new Date(now.getTime() + 90 * 24 * 60 * 60 * 1000).toISOString()

const MOCK_ORDERS: OrderSummary[] = [
  { id: 'b1000000-0000-0000-0000-000000000001', clientOrderId: 'CLT-ORD-001', clientId: 'a1000000-0000-0000-0000-000000000001', symbol: 'AAPL',  side: 'BUY',  orderType: 'MARKET', timeInForce: 'DAY', quantity: 100, status: 'NEW',              createdAt: new Date(now.getTime() - 10 * 60 * 1000).toISOString() },
  { id: 'b2000000-0000-0000-0000-000000000002', clientOrderId: 'CLT-ORD-002', clientId: 'a1000000-0000-0000-0000-000000000001', symbol: 'MSFT',  side: 'BUY',  orderType: 'LIMIT',  timeInForce: 'GTC', quantity: 50,  limitPrice: '415.00', status: 'SENT_TO_EXECUTION', expiryDate: in90Days, createdAt: new Date(now.getTime() - 25 * 60 * 1000).toISOString() },
  { id: 'b3000000-0000-0000-0000-000000000003', clientOrderId: 'CLT-ORD-003', clientId: 'a2000000-0000-0000-0000-000000000002', symbol: 'TSLA',  side: 'SELL', orderType: 'MARKET', timeInForce: 'DAY', quantity: 20,  status: 'FILLED',            createdAt: new Date(now.getTime() - 90 * 60 * 1000).toISOString() },
  { id: 'b4000000-0000-0000-0000-000000000004', clientOrderId: 'CLT-ORD-004', clientId: 'a1000000-0000-0000-0000-000000000001', symbol: 'GOOGL', side: 'BUY',  orderType: 'LIMIT',  timeInForce: 'GTC', quantity: 10,  limitPrice: '172.00', status: 'RISK_CHECK_PASSED', expiryDate: tomorrow, createdAt: new Date(now.getTime() - 45 * 60 * 1000).toISOString() },
  { id: 'b5000000-0000-0000-0000-000000000005', clientOrderId: 'CLT-ORD-005', clientId: 'a2000000-0000-0000-0000-000000000002', symbol: 'NVDA',  side: 'BUY',  orderType: 'MARKET', timeInForce: 'DAY', quantity: 5,   status: 'VALIDATED',         createdAt: new Date(now.getTime() - 5  * 60 * 1000).toISOString() },
  { id: 'b6000000-0000-0000-0000-000000000006', clientOrderId: 'CLT-ORD-006', clientId: 'a3000000-0000-0000-0000-000000000003', symbol: 'META',  side: 'SELL', orderType: 'LIMIT',  timeInForce: 'DAY', quantity: 15,  limitPrice: '500.00', status: 'EXPIRED',           createdAt: new Date(now.getTime() - 8  * 60 * 60 * 1000).toISOString() },
  { id: 'b7000000-0000-0000-0000-000000000007', clientOrderId: 'CLT-ORD-007', clientId: 'a1000000-0000-0000-0000-000000000001', symbol: 'AAPL',  side: 'SELL', orderType: 'MARKET', timeInForce: 'DAY', quantity: 50,  status: 'FILLED',            createdAt: new Date(now.getTime() - 3  * 60 * 60 * 1000).toISOString() },
  { id: 'b8000000-0000-0000-0000-000000000008', clientOrderId: 'CLT-ORD-008', clientId: 'a4000000-0000-0000-0000-000000000004', symbol: 'MSFT',  side: 'BUY',  orderType: 'LIMIT',  timeInForce: 'GTC', quantity: 30,  limitPrice: '414.00', status: 'ACCEPTED',          expiryDate: in2Days,  createdAt: new Date(now.getTime() - 60 * 60 * 1000).toISOString() },
]

const applyMockFilters = (params?: GetOrdersParams): OrderSummary[] => {
  let result = MOCK_ORDERS
  if (params?.status) {
    if (params.status === 'OPEN') result = result.filter((o) => OPEN_STATUSES.includes(o.status))
    else result = result.filter((o) => o.status === params.status)
  }
  if (params?.tif) result = result.filter((o) => o.timeInForce === params.tif)
  if (params?.clientId) result = result.filter((o) => o.clientId === params.clientId)
  return result
}

export const getOrders = async (params?: GetOrdersParams): Promise<OrdersPage> => {
  try {
    const res = await http.get('/orders', { params })
    return res.data.data
  } catch {
    const orders = applyMockFilters(params)
    return { orders, page: 1, pageSize: 50, totalCount: orders.length, totalPages: 1 }
  }
}

export const submitOrder = async (order: SubmitOrderRequest): Promise<SubmitOrderResponse> => {
  try {
    const res = await http.post('/orders', order)
    return res.data.data
  } catch (err: unknown) {
    // Re-throw API errors (422, 400) so the form can handle them
    if (err && typeof err === 'object' && 'response' in err) {
      const axiosErr = err as { response?: { status?: number; data?: unknown } }
      if (axiosErr.response?.status === 422 || axiosErr.response?.status === 400) {
        throw err
      }
    }
    // Backend offline — simulate 202 success in demo mode
    const fakeOrderId = `ORD-${Math.random().toString(36).substring(2, 10).toUpperCase()}`
    return { orderId: fakeOrderId, status: 'NEW' }
  }
}

export const getOrder = (_id: string): Promise<null> => Promise.resolve(null)
