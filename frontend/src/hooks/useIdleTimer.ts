import { useCallback, useEffect, useRef, useState } from 'react'

interface IdleTimerResult {
  secondsRemaining: number
  isExpired: boolean
  resetTimer: () => void
}

export function useIdleTimer(timeoutSeconds: number = 60): IdleTimerResult {
  const [secondsRemaining, setSecondsRemaining] = useState(timeoutSeconds)
  const [isExpired, setIsExpired] = useState(false)

  // useRef avoids stale closure inside setInterval
  const counterRef  = useRef(timeoutSeconds)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const startInterval = useCallback(() => {
    if (intervalRef.current) clearInterval(intervalRef.current)
    intervalRef.current = setInterval(() => {
      counterRef.current -= 1
      if (counterRef.current <= 0) {
        clearInterval(intervalRef.current!)
        intervalRef.current = null
        setSecondsRemaining(0)
        setIsExpired(true)
      } else {
        setSecondsRemaining(counterRef.current)
      }
    }, 1000)
  }, [])

  const resetTimer = useCallback(() => {
    counterRef.current = timeoutSeconds
    setSecondsRemaining(timeoutSeconds)
    setIsExpired(false)
    startInterval()
  }, [timeoutSeconds, startInterval])

  useEffect(() => {
    startInterval()
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current)
    }
  }, [startInterval])

  return { secondsRemaining, isExpired, resetTimer }
}
