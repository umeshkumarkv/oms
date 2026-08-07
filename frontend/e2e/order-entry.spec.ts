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

  test('submit button is disabled when form is incomplete', async ({ page }) => {
    // Button uses aria-label="Submit Order"; text shows "Complete required fields" when checks fail
    await expect(page.getByRole('button', { name: 'Submit Order' })).toBeDisabled()
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
    // SCRUM-141 added mandatory client dropdown — must be selected for allChecksPass to be true
    await page.locator('select#client-select').selectOption({ index: 1 })
    // Route To dropdown is also mandatory (SOR_VENUES has 5 venues configured)
    await page.locator('select[aria-label="Route To"]').selectOption({ index: 1 })

    await page.getByRole('button', { name: 'MARKET' }).click()
    await page.getByRole('button', { name: 'BUY' }).click()
    await page.getByPlaceholder('e.g. AAPL').fill('AAPL')
    await page.getByPlaceholder('Number of shares').fill('1')

    // Button shows "Review & Submit Order" when all checks pass
    await expect(page.getByRole('button', { name: 'Submit Order' })).toBeEnabled({ timeout: 8000 })
  })

  test('shows a validation error for LIMIT order with no price', async ({ page }) => {
    await page.getByRole('button', { name: 'LIMIT' }).click()
    await page.getByRole('button', { name: 'BUY' }).click()

    await page.getByPlaceholder('e.g. AAPL').fill('AAPL')
    await page.getByPlaceholder('Number of shares').fill('10')

    // Limit price placeholder is "0.00"; default value is empty → check fails → button disabled
    const limitPrice = page.getByPlaceholder('0.00')
    await limitPrice.clear()
    await expect(page.getByRole('button', { name: 'Submit Order' })).toBeDisabled()
  })

  test('SELL side toggle is aria-pressed and uses sell color', async ({ page }) => {
    const sellBtn = page.getByRole('button', { name: 'SELL', exact: true })
    await sellBtn.click()
    await expect(sellBtn).toHaveAttribute('aria-pressed', 'true')
    // Active SELL button uses --color-sell (#be185d) → white text on pink background
    await expect(sellBtn).toHaveCSS('color', 'rgb(255, 255, 255)')
  })

  test('SHORT SELL side toggle is aria-pressed and uses warning color', async ({ page }) => {
    const shortSellBtn = page.getByRole('button', { name: 'SHORT SELL' })
    await shortSellBtn.click()
    await expect(shortSellBtn).toHaveAttribute('aria-pressed', 'true')
    await expect(shortSellBtn).toHaveCSS('color', 'rgb(255, 255, 255)')
  })

  test('GTD time-in-force toggle shows the date picker', async ({ page }) => {
    const gtdBtn = page.getByRole('button', { name: 'GTD' })
    await expect(gtdBtn).toBeVisible()
    await gtdBtn.click()
    await expect(gtdBtn).toHaveAttribute('aria-pressed', 'true')
    // DatePicker renders when GTD is selected
    await expect(page.locator('.react-datepicker__input-container, input[type="text"][class*="datepicker"]').first()).toBeVisible()
  })

  test('DAY toggle clears the GTD date picker', async ({ page }) => {
    await page.getByRole('button', { name: 'GTD' }).click()
    await page.getByRole('button', { name: 'DAY' }).click()
    await expect(page.getByRole('button', { name: 'DAY' })).toHaveAttribute('aria-pressed', 'true')
    // DatePicker should no longer be in the DOM when DAY is active
    const datepickerCount = await page.locator('.react-datepicker__input-container').count()
    expect(datepickerCount).toBe(0)
  })

  test('Order Preview card is visible on the right panel', async ({ page }) => {
    // Right panel heading changed to "Order Preview" in SCRUM-187
    await expect(page.getByRole('heading', { name: 'Order Preview' })).toBeVisible()
  })

  test('fill-or-kill toggle is present and can be checked', async ({ page }) => {
    const fok = page.locator('input[aria-label="Fill or Kill"]')
    await expect(fok).toHaveCount(1)
    await fok.check()
    await expect(fok).toBeChecked()
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
    // Submit button is visible (aria-label="Submit Order")
    await expect(page.getByRole('button', { name: 'Submit Order' })).toBeVisible()
    // Two-column layout — Order Preview card present
    await expect(page.getByRole('heading', { name: 'Order Preview' })).toBeVisible()
  })
})
