import http from './http'
import { Execution, OrderSummary, SubmitOrderRequest, SubmitOrderResponse } from '../types/order.types'

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
  { id: 'b1000000-0000-0000-0000-000000000001', clientOrderId: 'CLT-ORD-001', clientId: 'a1000000-0000-0000-0000-000000000001', symbol: 'AAPL',  side: 'BUY',  orderType: 'MARKET', timeInForce: 'DAY', quantity: 100, status: 'NEW',               createdAt: new Date(now.getTime() - 10 * 60 * 1000).toISOString() },
  { id: 'b2000000-0000-0000-0000-000000000002', clientOrderId: 'CLT-ORD-002', clientId: 'a1000000-0000-0000-0000-000000000001', symbol: 'MSFT',  side: 'BUY',  orderType: 'LIMIT',  timeInForce: 'GTC', quantity: 50,  limitPrice: '415.00', status: 'SENT_TO_EXECUTION', expiryDate: in90Days, createdAt: new Date(now.getTime() - 25 * 60 * 1000).toISOString() },
  { id: 'b3000000-0000-0000-0000-000000000003', clientOrderId: 'CLT-ORD-003', clientId: 'a2000000-0000-0000-0000-000000000002', symbol: 'TSLA',  side: 'SELL', orderType: 'MARKET', timeInForce: 'DAY', quantity: 20,  status: 'FILLED',            filledQuantity: 20,  avgFillPrice: '245.70', createdAt: new Date(now.getTime() - 90 * 60 * 1000).toISOString() },
  { id: 'b4000000-0000-0000-0000-000000000004', clientOrderId: 'CLT-ORD-004', clientId: 'a1000000-0000-0000-0000-000000000001', symbol: 'GOOGL', side: 'BUY',  orderType: 'LIMIT',  timeInForce: 'GTC', quantity: 10,  limitPrice: '172.00', status: 'RISK_CHECK_PASSED', expiryDate: tomorrow,  createdAt: new Date(now.getTime() - 45 * 60 * 1000).toISOString() },
  { id: 'b5000000-0000-0000-0000-000000000005', clientOrderId: 'CLT-ORD-005', clientId: 'a2000000-0000-0000-0000-000000000002', symbol: 'NVDA',  side: 'BUY',  orderType: 'MARKET', timeInForce: 'DAY', quantity: 5,   status: 'VALIDATED',          createdAt: new Date(now.getTime() - 5  * 60 * 1000).toISOString() },
  { id: 'b6000000-0000-0000-0000-000000000006', clientOrderId: 'CLT-ORD-006', clientId: 'a3000000-0000-0000-0000-000000000003', symbol: 'META',  side: 'SELL', orderType: 'LIMIT',  timeInForce: 'DAY', quantity: 15,  limitPrice: '500.00', status: 'EXPIRED',            createdAt: new Date(now.getTime() - 8  * 60 * 60 * 1000).toISOString() },
  { id: 'b7000000-0000-0000-0000-000000000007', clientOrderId: 'CLT-ORD-007', clientId: 'a1000000-0000-0000-0000-000000000001', symbol: 'AAPL',  side: 'SELL', orderType: 'MARKET', timeInForce: 'DAY', quantity: 50,  status: 'FILLED',            filledQuantity: 50,  avgFillPrice: '178.35', createdAt: new Date(now.getTime() - 3  * 60 * 60 * 1000).toISOString() },
  { id: 'b8000000-0000-0000-0000-000000000008', clientOrderId: 'CLT-ORD-008', clientId: 'a4000000-0000-0000-0000-000000000004', symbol: 'MSFT',  side: 'BUY',  orderType: 'LIMIT',  timeInForce: 'GTC', quantity: 30,  limitPrice: '414.00', status: 'ACCEPTED',           expiryDate: in2Days,   createdAt: new Date(now.getTime() - 60 * 60 * 1000).toISOString() },
  { id: 'b9000000-0000-0000-0000-000000000009', clientOrderId: 'CLT-ORD-009', clientId: 'a3000000-0000-0000-0000-000000000003', symbol: 'AMD',   side: 'BUY',  orderType: 'LIMIT',  timeInForce: 'DAY', quantity: 40,  limitPrice: '155.00', status: 'PARTIALLY_FILLED',  filledQuantity: 25,  avgFillPrice: '154.90', createdAt: new Date(now.getTime() - 2  * 60 * 60 * 1000).toISOString() },
  { id: 'b0000000-0000-0000-0000-000000000010', clientOrderId: 'CLT-ORD-010', clientId: 'a5000000-0000-0000-0000-000000000005', symbol: 'NFLX',  side: 'BUY',  orderType: 'MARKET', timeInForce: 'DAY', quantity: 3,   status: 'RISK_REJECTED',      createdAt: new Date(now.getTime() - 15 * 60 * 1000).toISOString() },
]

// ── Mock fills ────────────────────────────────────────────────────────────────
const MOCK_EXECUTIONS: Record<string, Execution[]> = {
  'b3000000-0000-0000-0000-000000000003': [
    { executionId: 'e3000000-0000-0000-0000-000000000001', orderId: 'b3000000-0000-0000-0000-000000000003', symbol: 'TSLA', side: 'SELL', executedQty: 20, executedPrice: '245.70', fillType: 'FULL',    fillValue: '4914.00',  remainingQuantity: 0,  executionVenue: 'MOCK_ECN', executedAt: new Date(now.getTime() - 88 * 60 * 1000).toISOString() },
  ],
  'b7000000-0000-0000-0000-000000000007': [
    { executionId: 'e7000000-0000-0000-0000-000000000001', orderId: 'b7000000-0000-0000-0000-000000000007', symbol: 'AAPL', side: 'SELL', executedQty: 50, executedPrice: '178.35', fillType: 'FULL',    fillValue: '8917.50',  remainingQuantity: 0,  executionVenue: 'MOCK_ECN', executedAt: new Date(now.getTime() - 178 * 60 * 1000).toISOString() },
  ],
  'b9000000-0000-0000-0000-000000000009': [
    { executionId: 'e9000000-0000-0000-0000-000000000001', orderId: 'b9000000-0000-0000-0000-000000000009', symbol: 'AMD',  side: 'BUY',  executedQty: 15, executedPrice: '154.90', fillType: 'PARTIAL', fillValue: '2323.50',  remainingQuantity: 25, executionVenue: 'MOCK_ECN', executedAt: new Date(now.getTime() - 115 * 60 * 1000).toISOString() },
    { executionId: 'e9000000-0000-0000-0000-000000000002', orderId: 'b9000000-0000-0000-0000-000000000009', symbol: 'AMD',  side: 'BUY',  executedQty: 10, executedPrice: '154.90', fillType: 'PARTIAL', fillValue: '1549.00',  remainingQuantity: 15, executionVenue: 'MOCK_ECN', executedAt: new Date(now.getTime() - 112 * 60 * 1000).toISOString() },
  ],
}

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

export const getOrder = async (id: string): Promise<OrderSummary | null> => {
  try {
    const res = await http.get(`/orders/${id}`)
    return res.data.data
  } catch {
    return MOCK_ORDERS.find((o) => o.id === id) ?? null
  }
}

export const getExecutions = async (orderId: string): Promise<Execution[]> => {
  try {
    const res = await http.get(`/executions/${orderId}`)
    return res.data.data
  } catch {
    return MOCK_EXECUTIONS[orderId] ?? []
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
