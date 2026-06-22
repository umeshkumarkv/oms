import { test, expect } from '@playwright/test'
import { loginAsTrader, loginAsOps } from './helpers/auth'

test.describe('Portfolio', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsTrader(page)
    await page.goto('/portfolio')
    await page.waitForLoadState('networkidle')
  })

  test('renders the page', async ({ page }) => {
    // PortfolioPage has no <h1> — check for the client selector which is always present
    await expect(page.getByText('Client:')).toBeVisible()
  })

  test('shows the client selector dropdown', async ({ page }) => {
    const selector = page.getByRole('combobox').first()
    await expect(selector).toBeVisible()
    // Should have at least one client option loaded
    await expect(selector).not.toHaveValue('')
  })

  test('shows Cash Balance metric card', async ({ page }) => {
    await expect(page.getByText('Cash Balance')).toBeVisible()
  })

  test('shows Reserved Cash metric card', async ({ page }) => {
    await expect(page.getByText('Reserved Cash')).toBeVisible()
  })

  test('shows Buying Power metric card', async ({ page }) => {
    await expect(page.getByText('Buying Power')).toBeVisible()
  })

  test('shows Portfolio Value metric card', async ({ page }) => {
    await expect(page.getByText('Portfolio Value')).toBeVisible()
  })

  test('shows Holdings table with column headers', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Holdings' })).toBeVisible()
    for (const header of ['Symbol', 'Qty', 'Avg Cost', 'Current Price', 'Market Value']) {
      await expect(page.getByText(header).first()).toBeVisible()
    }
  })

  test('shows Unrealised P&L column header', async ({ page }) => {
    await expect(page.getByText(/unreali[sz]ed p&l/i)).toBeVisible()
  })

  test("shows Today's Activity table", async ({ page }) => {
    await expect(page.getByText("Today's Activity")).toBeVisible()
    await expect(page.getByText('Fill Qty').first()).toBeVisible()
    await expect(page.getByText('Fill Price').first()).toBeVisible()
  })

  test('shows auto-refresh note', async ({ page }) => {
    await expect(page.getByText(/auto-refresh/i)).toBeVisible()
  })

  test('client selector has multiple options', async ({ page }) => {
    const options = page.getByRole('combobox').first().locator('option')
    const count = await options.count()
    expect(count).toBeGreaterThan(1)
  })

  test('metric card values are formatted as currency', async ({ page }) => {
    // Values rendered by formatCurrency — should contain $ sign
    const currencyPattern = /\$[\d,]+\.?\d{0,2}/
    await expect(page.getByText(currencyPattern).first()).toBeVisible({ timeout: 8000 })
  })

  test('ops user can access the portfolio page', async ({ page }) => {
    await loginAsOps(page)
    await page.goto('/portfolio')
    await page.waitForLoadState('networkidle')
    await expect(page.getByText('Client:')).toBeVisible()
  })
})
