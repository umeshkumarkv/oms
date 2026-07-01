import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import MarketDataPage from '../pages/MarketDataPage'
import type { Instrument, MarketConfig, TickHistoryResponse } from '../types/market.types'

// ── vi.hoisted: survives Vitest's mock-hoisting transform ─────────────────────
const { mockIntradayChart } = vi.hoisted(() => ({
  mockIntradayChart: vi.fn(() => null),
}))

// ── Module mocks ───────────────────────────────────────────────────────────────

const mockUseMarketSocket = vi.fn()
vi.mock('../hooks/useMarketSocket', () => ({
  useMarketSocket: (...args: unknown[]) => mockUseMarketSocket(...args),
}))

const mockGetInstruments  = vi.fn()
const mockGetInstrument   = vi.fn()
const mockGetMarketConfig = vi.fn()
const mockGetTickHistory  = vi.fn()
vi.mock('../api/marketApi', () => ({
  getInstruments:  () => mockGetInstruments(),
  getInstrument:   (s: string) => mockGetInstrument(s),
  getMarketConfig: () => mockGetMarketConfig(),
  getTickHistory:  (s: string) => mockGetTickHistory(s),
}))

const mockGetWatchlist = vi.fn()
vi.mock('../api/watchlistApi', () => ({
  getWatchlist:        () => mockGetWatchlist(),
  addToWatchlist:      () => Promise.resolve(),
  removeFromWatchlist: () => Promise.resolve(),
}))

vi.mock('../api/apiClient', () => ({
  getCurrentUser: () => null,
}))

vi.mock('../services/websocketService', () => ({
  marketDataWs: {
    connect:            vi.fn(),
    disconnect:         vi.fn(),
    subscribe:          vi.fn(),
    unsubscribe:        vi.fn(),
    onTick:             vi.fn(() => () => {}),
    onConnectionChange: vi.fn(() => () => {}),
    onMarketStatus:     vi.fn(() => () => {}),
    isConnected:        vi.fn(() => false),
  },
}))

vi.mock('../utils/marketHours', () => ({
  isMarketOpen: vi.fn(() => false),
}))

vi.mock('../components/StockCard', () => ({
  IntradayChart: mockIntradayChart,
}))

// ── Fixtures ───────────────────────────────────────────────────────────────────

const AAPL: Instrument = {
  symbol:        'AAPL',
  bidPrice:      '149.95',
  askPrice:      '150.05',
  lastPrice:     '150.00',
  tradable:      true,
  halted:        false,
  minQuantity:   1,
  maxQuantity:   10000,
  dataTimestamp: '2026-06-29T14:30:00Z',
  changePct:     '0.50',
  previousClose: '149.25',
}

const MARKET_CONFIG: MarketConfig = {
  pollingIntervalMs: 5000,
  marketOpen:        true,
  marketHours:       { start: '09:30', end: '16:00', timezone: 'America/New_York' },
  dataSource:        'WEBSOCKET',
}

const TICK_HISTORY: TickHistoryResponse = {
  symbol: 'AAPL',
  from:   '09:30',
  to:     '09:31',
  ticks: [
    { time: '09:30', price: '149.50', bid: '149.45', ask: '149.55', volume: 1000 },
    { time: '09:31', price: '150.00', bid: '149.95', ask: '150.05', volume: 2000 },
  ],
}

function renderPage() {
  return render(
    <MemoryRouter>
      <MarketDataPage />
    </MemoryRouter>
  )
}

// ── Tests ──────────────────────────────────────────────────────────────────────

describe('MarketDataPage — WebSocket status pill and banner', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetMarketConfig.mockResolvedValue(MARKET_CONFIG)
    mockGetInstruments.mockResolvedValue([AAPL])
    mockGetWatchlist.mockRejectedValue(new Error('404'))
    mockGetInstrument.mockResolvedValue(AAPL)
    mockGetTickHistory.mockResolvedValue(TICK_HISTORY)
  })

  it('shouldNotRenderOfflineBannerWhenWsLiveIsTrue', () => {
    mockUseMarketSocket.mockReturnValue({ ticks: {}, wsLive: true })
    renderPage()
    expect(screen.queryByText(/WebSocket offline/)).toBeNull()
    expect(screen.getByText('Live')).toBeDefined()
  })

  it('shouldRenderOfflineBannerWhenWsLiveIsFalse', () => {
    mockUseMarketSocket.mockReturnValue({ ticks: {}, wsLive: false })
    renderPage()
    expect(screen.getByText(/WebSocket offline/)).toBeDefined()
    expect(screen.queryByText('Live')).toBeNull()
  })

  it('shouldPassLivePropTrueToIntradayChartWhenWsLiveAndMarketOpen', async () => {
    mockUseMarketSocket.mockReturnValue({ ticks: {}, wsLive: true })
    mockIntradayChart.mockClear()
    const { container } = renderPage()

    // Wait for initial load: getMarketConfig + getInstruments resolve → wlApiMissing=true → .mkt-row appears
    await waitFor(() => {
      expect(container.querySelector('.mkt-row')).not.toBeNull()
    }, { timeout: 3000 })

    // Click AAPL row → handleSelectSymbol fires → getTickHistory resolves → candles set
    fireEvent.click(container.querySelector('.mkt-row') as HTMLElement)

    // IntradayChart must render with live={true}: wsLive=true, hasChartData=true, marketOpen=true
    await waitFor(() => {
      expect(mockIntradayChart).toHaveBeenCalled()
      const allCalls = mockIntradayChart.mock.calls as unknown[][]
      const lastProps = allCalls[allCalls.length - 1][0] as { live: boolean }
      expect(lastProps.live).toBe(true)
    }, { timeout: 3000 })
  })
})
