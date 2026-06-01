import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import PendingApprovalsPanel from '../components/PendingApprovalsPanel'
import * as riskApi from '../api/riskApi'
import * as apiClient from '../api/apiClient'
import { PendingChange } from '../types/risk.types'

// ── Mocks ──────────────────────────────────────────────────────────────────────

vi.mock('../api/riskApi')
vi.mock('../api/apiClient')

const mockUser = { userId: 'user-checker', username: 'admin01', role: 'ADMIN' as const }

const makePendingChange = (overrides: Partial<PendingChange> = {}): PendingChange => ({
  id: 'change-1',
  ruleCode: 'ORDER_VALUE_LIMIT',
  changeType: 'DISABLE',
  proposedEnabled: false,
  proposedThreshold: null,
  makerUserId: 'user-maker',
  makerUsername: 'trader01',
  status: 'PENDING',
  makerNote: 'Disabling for maintenance',
  rejectionReason: null,
  checkerUserId: null,
  submittedAt: '2026-05-20T10:00:00Z',
  reviewedAt: null,
  ...overrides,
})

function renderPanel(props: { onChangeReviewed?: () => void } = {}) {
  return render(<PendingApprovalsPanel {...props} />)
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(apiClient.getCurrentUser).mockReturnValue(mockUser as never)
  vi.mocked(riskApi.getPendingChanges).mockResolvedValue([])
  vi.mocked(riskApi.approveChange).mockResolvedValue(makePendingChange({ status: 'APPROVED' }) as never)
  vi.mocked(riskApi.rejectChange).mockResolvedValue(makePendingChange({ status: 'REJECTED' }) as never)
})

// ── Tests ──────────────────────────────────────────────────────────────────────

describe('PendingApprovalsPanel — loading and empty states', () => {

  it('shouldRenderLoadingStateWhileFetchingChanges', () => {
    vi.mocked(riskApi.getPendingChanges).mockImplementation(() => new Promise(() => {}))
    renderPanel()
    expect(screen.getByText('Loading pending changes…')).toBeTruthy()
  })

  it('shouldRenderEmptyStateWhenNoChangesExist', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([])
    renderPanel()
    expect(await screen.findByText('No pending changes found.')).toBeTruthy()
  })

  it('shouldRenderPanelTitle', async () => {
    renderPanel()
    expect(await screen.findByText('Pending Approvals')).toBeTruthy()
  })

})

describe('PendingApprovalsPanel — rendering pending changes', () => {

  it('shouldRenderRuleCodeAndStatusBadge', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([makePendingChange()])
    renderPanel()
    expect(await screen.findByText('ORDER_VALUE_LIMIT')).toBeTruthy()
    expect(screen.getByText('Pending')).toBeTruthy()
  })

  it('shouldRenderMakerUsernameInRow', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([makePendingChange()])
    renderPanel()
    expect(await screen.findByText('trader01')).toBeTruthy()
  })

  it('shouldRenderSelfChangeIndicatorWhenMakerIsCurrentUser', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([
      makePendingChange({ makerUserId: 'user-checker' }),
    ])
    renderPanel()
    expect(await screen.findByText('(you)')).toBeTruthy()
  })

  it('shouldRenderDisableFromAndToColumnsForDisableChangeType', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([
      makePendingChange({ changeType: 'DISABLE', proposedEnabled: false }),
    ])
    renderPanel()
    expect(await screen.findByText('Enabled')).toBeTruthy()   // From column
    expect(screen.getByText('Disabled')).toBeTruthy()          // To column
  })

  it('shouldRenderEnableFromAndToColumnsForEnableChangeType', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([
      makePendingChange({ changeType: 'ENABLE', proposedEnabled: true }),
    ])
    renderPanel()
    expect(await screen.findByText('Disabled')).toBeTruthy()  // From column
    expect(screen.getByText('Enabled')).toBeTruthy()           // To column
  })

  it('shouldRenderProposedThresholdInToColumnForThresholdChangeType', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([
      makePendingChange({ changeType: 'UPDATE_THRESHOLD', proposedThreshold: '750000' }),
    ])
    renderPanel()
    expect(await screen.findByText('750000')).toBeTruthy()     // To column
    expect(screen.getAllByText('—').length).toBeGreaterThan(0) // From column shows —
  })

  it('shouldRenderApprovedStatusBadgeForReviewedChange', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([
      makePendingChange({ status: 'APPROVED', reviewedAt: '2026-05-20T11:00:00Z', checkerUserId: 'user-checker' }),
    ])
    renderPanel()
    expect(await screen.findByText('Approved')).toBeTruthy()
  })

  it('shouldNotRenderApproveOrRejectButtonsForReviewedChange', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([
      makePendingChange({ status: 'APPROVED', reviewedAt: '2026-05-20T11:00:00Z', checkerUserId: 'user-checker' }),
    ])
    renderPanel()
    await screen.findByText('Approved')
    expect(screen.queryByText('Approve')).toBeNull()
    expect(screen.queryByText('Reject')).toBeNull()
  })

  it('shouldRenderRejectionReasonForRejectedChange', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([
      makePendingChange({ status: 'REJECTED', rejectionReason: 'Policy violation', reviewedAt: '2026-05-20T11:00:00Z' }),
    ])
    renderPanel()
    expect(await screen.findByText('Policy violation')).toBeTruthy()
  })

})

