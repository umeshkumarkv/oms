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
  timeInForce?: string
  createdAfter?: string
  page?: number
  pageSize?: number
}

export const getOrders = async (params?: GetOrdersParams): Promise<OrdersPage> => {
  const res = await http.get('/orders', { params })
  return res.data.data
}

export const getOrder = async (id: string): Promise<OrderSummary> => {
  const res = await http.get(`/orders/${id}`)
  return res.data.data
}

export const getExecutions = async (orderId: string): Promise<Execution[]> => {
  const res = await http.get(`/executions/${orderId}`)
  return res.data.data
}

export const submitOrder = async (order: SubmitOrderRequest): Promise<SubmitOrderResponse> => {
  const body = {
    clientOrderId:      order.clientOrderId,
    clientId:           order.clientId,
    symbol:             order.symbol,
    side:               order.side,
    orderType:          order.orderType,
    tif:                order.tif,
    quantity:           order.quantity,
    limitPrice:         order.limitPrice,
    gtdDate:            order.gtdDate ?? undefined,
    routingDestination: order.routingDestination,
    fillOrKill:         order.fillOrKill ?? undefined,
  }
  const res = await http.post('/orders', body)
  return res.data.data
}
