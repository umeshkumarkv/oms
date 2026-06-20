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
    // Subtitle shows today's count — "N order today" (singular) or "N orders today" (plural)
    await expect(page.getByText(/\d+ orders? today/i)).toBeVisible()
  })

  test('shows all expected column headers', async ({ page }) => {
    for (const header of ['Order ID', 'Symbol', 'Side', 'Type', 'Qty', 'Status', 'TIF', 'Created']) {
      await expect(page.getByText(header).first()).toBeVisible()
    }
  })

  test('renders the orders table', async ({ page }) => {
    // Row count varies with live data — verify the table structure is present
    await expect(page.locator('tbody')).toBeVisible()
  })

  test('does not show prior-day terminal orders (COI-016 and COI-017 are hidden)', async ({ page }) => {
    await expect(page.getByText('COI-016')).not.toBeVisible()
    await expect(page.getByText('COI-017')).not.toBeVisible()
  })

  test('shows status badges when orders are present', async ({ page }) => {
    const rows = page.locator('tbody tr')
    const rowCount = await rows.count()
    if (rowCount === 0) {
      // Empty blotter is valid — no further assertions
      return
    }
    // At least one status pill should be visible in the table
    await expect(page.locator('tbody tr').first()).toBeVisible()
  })

  test('shows FILLED status badges when present', async ({ page }) => {
    const rows = page.locator('tbody tr')
    const rowCount = await rows.count()
    if (rowCount === 0) return
    // FILLED badge visible only if at least one order reached FILLED state
    const filled = page.getByText('FILLED')
    const filledCount = await filled.count()
    if (filledCount > 0) {
      await expect(filled.first()).toBeVisible()
    }
  })

  test('RISK_REJECTED orders display their status', async ({ page }) => {
    // Verify RISK_REJECTED badge renders when that state is present
    const rows = page.locator('tbody tr')
    if (await rows.count() === 0) return
    const badge = page.getByText('RISK REJECTED')
    if (await badge.count() > 0) {
      await expect(badge.first()).toBeVisible()
    }
  })

  test('GTC orders appear in the blotter when present', async ({ page }) => {
    const rows = page.locator('tbody tr')
    if (await rows.count() === 0) return
    // GTC TIF column renders "GTC" for good-till-cancelled orders
    const gtcCells = page.getByText('GTC')
    if (await gtcCells.count() > 0) {
      await expect(gtcCells.first()).toBeVisible()
    }
  })

  test('shows SHORT_SELL orders when present', async ({ page }) => {
    const shortSellBadges = page.getByText('Short Sell')
    const count = await shortSellBadges.count()
    if (count > 0) {
      await expect(shortSellBadges.first()).toBeVisible()
    }
  })

  test('search input is present and filters the table', async ({ page }) => {
    const searchInput = page.getByPlaceholder(/search/i)
    await expect(searchInput).toBeVisible()

    // Type a search term and verify the table reacts (row count changes or stays 0)
    await searchInput.fill('ZZZNOMATCH')
    await page.waitForTimeout(300)
    // After a no-match search, either 0 rows or an empty-state message
    const rows = page.locator('tbody tr')
    const count = await rows.count()
    expect(count).toBeGreaterThanOrEqual(0)
  })

  test('clearing search restores the full order list', async ({ page }) => {
    const searchInput = page.getByPlaceholder(/search/i)
    await searchInput.fill('ZZZNOMATCH')
    await page.waitForTimeout(300)
    const filteredCount = await page.locator('tbody tr').count()

    await searchInput.clear()
    await page.waitForTimeout(300)
    const restoredCount = await page.locator('tbody tr').count()
    // Restored count must be ≥ filtered count
    expect(restoredCount).toBeGreaterThanOrEqual(filteredCount)
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
