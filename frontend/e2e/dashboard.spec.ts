import { test, expect } from '@playwright/test'
import { loginAsTrader } from './helpers/auth'

test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsTrader(page)
    await page.goto('/dashboard')
    await page.waitForLoadState('networkidle')
  })

  test('shows the page title', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
  })

  test('renders all four metric cards', async ({ page }) => {
    await expect(page.getByText('Open Orders')).toBeVisible()
    await expect(page.getByText('Filled Today')).toBeVisible()
    await expect(page.getByText('Expired Today')).toBeVisible()
    await expect(page.getByText('GTD Active')).toBeVisible()
  })

  test('Open Orders card shows a count and subtitle', async ({ page }) => {
    const card = page.locator('.card-hover', { hasText: 'Open Orders' })
    await expect(card).toContainText('Active in market')
    await expect(card.getByText(/^\d+$/)).toBeVisible()
  })

  test('Filled Today card shows a count and subtitle', async ({ page }) => {
    const card = page.locator('.card-hover', { hasText: 'Filled Today' })
    await expect(card).toContainText('Executed')
    await expect(card.getByText(/^\d+$/)).toBeVisible()
  })

  test('Expired Today card shows a count and subtitle', async ({ page }) => {
    const card = page.locator('.card-hover', { hasText: 'Expired Today' })
    await expect(card).toContainText('Past expiry')
    await expect(card.getByText(/^\d+$/)).toBeVisible()
  })

  test('GTD Active card shows a count and subtitle', async ({ page }) => {
    const card = page.locator('.card-hover', { hasText: 'GTD Active' })
    await expect(card).toContainText('Good-till-date')
    await expect(card.getByText(/^\d+$/)).toBeVisible()
  })

  test('shows market status indicator in the navbar', async ({ page }) => {
    // NavBar renders "MARKET OPEN" or "MARKET CLOSED"
    await expect(page.getByText(/MARKET (OPEN|CLOSED)/).first()).toBeVisible()
  })

  test('shows quick action links', async ({ page }) => {
    // Use role=button to target the quick-action tiles, not the sidebar links
    await expect(page.getByRole('button', { name: 'New Order' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'View Blotter' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Portfolio' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Market Data' })).toBeVisible()
  })

  test('GTD Expiring Soon section is visible', async ({ page }) => {
    await expect(page.getByText('GTD Expiring Soon')).toBeVisible()
  })

  test('navigates to order entry from New Order quick action', async ({ page }) => {
    await page.getByRole('button', { name: 'New Order' }).click()
    await expect(page).toHaveURL(/\/orders\/new/)
  })

  test('navigates to blotter from View Blotter quick action', async ({ page }) => {
    await page.getByRole('button', { name: 'View Blotter' }).click()
    await expect(page).toHaveURL(/\/orders$/)
  })
})
