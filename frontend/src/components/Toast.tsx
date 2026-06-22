import React from 'react'

export const TOAST_TYPE = {
  SUCCESS: 'success',
  WARNING: 'warning',
  ERROR:   'error',
} as const

export type ToastType = typeof TOAST_TYPE[keyof typeof TOAST_TYPE]

interface ToastProps {
  message: string
  type: ToastType
}

const BG: Record<ToastType, string> = {
  [TOAST_TYPE.SUCCESS]: 'var(--color-nav)',
  [TOAST_TYPE.WARNING]: 'var(--color-warning)',
  [TOAST_TYPE.ERROR]:   'var(--color-error)',
}

const Toast: React.FC<ToastProps> = ({ message, type }) => (
  <div style={{
    background: BG[type],
    color: '#fff',
    padding: '14px 20px',
    borderRadius: 'var(--radius-lg)',
    boxShadow: 'var(--shadow-lg)',
    fontSize: '14px',
    fontWeight: 500,
    maxWidth: '420px',
  }}>
    {message}
  </div>
)

export default Toast
