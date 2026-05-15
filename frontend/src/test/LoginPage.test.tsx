import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import LoginPage from '../pages/LoginPage'
import { AuthProvider } from '../context/AuthContext'
import * as authApi from '../api/authApi'

// Mock react-router-dom navigate
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

// Mock authApi login
vi.mock('../api/authApi')

const renderLoginPage = () =>
  render(
    <MemoryRouter>
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    </MemoryRouter>
  )

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders username input, password input, and sign in button', () => {
    renderLoginPage()
    expect(screen.getByLabelText(/username/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
  })

  it('renders demo accounts footer', () => {
    renderLoginPage()
    expect(screen.getByText(/demo accounts/i)).toBeInTheDocument()
    expect(screen.getByText('trader.john')).toBeInTheDocument()
    expect(screen.getByText('trader.sarah')).toBeInTheDocument()
    expect(screen.getByText('ops.manager')).toBeInTheDocument()
    expect(screen.getByText('risk.manager')).toBeInTheDocument()
  })

  it('sign in button is disabled when fields are empty', () => {
    renderLoginPage()
    expect(screen.getByRole('button', { name: /sign in/i })).toBeDisabled()
  })

  it('redirects to /dashboard on successful login', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      token: 'test-token',
      expiresAt: '2026-12-31T00:00:00Z',
      role: 'TRADER',
      userId: '00000000-0000-0000-0000-000000000001',
      displayName: 'trader01',
    })

    renderLoginPage()
    await userEvent.type(screen.getByLabelText(/username/i), 'trader01')
    await userEvent.type(screen.getByLabelText(/password/i), 'Demo@1234')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/dashboard', { replace: true }))
  })

  it('shows loading state while request is in flight', async () => {
    let resolveLogin!: (v: authApi.LoginResponse) => void
    vi.mocked(authApi.login).mockReturnValue(
      new Promise<authApi.LoginResponse>((r) => { resolveLogin = r })
    )

    renderLoginPage()
    await userEvent.type(screen.getByLabelText(/username/i), 'trader01')
    await userEvent.type(screen.getByLabelText(/password/i), 'Demo@1234')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))

    expect(screen.getByLabelText('Signing in')).toBeInTheDocument()
    expect(screen.getByLabelText(/username/i)).toBeDisabled()

    // Clean up
    resolveLogin({ token: 't', expiresAt: '', role: 'TRADER', userId: 'u', displayName: 'd' })
  })

  it('shows inline error on 401 response', async () => {
    vi.mocked(authApi.login).mockRejectedValue({ response: { status: 401 } })

    renderLoginPage()
    await userEvent.type(screen.getByLabelText(/username/i), 'bad')
    await userEvent.type(screen.getByLabelText(/password/i), 'bad')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() =>
      expect(screen.getByText('Invalid username or password')).toBeInTheDocument()
    )
  })

  it('shows network error banner on non-401 failure', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new Error('Network Error'))

    renderLoginPage()
    await userEvent.type(screen.getByLabelText(/username/i), 'trader01')
    await userEvent.type(screen.getByLabelText(/password/i), 'Demo@1234')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() =>
      expect(screen.getByText(/unable to connect/i)).toBeInTheDocument()
    )
  })

  it('password field is masked (type=password)', () => {
    renderLoginPage()
    const passwordInput = screen.getByLabelText(/password/i)
    expect(passwordInput).toHaveAttribute('type', 'password')
  })

  it('clicking demo account button fills in username', async () => {
    renderLoginPage()
    await userEvent.click(screen.getByText('trader.john'))
    expect(screen.getByLabelText(/username/i)).toHaveValue('trader.john')
  })
})