describe('PendingApprovalsPanel — approve flow', () => {

  it('shouldCallApproveChangeWhenApproveButtonClicked', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([makePendingChange()])
    renderPanel()
    const approveBtn = await screen.findByText('Approve')
    fireEvent.click(approveBtn)
    await waitFor(() => expect(riskApi.approveChange).toHaveBeenCalledWith('change-1'))
  })

  it('shouldShowSuccessToastAfterApproval', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([makePendingChange()])
    renderPanel()
    fireEvent.click(await screen.findByText('Approve'))
    expect(await screen.findByText('ORDER_VALUE_LIMIT change approved and applied')).toBeTruthy()
  })

  it('shouldCallOnChangeReviewedCallbackAfterApproval', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([makePendingChange()])
    const onChangeReviewed = vi.fn()
    renderPanel({ onChangeReviewed })
    fireEvent.click(await screen.findByText('Approve'))
    await waitFor(() => expect(onChangeReviewed).toHaveBeenCalledOnce())
  })

  it('shouldShowInlineRowErrorOnSelfApproval403', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([makePendingChange()])
    vi.mocked(riskApi.approveChange).mockRejectedValue({ response: { status: 403 } })
    renderPanel()
    fireEvent.click(await screen.findByText('Approve'))
    expect(await screen.findByText('You cannot approve your own change')).toBeTruthy()
  })

  it('shouldShowInlineRowErrorOnAlreadyReviewed409', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([makePendingChange()])
    vi.mocked(riskApi.approveChange).mockRejectedValue({ response: { status: 409 } })
    renderPanel()
    fireEvent.click(await screen.findByText('Approve'))
    expect(await screen.findByText('This change has already been reviewed')).toBeTruthy()
  })

  it('shouldShowGenericToastOnUnexpectedApproveError', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([makePendingChange()])
    vi.mocked(riskApi.approveChange).mockRejectedValue({ response: { status: 500 } })
    renderPanel()
    fireEvent.click(await screen.findByText('Approve'))
    expect(await screen.findByText('Failed to approve change — please try again')).toBeTruthy()
  })

})

describe('PendingApprovalsPanel — reject flow', () => {

  it('shouldOpenRejectModalWhenRejectButtonClicked', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([makePendingChange()])
    renderPanel()
    fireEvent.click(await screen.findByText('Reject'))
    expect(await screen.findByRole('heading', { name: 'Reject Change' })).toBeTruthy()
    expect(screen.getByPlaceholderText('Provide a mandatory reason for rejection')).toBeTruthy()
  })

  it('shouldKeepRejectButtonDisabledWhenReasonIsEmpty', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([makePendingChange()])
    renderPanel()
    fireEvent.click(await screen.findByText('Reject'))
    await screen.findByPlaceholderText('Provide a mandatory reason for rejection')
    const confirmBtn = screen.getByRole('button', { name: 'Reject Change' })
    expect((confirmBtn as HTMLButtonElement).disabled).toBe(true)
  })

  it('shouldCallRejectChangeWithReasonAfterConfirmation', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([makePendingChange()])
    renderPanel()
    fireEvent.click(await screen.findByText('Reject'))
    const textarea = await screen.findByPlaceholderText('Provide a mandatory reason for rejection')
    fireEvent.change(textarea, { target: { value: 'Not compliant with policy' } })
    fireEvent.click(screen.getByRole('button', { name: 'Reject Change' }))
    await waitFor(() =>
      expect(riskApi.rejectChange).toHaveBeenCalledWith('change-1', 'Not compliant with policy')
    )
  })

  it('shouldShowSuccessToastAfterRejection', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([makePendingChange()])
    renderPanel()
    fireEvent.click(await screen.findByText('Reject'))
    const textarea = await screen.findByPlaceholderText('Provide a mandatory reason for rejection')
    fireEvent.change(textarea, { target: { value: 'Policy violation' } })
    fireEvent.click(screen.getByRole('button', { name: 'Reject Change' }))
    expect(await screen.findByText('ORDER_VALUE_LIMIT change rejected')).toBeTruthy()
  })

  it('shouldDismissRejectModalWhenCancelClicked', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([makePendingChange()])
    renderPanel()
    fireEvent.click(await screen.findByText('Reject'))
    await screen.findByPlaceholderText('Provide a mandatory reason for rejection')
    fireEvent.click(screen.getByText('Cancel'))
    await waitFor(() =>
      expect(screen.queryByPlaceholderText('Provide a mandatory reason for rejection')).toBeNull()
    )
  })

  it('shouldShowInlineRowErrorOnAlreadyReviewed409ForReject', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([makePendingChange()])
    vi.mocked(riskApi.rejectChange).mockRejectedValue({ response: { status: 409 } })
    renderPanel()
    fireEvent.click(await screen.findByText('Reject'))
    const textarea = await screen.findByPlaceholderText('Provide a mandatory reason for rejection')
    fireEvent.change(textarea, { target: { value: 'Testing' } })
    fireEvent.click(screen.getByRole('button', { name: 'Reject Change' }))
    expect(await screen.findByText('This change has already been reviewed')).toBeTruthy()
  })

})

describe('PendingApprovalsPanel — refresh', () => {

  it('shouldReloadChangesWhenRefreshButtonClicked', async () => {
    vi.mocked(riskApi.getPendingChanges).mockResolvedValue([])
    renderPanel()
    await screen.findByText('No pending changes found.')
    fireEvent.click(screen.getByText('↻ Refresh'))
    await waitFor(() => expect(riskApi.getPendingChanges).toHaveBeenCalledTimes(2))
  })

})
