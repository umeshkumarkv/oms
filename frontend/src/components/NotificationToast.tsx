import React, { useEffect } from 'react'
import { NotificationMessage, NotificationType } from '../types/notification.types'

const AUTO_DISMISS_MS = 5000

function borderColor(type: NotificationType): string {
  switch (type) {
    case 'EXECUTION_COMPLETED': return 'var(--color-success)'
    case 'RISK_RESULT':         return 'var(--color-error)'
    default:                    return 'var(--color-primary)'
  }
}

interface ToastItemProps {
  notification: NotificationMessage
  onDismiss: (id: string) => void
}

const ToastItem: React.FC<ToastItemProps> = ({ notification, onDismiss }) => {
  useEffect(() => {
    const timer = setTimeout(() => onDismiss(notification.id), AUTO_DISMISS_MS)
    return () => clearTimeout(timer)
  }, [notification.id, onDismiss])

  const s: React.CSSProperties = {
    display: 'flex',
    alignItems: 'flex-start',
    gap: 10,
    background: 'var(--color-surface)',
    border: '1px solid var(--color-border)',
    borderLeft: `4px solid ${borderColor(notification.type)}`,
    borderRadius: 'var(--radius-md)',
    boxShadow: 'var(--shadow-md)',
    padding: '10px 12px',
    minWidth: 280,
    maxWidth: 360,
  }

  return (
    <div style={s} className="toast-slide-in" role="alert" aria-live="polite">
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontWeight: 600, fontSize: 13, color: 'var(--color-text-primary)', marginBottom: 2 }}>
          {notification.title}
        </div>
        <div style={{ fontSize: 12, color: 'var(--color-text-secondary)', wordBreak: 'break-word' }}>
          {notification.message}
        </div>
      </div>
      <button
        onClick={() => onDismiss(notification.id)}
        aria-label="Dismiss notification"
        style={{
          background: 'none',
          border: 'none',
          cursor: 'pointer',
          color: 'var(--color-text-muted)',
          fontSize: 16,
          lineHeight: 1,
          padding: 0,
          flexShrink: 0,
        }}
      >
        ×
      </button>
    </div>
  )
}

interface NotificationToastProps {
  notifications: NotificationMessage[]
  onDismiss: (id: string) => void
}

const NotificationToast: React.FC<NotificationToastProps> = ({ notifications, onDismiss }) => {
  if (notifications.length === 0) return null

  return (
    <div
      style={{
        position: 'fixed',
        bottom: 24,
        right: 24,
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        zIndex: 9999,
      }}
    >
      {notifications.map(n => (
        <ToastItem key={n.id} notification={n} onDismiss={onDismiss} />
      ))}
    </div>
  )
}

export default NotificationToast
