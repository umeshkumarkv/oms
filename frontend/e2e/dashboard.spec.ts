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

  test('Open Orders card shows 9', async ({ page }) => {
    const card = page.locator('.card-hover', { hasText: 'Open Orders' })
    await expect(card).toContainText('9')
    await expect(card).toContainText('Active in market')
  })

  test('Filled Today card shows 2', async ({ page }) => {
    const card = page.locator('.card-hover', { hasText: 'Filled Today' })
    await expect(card).toContainText('2')
    await expect(card).toContainText('Executed')
  })

  test('Expired Today card shows 1', async ({ page }) => {
    const card = page.locator('.card-hover', { hasText: 'Expired Today' })
    await expect(card).toContainText('1')
    await expect(card).toContainText('Past expiry')
  })

  test('GTD Active card shows 2', async ({ page }) => {
    const card = page.locator('.card-hover', { hasText: 'GTD Active' })
    await expect(card).toContainText('2')
    await expect(card).toContainText('Good-till-date')
  })

  test('shows market status indicator in the navbar', async ({ page }) => {
    // Market status pill renders OPEN or CLOSED in all-caps
    await expect(page.getByText(/^(OPEN|CLOSED)$/).first()).toBeVisible()
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
