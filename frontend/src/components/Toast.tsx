import React from 'react'

export type ToastType = 'success' | 'warning' | 'error'

interface ToastProps {
  message: string
  type: ToastType
}

const BG: Record<ToastType, string> = {
  success: 'var(--color-nav)',
  warning: 'var(--color-warning)',
  error:   'var(--color-error)',
}

const Toast: React.FC<ToastProps> = ({ message, type }) => (
  <div style={{
    position: 'fixed',
    top: '20px',
    right: '20px',
    background: BG[type],
    color: '#fff',
    padding: '14px 20px',
    borderRadius: 'var(--radius-lg)',
    boxShadow: 'var(--shadow-lg)',
    zIndex: 9999,
    fontSize: '14px',
    fontWeight: 500,
  }}>
    {message}
  </div>
)

export default Toast
