import { useState, useCallback } from 'react'
import type { ToastType } from '../components/Toast'
import { TOAST_DISMISS_MS } from '../utils/constants'

export interface ToastEntry {
  id: string
  message: string
  type: ToastType
}

export function useToast() {
  const [toasts, setToasts] = useState<ToastEntry[]>([])

  const addToast = useCallback((message: string, type: ToastType, duration = TOAST_DISMISS_MS) => {
    const id = crypto.randomUUID()
    setToasts(prev => [...prev, { id, message, type }])
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), duration)
  }, [])

  return { toasts, addToast }
}
