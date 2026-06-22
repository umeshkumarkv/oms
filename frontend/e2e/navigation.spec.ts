import { test, expect, Page } from '@playwright/test'
import { loginAsTrader, loginAsOps, loginAsAdmin } from './helpers/auth'

// ── Protected route redirects ──────────────────────────────────────────────

test.describe('Protected routes', () => {
  test('unauthenticated /dashboard → redirects to /login', async ({ page }) => {
    await page.goto('/dashboard')
    await expect(page).toHaveURL(/\/(?:login|$)/)
  })

  test('unauthenticated /orders → redirects to /login', async ({ page }) => {
    await page.goto('/orders')
    await expect(page).toHaveURL(/\/(?:login|$)/)
  })

  test('unauthenticated /orders/new → redirects to /login', async ({ page }) => {
    await page.goto('/orders/new')
    await expect(page).toHaveURL(/\/(?:login|$)/)
  })

  test('unauthenticated /portfolio → redirects to /login', async ({ page }) => {
    await page.goto('/portfolio')
    await expect(page).toHaveURL(/\/(?:login|$)/)
  })

  test('unauthenticated /market → redirects to /login', async ({ page }) => {
    await page.goto('/market')
    await expect(page).toHaveURL(/\/(?:login|$)/)
  })

  test('unauthenticated /audit → redirects to /login', async ({ page }) => {
    await page.goto('/audit')
    await expect(page).toHaveURL(/\/(?:login|$)/)
  })
})

// ── Sign out ───────────────────────────────────────────────────────────────

test.describe('Sign out', () => {
  test('sign out clears auth and redirects to login page', async ({ page }) => {
    await loginAsTrader(page)
    await page.goto('/dashboard')
    await page.waitForLoadState('networkidle')

    // Sign Out button is in the NavBar
    await page.getByRole('button', { name: /sign out/i }).click()
    await expect(page).toHaveURL(/\/(?:login|$)/)
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  })

  // NOTE: a post-signout page.goto() re-triggers addInitScript which re-injects
  // the auth token, so re-navigation after sign-out cannot be reliably tested
  // with the current addInitScript auth approach. Covered by unit tests in
  // src/test/ProtectedRoute.test.tsx instead.
})

// ── Navbar ─────────────────────────────────────────────────────────────────

test.describe('Navbar', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsTrader(page)
    await page.goto('/dashboard')
    await page.waitForLoadState('networkidle')
  })

  test('shows the Broker OMS logo text', async ({ page }) => {
    await expect(page.getByText('Broker OMS').first()).toBeVisible()
  })

  test('shows market status pill (OPEN or CLOSED)', async ({ page }) => {
    // NavBar renders "MARKET OPEN" or "MARKET CLOSED" — not bare "OPEN"/"CLOSED"
    await expect(page.getByText(/MARKET (OPEN|CLOSED)/).first()).toBeVisible()
  })

  test('shows user initials avatar', async ({ page }) => {
    // Trader john — initials "TJ" rendered as avatar
    const avatar = page.getByText(/^[A-Z]{1,2}$/).first()
    await expect(avatar).toBeVisible()
  })

  test('shows role badge (TRADER for trader.john)', async ({ page }) => {
    await expect(page.getByText('TRADER').first()).toBeVisible()
  })

  test('shows sign out button', async ({ page }) => {
    await expect(page.getByRole('button', { name: /sign out/i })).toBeVisible()
  })

  test('admin role badge shows ADMIN for admin user', async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto('/admin')
    await page.waitForLoadState('networkidle')
    // Admin badge renders "ADMIN — {username}" in the admin panel header
    await expect(page.getByText(/ADMIN —/).first()).toBeVisible()
  })
})

// ── Sidebar ────────────────────────────────────────────────────────────────

test.describe('Sidebar', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsTrader(page)
    await page.goto('/dashboard')
    await page.waitForLoadState('networkidle')
    // Reset sidebar to expanded state by clearing localStorage key
    await page.evaluate(() => localStorage.removeItem('oms-sidebar-collapsed'))
    await page.reload()
    await page.waitForLoadState('networkidle')
  })

  test('sidebar is visible and expanded by default', async ({ page }) => {
    const sidebar = page.locator('.sidebar').first()
    await expect(sidebar).toBeVisible()
    await expect(sidebar).not.toHaveClass(/collapsed/)
  })

  test('burger toggle collapses the sidebar', async ({ page }) => {
    // Burger button is a 32×32 button in the navbar
    const burger = page.getByRole('button', { name: /menu|toggle|sidebar/i }).first()
    await burger.click()
    await expect(page.locator('.sidebar').first()).toHaveClass(/collapsed/)
  })

  test('sidebar collapse state persists across page reload', async ({ page }) => {
    // Collapse
    await page.getByRole('button', { name: /menu|toggle|sidebar/i }).first().click()
    await expect(page.locator('.sidebar').first()).toHaveClass(/collapsed/)

    // Reload — localStorage should restore collapsed state
    await page.reload()
    await page.waitForLoadState('networkidle')
    await expect(page.locator('.sidebar').first()).toHaveClass(/collapsed/)
  })

  test('sidebar links navigate to the correct routes', async ({ page }) => {
    const links: [string, RegExp][] = [
      ['Order Blotter', /\/orders$/],
      ['Market Data',   /\/market/],
      ['Portfolio',     /\/portfolio/],
    ]
    for (const [label, urlPattern] of links) {
      await page.getByRole('link', { name: label }).click()
      await expect(page).toHaveURL(urlPattern)
    }
  })

  test('active sidebar link updates when navigating', async ({ page }) => {
    await page.getByRole('link', { name: 'Order Blotter' }).click()
    await expect(page).toHaveURL(/\/orders$/)

    // The active link has the .active class
    const activeLink = page.locator('.sidebar-link.active')
    await expect(activeLink).toContainText('Order Blotter')
  })

  test('Admin Panel link visible only for admin role', async ({ page }) => {
    // Trader should NOT see Admin Panel link
    await expect(page.getByRole('link', { name: 'Admin Panel' })).not.toBeVisible()

    // Admin user should see it
    await loginAsAdmin(page)
    await page.goto('/dashboard')
    await page.waitForLoadState('networkidle')
    await expect(page.getByRole('link', { name: 'Admin Panel' })).toBeVisible()
  })
})
