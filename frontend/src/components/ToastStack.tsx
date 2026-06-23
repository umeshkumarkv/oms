import React from 'react'
import Toast from './Toast'
import type { ToastEntry } from '../hooks/useToast'

const ToastStack: React.FC<{ toasts: ToastEntry[] }> = ({ toasts }) => {
  if (toasts.length === 0) return null
  return (
    <div style={{
      position: 'fixed',
      top: '20px',
      right: '20px',
      zIndex: 9999,
      display: 'flex',
      flexDirection: 'column',
      gap: '8px',
    }}>
      {toasts.map(t => (
        <Toast key={t.id} message={t.message} type={t.type} />
      ))}
    </div>
  )
}

export default ToastStack
