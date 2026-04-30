import React, { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { login as apiLogin } from '../api/authApi'
import { useAuth } from '../context/AuthContext'

const DEMO_ACCOUNTS = ['trader01', 'trader02', 'viewer01', 'admin01']

const LoginPage: React.FC = () => {
  const navigate = useNavigate()
  const location = useLocation()
  const { login, user, clearSessionExpired, sessionExpired } = useAuth()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [inlineError, setInlineError] = useState<string | null>(null)
  const [networkError, setNetworkError] = useState<string | null>(null)

  useEffect(() => {
    if (user) navigate('/dashboard', { replace: true })
  }, [user, navigate])

  const routeSessionExpired = (location.state as { sessionExpired?: boolean } | null)?.sessionExpired
  const sessionExpiredMsg = sessionExpired || routeSessionExpired

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setInlineError(null)
    setNetworkError(null)
    setLoading(true)

    try {
      const data = await apiLogin(username, password)
      login({
        token: data.token,
        userId: data.userId,
        username,
        role: data.role,
        displayName: data.displayName,
      })
      navigate('/dashboard', { replace: true })
    } catch (err: unknown) {
      const axiosErr = err as { response?: { status?: number } }
      if (axiosErr?.response?.status === 401) {
        setInlineError('Invalid username or password')
      } else {
        setNetworkError('Unable to connect to the server. Please try again.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={styles.page}>
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
      <div style={styles.card}>
        <div style={styles.logo}>
          <span style={styles.logoIcon}>📈</span>
          <span style={styles.logoText}>Broker OMS</span>
        </div>

        <h1 style={styles.heading}>Sign in to your account</h1>

        {sessionExpiredMsg && (
          <div style={{ ...styles.banner, ...styles.bannerWarning }} role="alert">
            Session expired — please sign in again
            <button style={styles.bannerClose} onClick={clearSessionExpired} aria-label="Dismiss">✕</button>
          </div>
        )}

        {networkError && (
          <div style={{ ...styles.banner, ...styles.bannerError }} role="alert">
            {networkError}
            <button style={styles.bannerClose} onClick={() => setNetworkError(null)} aria-label="Dismiss">✕</button>
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate>
          <div style={styles.field}>
            <label htmlFor="username" style={styles.label}>Username</label>
            <input
              id="username"
              type="text"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              disabled={loading}
              required
              style={styles.input}
              placeholder="e.g. trader01"
            />
          </div>

          <div style={styles.field}>
            <label htmlFor="password" style={styles.label}>Password</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={loading}
              required
              style={styles.input}
              placeholder="••••••••"
            />
          </div>

          {inlineError && (
            <p style={styles.inlineError} role="alert">{inlineError}</p>
          )}

          <button
            type="submit"
            disabled={loading || !username || !password}
            style={{ ...styles.button, ...(loading || !username || !password ? styles.buttonDisabled : {}) }}
          >
            {loading ? (
              <span style={styles.spinnerWrap} aria-label="Signing in">
                <span style={styles.spinner} aria-hidden="true" />
                Signing in…
              </span>
            ) : (
              'Sign in'
            )}
          </button>
        </form>

        <p style={styles.footer}>
          Demo accounts —{' '}
          {DEMO_ACCOUNTS.map((a, i) => (
            <React.Fragment key={a}>
              <button style={styles.demoBtn} type="button" onClick={() => setUsername(a)} tabIndex={-1}>
                {a}
              </button>
              {i < DEMO_ACCOUNTS.length - 1 && <span style={styles.separator}> · </span>}
            </React.Fragment>
          ))}
          <span style={styles.demoPassword}>&nbsp;· password: Demo@1234</span>
        </p>
      </div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  page: {
    minHeight: '100vh',
    background: '#f5f6fa',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '24px',
  },
  card: {
    background: '#fff',
    borderRadius: '12px',
    boxShadow: '0 4px 24px rgba(0,0,0,0.08)',
    padding: '40px 36px',
    width: '100%',
    maxWidth: '400px',
  },
  logo: { display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '24px' },
  logoIcon: { fontSize: '24px' },
  logoText: { fontSize: '20px', fontWeight: 700, color: '#1a2236', letterSpacing: '-0.02em' },
  heading: { fontSize: '18px', fontWeight: 600, color: '#111827', margin: '0 0 24px 0' },
  banner: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '10px 14px',
    borderRadius: '8px',
    marginBottom: '16px',
    fontSize: '14px',
    fontWeight: 500,
  },
  bannerWarning: { background: '#fef3c7', color: '#92400e', border: '1px solid #fcd34d' },
  bannerError: { background: '#fee2e2', color: '#991b1b', border: '1px solid #fca5a5' },
  bannerClose: {
    background: 'none', border: 'none', cursor: 'pointer',
    fontSize: '14px', color: 'inherit', padding: '0 0 0 8px', opacity: 0.7,
  },
  field: { marginBottom: '16px' },
  label: { display: 'block', fontSize: '14px', fontWeight: 500, color: '#374151', marginBottom: '6px' },
  input: {
    width: '100%', padding: '9px 12px', border: '1px solid #d1d5db',
    borderRadius: '8px', fontSize: '14px', color: '#111827', background: '#fff',
    outline: 'none', boxSizing: 'border-box',
  },
  inlineError: { color: '#dc2626', fontSize: '13px', margin: '-8px 0 12px 0', fontWeight: 500 },
  button: {
    width: '100%', padding: '10px', marginTop: '4px',
    background: '#6366f1', color: '#fff', border: 'none', borderRadius: '8px',
    fontSize: '15px', fontWeight: 600, cursor: 'pointer',
  },
  buttonDisabled: { background: '#a5b4fc', cursor: 'not-allowed' },
  spinnerWrap: { display: 'inline-flex', alignItems: 'center', gap: '8px' },
  spinner: {
    display: 'inline-block', width: '14px', height: '14px',
    border: '2px solid rgba(255,255,255,0.4)', borderTopColor: '#fff',
    borderRadius: '50%', animation: 'spin 0.7s linear infinite',
  },
  footer: {
    marginTop: '24px', paddingTop: '16px', borderTop: '1px solid #f3f4f6',
    fontSize: '12px', color: '#6b7280', textAlign: 'center', lineHeight: 1.8,
  },
  demoBtn: {
    background: 'none', border: 'none', padding: 0, fontSize: '12px',
    color: '#6366f1', cursor: 'pointer', fontFamily: 'inherit',
    textDecoration: 'underline', textDecorationStyle: 'dotted',
  },
  separator: { color: '#d1d5db' },
  demoPassword: { color: '#9ca3af' },
}

export default LoginPage
