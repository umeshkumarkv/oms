import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import ActiveClientsPanel from '../components/ActiveClientsPanel'
import { ActiveClient } from '../types/client.types'

// ── Fixtures ──────────────────────────────────────────────────────────────────

const makeClient = (overrides: Partial<ActiveClient> = {}): ActiveClient => ({
  clientId:         'a1000000-0000-0000-0000-000000000001',
  clientName:       'John Smith',
  turnoverToday:    '500000.00',
  availableCash:    '450000.00',
  cashLimitWarning: false,
  ...overrides,
})

const threeClients: ActiveClient[] = [
  makeClient({ clientId: 'a1000000-0000-0000-0000-000000000001', clientName: 'John Smith',    turnoverToday: '1250000.00', availableCash: '500000.00', cashLimitWarning: false }),
  makeClient({ clientId: 'a2000000-0000-0000-0000-000000000002', clientName: 'Sarah Johnson', turnoverToday: '450000.00',  availableCash: '8000.00',   cashLimitWarning: true  }),
  makeClient({ clientId: 'a3000000-0000-0000-0000-000000000003', clientName: 'Acme Corp',     turnoverToday: '500.00',     availableCash: '75000.00',  cashLimitWarning: false }),
]

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('ActiveClientsPanel — column headers', () => {
  it('shouldRenderTableWithFiveColumnHeaders', () => {
    render(<ActiveClientsPanel clients={threeClients} error={false} />)
    expect(screen.getByText('#')).toBeInTheDocument()
    expect(screen.getByText('Client Name')).toBeInTheDocument()
    expect(screen.getByText("Today's Turnover")).toBeInTheDocument()
    expect(screen.getByText('Available Cash')).toBeInTheDocument()
    expect(screen.getByText('Status')).toBeInTheDocument()
  })
})

describe('ActiveClientsPanel — data rows', () => {
  it('shouldDisplayRankedClientsInOrder', () => {
    render(<ActiveClientsPanel clients={threeClients} error={false} />)
    const names = screen.getAllByText(/John Smith|Sarah Johnson|Acme Corp/)
    expect(names[0].textContent).toBe('John Smith')
    expect(names[1].textContent).toBe('Sarah Johnson')
    expect(names[2].textContent).toBe('Acme Corp')
  })

  it('shouldFormatTurnoverMillionsAsCompactString', () => {
    render(<ActiveClientsPanel clients={[makeClient({ turnoverToday: '1250000.00' })]} error={false} />)
    expect(screen.getByText('$1.3M')).toBeInTheDocument()
  })

  it('shouldFormatTurnoverThousandsAsCompactString', () => {
    render(<ActiveClientsPanel clients={[makeClient({ turnoverToday: '450000.00', availableCash: '75000.00' })]} error={false} />)
    expect(screen.getByText('$450.0K')).toBeInTheDocument()
  })

  it('shouldFormatSmallTurnoverAsDecimalString', () => {
    render(<ActiveClientsPanel clients={[makeClient({ turnoverToday: '500.00' })]} error={false} />)
    expect(screen.getByText('$500.00')).toBeInTheDocument()
  })

  it('shouldShowLowCashPillWhenCashLimitWarningTrue', () => {
    render(<ActiveClientsPanel clients={[makeClient({ cashLimitWarning: true })]} error={false} />)
    expect(screen.getByText('⚠ Low Cash')).toBeInTheDocument()
  })

  it('shouldShowOkPillWhenCashLimitWarningFalse', () => {
    render(<ActiveClientsPanel clients={[makeClient({ cashLimitWarning: false })]} error={false} />)
    expect(screen.getByText('OK')).toBeInTheDocument()
  })

  it('shouldShowBothPillTypesWhenMixedWarnings', () => {
    render(<ActiveClientsPanel clients={threeClients} error={false} />)
    expect(screen.getByText('⚠ Low Cash')).toBeInTheDocument()
    expect(screen.getAllByText('OK').length).toBe(2)
  })
})

describe('ActiveClientsPanel — empty state', () => {
  it('shouldShowEmptyStateWhenNoClients', () => {
    render(<ActiveClientsPanel clients={[]} error={false} />)
    expect(screen.getByText('No active clients today')).toBeInTheDocument()
  })
})

describe('ActiveClientsPanel — error state', () => {
  it('shouldShowErrorMessageWhenErrorTrue', () => {
    render(<ActiveClientsPanel clients={null} error={true} />)
    expect(screen.getByText('Unable to load active clients')).toBeInTheDocument()
  })
})

describe('ActiveClientsPanel — loading state', () => {
  it('shouldRenderSkeletonRowsWhenClientsIsNull', () => {
    const { container } = render(<ActiveClientsPanel clients={null} error={false} />)
    const skeletons = container.querySelectorAll('.skeleton')
    expect(skeletons.length).toBeGreaterThanOrEqual(3)
  })
})
