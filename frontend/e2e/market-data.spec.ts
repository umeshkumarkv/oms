import { test, expect } from '@playwright/test'
import { loginAsTrader } from './helpers/auth'

test.describe('Market Data', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsTrader(page)
    await page.goto('/market')
    await page.waitForLoadState('networkidle')
  })

  test('renders the page heading', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /market/i }).first()).toBeVisible()
  })

  test('shows the watchlist panel with at least one instrument', async ({ page }) => {
    // Watchlist panel renders stock cards — wait for at least one ticker to appear
    await expect(page.locator('[data-testid="watchlist"], .watchlist, tbody tr').first()).toBeVisible({ timeout: 8000 })
  })

  test('shows market status (OPEN or CLOSED)', async ({ page }) => {
    // NavBar renders "MARKET OPEN" or "MARKET CLOSED"
    await expect(page.getByText(/MARKET (OPEN|CLOSED)/).first()).toBeVisible()
  })

  test('search input for adding instrument is present', async ({ page }) => {
    // The page has a search/add instrument input
    const searchInput = page.getByPlaceholder(/search|add|symbol/i).first()
    await expect(searchInput).toBeVisible()
  })

  test('symbol price data is displayed', async ({ page }) => {
    // At least one price value is rendered — price columns contain numeric-looking text
    // Wait for network data to arrive after networkidle
    const pricePattern = /\d+\.\d{2}/
    await expect(page.getByText(pricePattern).first()).toBeVisible({ timeout: 10000 })
  })

  test('bid and ask columns are present', async ({ page }) => {
    // MarketDataPage shows bid/ask spread data
    await expect(page.getByText(/bid/i).first()).toBeVisible()
    await expect(page.getByText(/ask/i).first()).toBeVisible()
  })

  test('intraday chart or tick history section is present', async ({ page }) => {
    // Page renders either a chart or a tick history table
    const hasChart = await page.locator('canvas, svg[class*="chart"]').count()
    const hasTick  = await page.getByText(/tick|history|intraday/i).count()
    expect(hasChart + hasTick).toBeGreaterThan(0)
  })

  test('clicking a watchlist instrument shows detail data', async ({ page }) => {
    // Click the first instrument in the list — detail panel or expanded view should appear
    const firstCard = page.locator('[role="button"], tr[data-symbol], .stock-card').first()
    if (await firstCard.count() > 0) {
      await firstCard.click()
      await page.waitForLoadState('networkidle')
      // After selection some detail is visible — check for a price or spread value
      await expect(page.getByText(/\d+\.\d{2}/).first()).toBeVisible()
    }
  })
})