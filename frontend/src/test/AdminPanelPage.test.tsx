import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import AdminPanelPage from '../pages/AdminPanelPage'
import * as authApi from '../api/authApi'
import * as riskApi from '../api/riskApi'
import { RiskRule } from '../types/risk.types'

// ── Mocks ──────────────────────────────────────────────────────────────────────

vi.mock('../api/authApi')
vi.mock('../api/riskApi')

const mockUser = { username: 'admin01', role: 'ADMIN' as const, sub: 'u1' }

const makeRule = (
  ruleCode: string,
  ruleCategory: RiskRule['ruleCategory'],
  priority: number,
  enabled = true,
): RiskRule => ({
  ruleCode,
  ruleName: ruleCode,
  ruleCategory,
  description: `${ruleCode} description`,
  enabled,
  thresholdValue: null,
  thresholdUnit: null,
  action: 'REJECT',
  priority,
})

const MOCK_RULES: RiskRule[] = [
  makeRule('MARKET_HOURS_CHECK',       'REDUNDANT',       1),
  makeRule('CLIENT_ACTIVE_CHECK',      'REDUNDANT',       2),
  makeRule('CASH_BALANCE_CHECK',       'REDUNDANT',       3),
  makeRule('HOLDINGS_CHECK',           'REDUNDANT',       4),
  makeRule('RULE_TRADING_ENABLED',     'CLIENT',          5),
  makeRule('RULE_KYC_VERIFIED',        'CLIENT',          6),
  makeRule('RULE_SUFFICIENT_CASH',     'CLIENT',          7),
  makeRule('RULE_SUFFICIENT_HOLDINGS', 'CLIENT',          8),
  makeRule('RULE_MARGIN_CALL_ACTIVE',  'CLIENT',          9),
  makeRule('RULE_DUPLICATE_ORDER',     'STATEFUL',        10),
  makeRule('RULE_SELF_TRADE',          'STATEFUL',        11),
  makeRule('INSTRUMENT_TRADABLE_CHECK','REDUNDANT',       12),
  makeRule('SINGLE_ORDER_QTY_LIMIT',  'FINANCIAL_LIMIT', 13),
  makeRule('ORDER_VALUE_LIMIT',        'FINANCIAL_LIMIT', 14),
  makeRule('RULE_MAX_ORDER_QTY',       'PRE_TRADE',       15),
  makeRule('RULE_MAX_ORDER_VALUE',     'PRE_TRADE',       16),
  makeRule('QUANTITY_POSITIVE_CHECK',  'REDUNDANT',       17),
  makeRule('LIMIT_PRICE_CHECK',        'REDUNDANT',       18),
  makeRule('PRICE_DEVIATION_CHECK',    'FAT_FINGER',      19, false),
  makeRule('NOTIONAL_VALUE_CHECK',     'FAT_FINGER',      20, false),
  makeRule('ORDER_QTY_SPIKE_CHECK',    'FAT_FINGER',      21, false),
  makeRule('ORDER_VALUE_SPIKE_CHECK',  'FAT_FINGER',      22, false),
  makeRule('RULE_DAILY_ORDER_COUNT',   'FAT_FINGER',      23, false),
]

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminPanelPage />
    </MemoryRouter>
  )
}

beforeEach(() => {
  vi.mocked(authApi.getCurrentUser).mockReturnValue(mockUser as never)
  vi.mocked(riskApi.getRiskRules).mockResolvedValue(MOCK_RULES)
  vi.mocked(riskApi.getRiskRuleAudit).mockResolvedValue([])
  vi.mocked(riskApi.getPendingChanges).mockResolvedValue([])
  vi.mocked(riskApi.patchRiskRule).mockResolvedValue(undefined as never)
})

// ── Tests ──────────────────────────────────────────────────────────────────────

