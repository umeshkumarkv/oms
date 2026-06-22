import { test, expect } from '@playwright/test'
import { loginAsTrader } from './helpers/auth'

test.describe('Login page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
  })

  test('renders the sign-in form', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
    await expect(page.getByLabel('Username')).toBeVisible()
    await expect(page.getByLabel('Password')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
  })

  test('submit button is disabled when fields are empty', async ({ page }) => {
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeDisabled()
  })

  test('submit button enables only when both fields are filled', async ({ page }) => {
    const btn = page.getByRole('button', { name: 'Sign in' })
    await expect(btn).toBeDisabled()

    await page.getByLabel('Username').fill('trader.john')
    await expect(btn).toBeDisabled()  // password still empty

    await page.getByLabel('Password').fill('Demo@1234')
    await expect(btn).toBeEnabled()
  })

  test('clicking a demo account chip fills the username field', async ({ page }) => {
    await page.getByRole('button', { name: 'trader.john' }).click()
    await expect(page.getByLabel('Username')).toHaveValue('trader.john')
  })

  test('all four demo account chips are visible', async ({ page }) => {
    await expect(page.getByRole('button', { name: 'trader.john' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'trader.sarah' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'ops.manager' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'risk.manager' })).toBeVisible()
  })

  test('password reveal toggle switches input type', async ({ page }) => {
    const passwordInput = page.getByLabel('Password')
    await expect(passwordInput).toHaveAttribute('type', 'password')

    await page.getByRole('button', { name: 'Reveal' }).click()
    await expect(passwordInput).toHaveAttribute('type', 'text')

    await page.getByRole('button', { name: 'Hide' }).click()
    await expect(passwordInput).toHaveAttribute('type', 'password')
  })

  test('shows the Broker OMS branding', async ({ page }) => {
    await expect(page.getByText('Broker OMS').first()).toBeVisible()
    await expect(page.getByText('Demo accounts')).toBeVisible()
    await expect(page.getByText('Demo@1234')).toBeVisible()
  })

  test('redirects to dashboard if already logged in', async ({ page }) => {
    // Must use a real JWT — a fake token causes the gateway to return 401,
    // which triggers the session-expired handler and immediately redirects back to /
    await loginAsTrader(page)
    await page.goto('/')
    await expect(page).toHaveURL(/\/dashboard/)
  })

  test('shows inline error for invalid credentials', async ({ page }) => {
    await page.getByLabel('Username').fill('trader.john')
    await page.getByLabel('Password').fill('WrongPassword!')
    await page.getByRole('button', { name: 'Sign in' }).click()
    // Auth service returns 401 for bad password → inline error rendered
    await expect(page.getByText('Invalid username or password')).toBeVisible({ timeout: 8000 })
  })

  test('successful login navigates to /dashboard', async ({ page }) => {
    // Fills the form and submits — depends on auth service being up
    await page.getByRole('button', { name: 'trader.john' }).click()
    await page.getByLabel('Password').fill('Demo@1234')
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page).toHaveURL(/\/dashboard|\/admin/, { timeout: 10000 })
  })
})
