import React from 'react'

export interface ConfirmModalProps {
  title: string
  message: string
  confirmLabel: string
  danger?: boolean
  onConfirm: () => void
  onCancel: () => void
}

const ConfirmModal: React.FC<ConfirmModalProps> = ({
  title, message, confirmLabel, danger, onConfirm, onCancel,
}) => (
  <div
    style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 500,
    }}
    onClick={onCancel}
  >
    <div
      style={{
        background: 'var(--color-surface)',
        borderRadius: 'var(--radius-lg)',
        padding: '24px',
        width: '440px', maxWidth: '90vw',
        boxShadow: 'var(--shadow-lg)',
      }}
      onClick={e => e.stopPropagation()}
    >
      <h3 style={{
        margin: '0 0 6px', fontSize: '16px', fontWeight: 700,
        color: danger ? 'var(--color-error)' : 'var(--color-text-primary)',
      }}>
        {title}
      </h3>
      <p style={{
        fontSize: '13px', color: 'var(--color-text-secondary)',
        lineHeight: 1.6, margin: '0 0 4px',
      }}>
        {message}
      </p>
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
        <button
          style={{
            padding: '7px 14px', borderRadius: 'var(--radius-md)',
            fontSize: '13px', fontWeight: 600, cursor: 'pointer',
            background: 'var(--color-surface)', color: 'var(--color-text-primary)',
            border: '1px solid var(--color-border)', transition: 'opacity 0.15s',
          }}
          onClick={onCancel}
        >
          Cancel
        </button>
        <button
          style={{
            padding: '7px 14px', borderRadius: 'var(--radius-md)',
            fontSize: '13px', fontWeight: 600, cursor: 'pointer',
            border: 'none', transition: 'opacity 0.15s',
            background: danger ? 'var(--color-error)' : 'var(--color-primary)',
            color: '#fff',
          }}
          onClick={onConfirm}
        >
          {confirmLabel}
        </button>
      </div>
    </div>
  </div>
)

export default ConfirmModal