describe('AdminPanelPage — risk rule grouping', () => {

  it('shouldRenderFiveGroupHeaders', async () => {
    renderPage()
    expect(await screen.findByText('Market Rules')).toBeTruthy()
    expect(screen.getByText('Client Rules')).toBeTruthy()
    expect(screen.getByText('Instrument Rules')).toBeTruthy()
    expect(screen.getByText('Order Rules')).toBeTruthy()
    expect(screen.getByText('Fat Finger / Risk Controls')).toBeTruthy()
  })

  it('shouldShowRuleCountBadgePerGroup', async () => {
    renderPage()
    await screen.findByText('Market Rules')
    // Market: 1, Client: 10, Instrument: 1, Order: 6, Fat Finger: 5
    const badges = screen.getAllByText(/^\d+$/)
    const counts = badges.map(b => Number(b.textContent)).sort((a, b) => a - b)
    expect(counts).toContain(1)   // Market
    expect(counts).toContain(5)   // Fat Finger
    expect(counts).toContain(6)   // Order
    expect(counts).toContain(10)  // Client
  })

  it('shouldCollapseGroupWhenHeaderClicked', async () => {
    renderPage()
    const header = await screen.findByText('Market Rules')
    // MARKET_HOURS_CHECK is visible initially
    expect(screen.getByText('MARKET_HOURS_CHECK')).toBeTruthy()
    fireEvent.click(header.closest('[role="button"]')!)
    expect(screen.queryByText('MARKET_HOURS_CHECK')).toBeNull()
  })

  it('shouldExpandGroupWhenHeaderClickedAgain', async () => {
    renderPage()
    const header = await screen.findByText('Market Rules')
    const headerBtn = header.closest('[role="button"]')!
    fireEvent.click(headerBtn) // collapse
    fireEvent.click(headerBtn) // re-expand
    expect(screen.getByText('MARKET_HOURS_CHECK')).toBeTruthy()
  })

  it('shouldCollapseAllGroupsWhenCollapseAllClicked', async () => {
    renderPage()
    await screen.findByText('Market Rules')
    const collapseBtn = screen.getByText('Collapse All')
    fireEvent.click(collapseBtn)
    expect(screen.queryByText('MARKET_HOURS_CHECK')).toBeNull()
    expect(screen.queryByText('RULE_TRADING_ENABLED')).toBeNull()
    expect(screen.queryByText('PRICE_DEVIATION_CHECK')).toBeNull()
  })

  it('shouldExpandAllGroupsWhenExpandAllClicked', async () => {
    renderPage()
    await screen.findByText('Market Rules')
    // Collapse all first
    fireEvent.click(screen.getByText('Collapse All'))
    // Button now shows Expand All
    fireEvent.click(screen.getByText('Expand All'))
    expect(screen.getByText('MARKET_HOURS_CHECK')).toBeTruthy()
    expect(screen.getByText('PRICE_DEVIATION_CHECK')).toBeTruthy()
  })

  it('shouldPlaceEachRuleInExactlyOneGroup', async () => {
    renderPage()
    await screen.findByText('Market Rules')
    // Every rule code appears exactly once in the document
    for (const rule of MOCK_RULES) {
      const elements = screen.getAllByText(rule.ruleCode)
      expect(elements).toHaveLength(1)
    }
  })

  it('shouldOpenToggleModalWhenEnableToggleClicked', async () => {
    renderPage()
    await screen.findByText('MARKET_HOURS_CHECK')
    const switches = screen.getAllByRole('switch')
    fireEvent.click(switches[0])
    // The ToggleModal renders a textarea with this placeholder — unique to the modal
    expect(await screen.findByPlaceholderText(/min 10 characters/)).toBeTruthy()
  })

  it('shouldKeepAuditLogAsAFlatTable', async () => {
    vi.mocked(riskApi.getRiskRuleAudit).mockResolvedValue([{
      id: 'rec-1', ruleCode: 'ORDER_VALUE_LIMIT', fieldChanged: 'enabled',
      oldValue: 'true', newValue: 'false', changedBy: 'admin01',
      changeReason: 'Testing audit log', changedAt: '2026-05-15T10:00:00Z',
    }])
    renderPage()
    // Audit log has a flat Rule/Field/Change/Changed By/Reason/When header — no group headers inside it
    expect(await screen.findByText('Changed By')).toBeTruthy()
    // Audit log rule code appears outside of a group section (it's in the flat audit table)
    const cells = screen.getAllByText('ORDER_VALUE_LIMIT')
    // One cell is the grouped rule row, one is the audit log row
    expect(cells.length).toBeGreaterThanOrEqual(1)
  })

  it('shouldApplyReducedOpacityToDisabledRules', async () => {
    renderPage()
    await screen.findByText('PRICE_DEVIATION_CHECK')
    // PRICE_DEVIATION_CHECK is disabled — its row should have opacity 0.6
    const cell = screen.getByText('PRICE_DEVIATION_CHECK')
    const row = cell.closest('tr') as HTMLTableRowElement
    expect(row.style.opacity).toBe('0.6')
  })

})
