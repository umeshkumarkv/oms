import { test, expect } from '@playwright/test'
import { loginAsTrader } from './helpers/auth'

test.describe('Order Blotter', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsTrader(page)
    await page.goto('/orders')
    await page.waitForLoadState('networkidle')
  })

  test('renders the page title and subtitle', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Order Blotter' })).toBeVisible()
    // Subtitle shows today's count
    await expect(page.getByText(/orders today/i)).toBeVisible()
  })

  test('shows all expected column headers', async ({ page }) => {
    for (const header of ['Order ID', 'Symbol', 'Side', 'Type', 'Qty', 'Status', 'TIF', 'Created']) {
      await expect(page.getByText(header).first()).toBeVisible()
    }
  })

  test('renders 15 visible orders (13 today + 2 prior-day GTD carry-forward)', async ({ page }) => {
    const rows = page.locator('tbody tr')
    await expect(rows).toHaveCount(15)
  })

  test('does not show prior-day terminal orders (COI-016 and COI-017 are hidden)', async ({ page }) => {
    await expect(page.getByText('COI-016')).not.toBeVisible()
    await expect(page.getByText('COI-017')).not.toBeVisible()
  })

  test('shows a PARTIAL FILL status badge', async ({ page }) => {
    await expect(page.getByText('PARTIAL FILL')).toBeVisible()
  })

  test('shows FILLED status badges', async ({ page }) => {
    await expect(page.getByText('FILLED').first()).toBeVisible()
  })

  test('shows a RISK REJECTED order', async ({ page }) => {
    // COI-004 is RISK_REJECTED
    await expect(page.getByText('COI-004')).toBeVisible()
  })

  test('shows GTD carry-forward orders (COI-014 and COI-015)', async ({ page }) => {
    await expect(page.getByText('COI-014')).toBeVisible()
    await expect(page.getByText('COI-015')).toBeVisible()
  })

  test('shows SHORT_SELL orders', async ({ page }) => {
    // COI-010 is SHORT_SELL REJECTED, COI-013 is SHORT_SELL VALIDATED
    const shortSellBadges = page.getByText('Short Sell')
    await expect(shortSellBadges.first()).toBeVisible()
  })

  test('search by symbol filters the table', async ({ page }) => {
    const searchInput = page.getByPlaceholder(/search/i)
    await searchInput.fill('MSFT')
    await page.waitForTimeout(300)  // debounce

    const rows = page.locator('tbody tr')
    const count = await rows.count()
    expect(count).toBeGreaterThan(0)

    // Every visible row should contain MSFT
    for (let i = 0; i < count; i++) {
      await expect(rows.nth(i)).toContainText('MSFT')
    }
  })

  test('search by client ref filters the table', async ({ page }) => {
    const searchInput = page.getByPlaceholder(/search/i)
    await searchInput.fill('COI-008')
    await page.waitForTimeout(300)

    await expect(page.locator('tbody tr')).toHaveCount(1)
    await expect(page.getByText('COI-008')).toBeVisible()
  })

  test('clicking a row opens the order detail panel', async ({ page }) => {
    await page.locator('tbody tr').first().click()
    // Detail panel shows order information
    await expect(page.getByText('Order Details')).toBeVisible()
  })

  test('CSV export button is present', async ({ page }) => {
    await expect(page.getByTitle('Export current filtered view as CSV')).toBeVisible()
  })
})
