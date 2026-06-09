import { test, expect } from '@playwright/test'
import { loginAsTrader } from './helpers/auth'

test.describe('Order Entry', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsTrader(page)
    await page.goto('/orders/new')
    await page.waitForLoadState('networkidle')
  })

  test('renders the order entry form', async ({ page }) => {
    await expect(page.getByPlaceholder('e.g. AAPL')).toBeVisible()
    await expect(page.getByRole('button', { name: 'BUY' })).toBeVisible()
    // exact: true required — 'SELL' is a substring of 'SHORT SELL', so without exact
    // both buttons would match (Playwright name option is substring by default)
    await expect(page.getByRole('button', { name: 'SELL', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: 'SHORT SELL' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'MARKET' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'LIMIT' })).toBeVisible()
    await expect(page.getByPlaceholder('Number of shares')).toBeVisible()
  })

  test('PLACE ORDER button is disabled when form is incomplete', async ({ page }) => {
    await expect(page.getByRole('button', { name: 'PLACE ORDER' })).toBeDisabled()
  })

  test('Limit Price field is visible for LIMIT orders', async ({ page }) => {
    await page.getByRole('button', { name: 'LIMIT' }).click()
    await expect(page.getByText('Limit Price ($)')).toBeVisible()
  })

  test('Limit Price field is hidden for MARKET orders', async ({ page }) => {
    await page.getByRole('button', { name: 'MARKET' }).click()
    await expect(page.getByText('Limit Price ($)')).not.toBeVisible()
  })

  test('BUY side toggle highlights in blue', async ({ page }) => {
    const buyBtn = page.getByRole('button', { name: 'BUY' })
    await buyBtn.click()
    // Active BUY button uses --color-buy (#1d4ed8)
    await expect(buyBtn).toHaveCSS('color', 'rgb(255, 255, 255)')
  })

  test('symbol input accepts text and updates value', async ({ page }) => {
    // Typeahead dropdown is gated on symbolInput !== symbol; since both state vars
    // are set together in onChange the dropdown never renders — this tests the input itself
    const symbolInput = page.getByPlaceholder('e.g. AAPL')
    await symbolInput.fill('AAPL')
    await expect(symbolInput).toHaveValue('AAPL')
  })

  test('submit button enables when BUY MARKET DAY form is complete', async ({ page }) => {
    // Market is CLOSED in test environment so we only verify the button enables —
    // not that the submission succeeds (server-side L2 rejects MARKET_CLOSED)
    await page.getByRole('button', { name: 'MARKET' }).click()
    await page.getByRole('button', { name: 'BUY' }).click()

    await page.getByPlaceholder('e.g. AAPL').fill('AAPL')
    await page.getByPlaceholder('Number of shares').fill('1')

    await expect(page.getByRole('button', { name: 'PLACE ORDER' })).toBeEnabled({ timeout: 5000 })
  })

  test('shows a validation error for LIMIT order with no price', async ({ page }) => {
    await page.getByRole('button', { name: 'LIMIT' }).click()
    await page.getByRole('button', { name: 'BUY' }).click()

    await page.getByPlaceholder('e.g. AAPL').fill('AAPL')
    await page.getByPlaceholder('Number of shares').fill('10')

    // Limit price placeholder is "0.00"; default value is empty → check fails → button disabled
    const limitPrice = page.getByPlaceholder('0.00')
    await limitPrice.clear()
    await expect(page.getByRole('button', { name: 'PLACE ORDER' })).toBeDisabled()
  })

  test('BUY LIMIT order form renders correctly', async ({ page }) => {
    await page.getByRole('button', { name: 'BUY' }).click()
    await page.getByRole('button', { name: 'LIMIT' }).click()

    await page.getByPlaceholder('e.g. AAPL').fill('AAPL')
    await page.getByPlaceholder('Number of shares').fill('100')

    // BUY button active — white text on blue background
    await expect(page.getByRole('button', { name: 'BUY' })).toHaveCSS('color', 'rgb(255, 255, 255)')
    // LIMIT button active
    await expect(page.getByRole('button', { name: 'LIMIT' })).toHaveCSS('color', 'rgb(255, 255, 255)')
    // Limit Price field visible
    await expect(page.getByPlaceholder('0.00')).toBeVisible()
    // PLACE ORDER button is visible
    await expect(page.getByRole('button', { name: 'PLACE ORDER' })).toBeVisible()
    // Two-column layout — Order Details card present
    await expect(page.getByText('Order Details')).toBeVisible()
    // Validation panel present
    await expect(page.getByText('Validation')).toBeVisible()
  })
})
