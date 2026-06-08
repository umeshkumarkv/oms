import React, { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { CheckCircle, Eye, EyeOff, TrendingUp } from 'lucide-react'
import { login as apiLogin } from '../api/authApi'
import { useAuth } from '../context/AuthContext'

const DEMO_ACCOUNTS = [
  { username: 'trader.john',  role: 'TRADER'     },
  { username: 'trader.sarah', role: 'TRADER'     },
  { username: 'ops.manager',  role: 'OPERATIONS' },
  { username: 'risk.manager', role: 'ADMIN'      },
]

const LoginPage: React.FC = () => {
  const navigate = useNavigate()
  const location = useLocation()
  const { login, user, clearSessionExpired, sessionExpired } = useAuth()

  const [username,     setUsername]     = useState('')
  const [password,     setPassword]     = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [loading,      setLoading]      = useState(false)
  const [inlineError,  setInlineError]  = useState<string | null>(null)
  const [networkError, setNetworkError] = useState<string | null>(null)

  const routeSessionExpired = (location.state as { sessionExpired?: boolean } | null)?.sessionExpired
  const [bannerVisible, setBannerVisible] = useState(() => !!(sessionExpired || routeSessionExpired))

  useEffect(() => {
    if (user) navigate('/dashboard', { replace: true })
  }, [user, navigate])

  useEffect(() => {
    if (!bannerVisible) return
    const timer = setTimeout(() => {
      setBannerVisible(false)
      clearSessionExpired()
    }, 10_000)
    return () => clearTimeout(timer)
  }, [bannerVisible, clearSessionExpired])

  const handleDismissBanner = () => {
    setBannerVisible(false)
    clearSessionExpired()
  }

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
      setBannerVisible(false)
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
    <div style={{ minHeight: '100vh', display: 'flex' }}>
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>

      {/* ── Left branding panel (desktop only) ── */}
      <div
        className="login-left-panel"
        style={{
          flex: '0 0 420px',
          background: 'var(--color-nav)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '48px 40px',
          gap: '36px',
        }}
      >
        {/* Logo mark */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '18px' }}>
          <div style={{
            width: '80px', height: '80px',
            borderRadius: '22px',
            background: 'var(--color-primary)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: '0 0 0 16px rgba(99,102,241,0.12)',
          }}>
            <TrendingUp size={40} strokeWidth={2} color="#fff" />
          </div>
          <div style={{ textAlign: 'center' }}>
            <div style={{
              fontSize: '26px', fontWeight: 700, color: '#fff',
              letterSpacing: '-0.03em', lineHeight: 1.1,
            }}>
              Broker OMS
            </div>
            <div style={{ fontSize: '13px', color: 'rgba(255,255,255,0.4)', marginTop: '8px' }}>
              Institutional-grade order execution
            </div>
          </div>
        </div>

        {/* Feature bullets */}
        <div style={{
          width: '100%', maxWidth: '280px',
          display: 'flex', flexDirection: 'column', gap: '14px',
        }}>
          {[
            'Real-time market data & pricing',
            'Two-level compliance validation',
            'Immutable full audit trail',
          ].map(text => (
            <div key={text} style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <CheckCircle size={15} color="var(--color-primary)" strokeWidth={2} style={{ flexShrink: 0 }} />
              <span style={{ fontSize: '13px', color: 'rgba(255,255,255,0.55)', lineHeight: 1.4 }}>
                {text}
              </span>
            </div>
          ))}
        </div>

        {/* Footer note */}
        <div style={{
          fontSize: '11px',
          color: 'rgba(255,255,255,0.18)',
          textAlign: 'center',
          lineHeight: 1.6,
        }}>
          POC · US Equities Only<br />Simulated Exchange
        </div>
      </div>

      {/* ── Right form panel ── */}
      <div style={{
        flex: 1,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        background: 'var(--color-bg)',
        padding: '40px 24px',
      }}>
        <div style={{ width: '100%', maxWidth: '380px' }}>

          {/* Mobile-only compact logo */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '9px', marginBottom: '36px' }}>
            <div style={{
              width: '32px', height: '32px',
              borderRadius: 'var(--radius-md)',
              background: 'var(--color-primary)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <TrendingUp size={18} strokeWidth={2.5} color="#fff" />
            </div>
            <span style={{ fontWeight: 700, fontSize: '16px', color: 'var(--color-text-primary)' }}>
              Broker OMS
            </span>
          </div>

          <h1 style={{
            fontSize: '22px', fontWeight: 700,
            color: 'var(--color-text-primary)',
            letterSpacing: '-0.02em',
            marginBottom: '6px',
          }}>
            Sign in
          </h1>
          <p style={{ fontSize: '14px', color: 'var(--color-text-secondary)', marginBottom: '28px' }}>
            Access your trading dashboard
          </p>

          {/* Session expired banner */}
          {bannerVisible && (
            <div style={{
              display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              padding: '10px 14px',
              borderRadius: 'var(--radius-md)',
              background: 'var(--color-warning-bg)',
              border: '1px solid #fcd34d',
              color: '#92400e',
              fontSize: '13px',
              marginBottom: '16px',
            }} role="alert">
              Session expired — please sign in again
              <button
                onClick={handleDismissBanner}
                style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '14px', color: 'inherit', opacity: 0.7 }}
                aria-label="Dismiss"
              >✕</button>
            </div>
          )}

          {/* Network error banner */}
          {networkError && (
            <div style={{
              display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              padding: '10px 14px',
              borderRadius: 'var(--radius-md)',
              background: 'var(--color-error-bg)',
              border: '1px solid #fca5a5',
              color: '#991b1b',
              fontSize: '13px',
              marginBottom: '16px',
            }} role="alert">
              {networkError}
              <button
                onClick={() => setNetworkError(null)}
                style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '14px', color: 'inherit', opacity: 0.7 }}
                aria-label="Dismiss"
              >✕</button>
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate>
            {/* Username */}
            <div style={{ marginBottom: '16px' }}>
              <label
                htmlFor="username"
                style={{ display: 'block', fontSize: '13px', fontWeight: 500, color: 'var(--color-text-secondary)', marginBottom: '6px' }}
              >
                Username
              </label>
              <input
                id="username"
                className="login-input"
                type="text"
                autoComplete="username"
                value={username}
                onChange={e => setUsername(e.target.value)}
                disabled={loading}
                required
                placeholder="e.g. trader01"
                style={{
                  width: '100%', padding: '10px 12px',
                  border: '1.5px solid var(--color-border)',
                  borderRadius: 'var(--radius-md)',
                  fontSize: '14px', color: 'var(--color-text-primary)',
                  background: '#fff', boxSizing: 'border-box',
                  transition: 'border-color 0.15s',
                }}
              />
            </div>

            {/* Password */}
            <div style={{ marginBottom: '20px' }}>
              <label
                htmlFor="password"
                style={{ display: 'block', fontSize: '13px', fontWeight: 500, color: 'var(--color-text-secondary)', marginBottom: '6px' }}
              >
                Password
              </label>
              <div style={{ position: 'relative' }}>
                <input
                  id="password"
                  className="login-input"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  disabled={loading}
                  required
                  placeholder="••••••••"
                  style={{
                    width: '100%', padding: '10px 40px 10px 12px',
                    border: '1.5px solid var(--color-border)',
                    borderRadius: 'var(--radius-md)',
                    fontSize: '14px', color: 'var(--color-text-primary)',
                    background: '#fff', boxSizing: 'border-box',
                    transition: 'border-color 0.15s',
                  }}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(p => !p)}
                  style={{
                    position: 'absolute', right: '10px', top: '50%', transform: 'translateY(-50%)',
                    background: 'none', border: 'none', cursor: 'pointer',
                    color: 'var(--color-text-muted)',
                    display: 'flex', alignItems: 'center', padding: '2px',
                  }}
                  aria-label={showPassword ? 'Hide' : 'Reveal'}
                >
                  {showPassword ? <EyeOff size={16} strokeWidth={1.75} /> : <Eye size={16} strokeWidth={1.75} />}
                </button>
              </div>
            </div>

            {/* Inline error */}
            {inlineError && (
              <p style={{ color: 'var(--color-error)', fontSize: '13px', margin: '-12px 0 14px', fontWeight: 500 }} role="alert">
                {inlineError}
              </p>
            )}

            {/* Submit */}
            <button
              type="submit"
              disabled={loading || !username || !password}
              style={{
                width: '100%', padding: '11px', marginTop: '4px',
                background: loading || !username || !password
                  ? '#a5b4fc'
                  : 'linear-gradient(135deg, #6366f1, #4f46e5)',
                color: '#fff', border: 'none',
                borderRadius: 'var(--radius-md)',
                fontSize: '14px', fontWeight: 600,
                cursor: loading || !username || !password ? 'not-allowed' : 'pointer',
                transition: 'opacity 0.15s',
              }}
            >
              {loading ? (
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }} aria-label="Signing in">
                  <span style={{
                    display: 'inline-block', width: '14px', height: '14px',
                    border: '2px solid rgba(255,255,255,0.4)', borderTopColor: '#fff',
                    borderRadius: '50%', animation: 'spin 0.7s linear infinite',
                  }} aria-hidden="true" />
                  Signing in…
                </span>
              ) : 'Sign in'}
            </button>
          </form>

          {/* Demo accounts */}
          <div style={{
            marginTop: '28px', paddingTop: '20px',
            borderTop: '1px solid var(--color-border-light)',
          }}>
            <p style={{
              fontSize: '11px', fontWeight: 600,
              color: 'var(--color-text-muted)',
              textTransform: 'uppercase', letterSpacing: '0.05em',
              marginBottom: '10px',
            }}>
              Demo accounts
            </p>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginBottom: '10px' }}>
              {DEMO_ACCOUNTS.map(({ username: u, role }) => (
                <button
                  key={u}
                  type="button"
                  onClick={() => setUsername(u)}
                  tabIndex={-1}
                  title={role}
                  style={{
                    padding: '4px 11px',
                    borderRadius: 'var(--radius-pill)',
                    border: '1px solid var(--color-border)',
                    background: '#fff',
                    fontSize: '12px', fontWeight: 500,
                    color: 'var(--color-primary)',
                    cursor: 'pointer', fontFamily: 'inherit',
                    transition: 'background 0.15s, border-color 0.15s',
                  }}
                  onMouseEnter={e => {
                    const b = e.currentTarget as HTMLButtonElement
                    b.style.background = 'var(--color-primary-light)'
                    b.style.borderColor = 'var(--color-primary)'
                  }}
                  onMouseLeave={e => {
                    const b = e.currentTarget as HTMLButtonElement
                    b.style.background = '#fff'
                    b.style.borderColor = 'var(--color-border)'
                  }}
                >
                  {u}
                </button>
              ))}
            </div>
            <p style={{ fontSize: '12px', color: 'var(--color-text-muted)' }}>
              password: <code style={{ fontFamily: 'monospace', color: 'var(--color-text-secondary)' }}>Demo@1234</code>
            </p>
          </div>

        </div>
      </div>
    </div>
  )
}

export default LoginPage
