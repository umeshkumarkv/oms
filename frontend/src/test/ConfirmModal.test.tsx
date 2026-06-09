import { render, screen, fireEvent } from '@testing-library/react'
import { vi, describe, it, expect } from 'vitest'
import ConfirmModal from '../components/ConfirmModal'

const baseProps = {
  title: 'Confirm Action',
  message: 'Are you sure you want to proceed?',
  confirmLabel: 'Confirm',
  onConfirm: vi.fn(),
  onCancel: vi.fn(),
}

describe('ConfirmModal', () => {

  it('shouldRenderTitleAndMessage', () => {
    render(<ConfirmModal {...baseProps} />)
    expect(screen.getByText('Confirm Action')).toBeTruthy()
    expect(screen.getByText('Are you sure you want to proceed?')).toBeTruthy()
  })

  it('shouldRenderConfirmAndCancelButtons', () => {
    render(<ConfirmModal {...baseProps} />)
    expect(screen.getByText('Confirm')).toBeTruthy()
    expect(screen.getByText('Cancel')).toBeTruthy()
  })

  it('shouldCallOnConfirmWhenConfirmButtonClicked', () => {
    const onConfirm = vi.fn()
    render(<ConfirmModal {...baseProps} onConfirm={onConfirm} />)
    fireEvent.click(screen.getByText('Confirm'))
    expect(onConfirm).toHaveBeenCalledOnce()
  })

  it('shouldCallOnCancelWhenCancelButtonClicked', () => {
    const onCancel = vi.fn()
    render(<ConfirmModal {...baseProps} onCancel={onCancel} />)
    fireEvent.click(screen.getByText('Cancel'))
    expect(onCancel).toHaveBeenCalledOnce()
  })

  it('shouldCallOnCancelWhenBackdropClicked', () => {
    const onCancel = vi.fn()
    const { container } = render(<ConfirmModal {...baseProps} onCancel={onCancel} />)
    fireEvent.click(container.firstChild as Element)
    expect(onCancel).toHaveBeenCalledOnce()
  })

  it('shouldNotPropagateClickFromModalBody', () => {
    const onCancel = vi.fn()
    render(<ConfirmModal {...baseProps} onCancel={onCancel} />)
    fireEvent.click(screen.getByText('Confirm Action'))
    expect(onCancel).not.toHaveBeenCalled()
  })

  it('shouldApplyErrorColorToTitleWhenDanger', () => {
    render(<ConfirmModal {...baseProps} danger />)
    const title = screen.getByText('Confirm Action')
    expect(title.style.color).toBe('var(--color-error)')
  })

  it('shouldApplyPrimaryColorToTitleWhenNotDanger', () => {
    render(<ConfirmModal {...baseProps} />)
    const title = screen.getByText('Confirm Action')
    expect(title.style.color).toBe('var(--color-text-primary)')
  })

})
