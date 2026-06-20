import { test, expect } from '@playwright/test'
import { loginAsAdmin, loginAsTrader } from './helpers/auth'

test.describe('Admin Panel', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto('/admin')
    await page.waitForLoadState('networkidle')
  })

  test('admin user can access the Admin Panel', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Admin Panel' })).toBeVisible()
  })

  test('shows ADMIN role badge in the header', async ({ page }) => {
    // Badge renders "ADMIN — {username}" — scoped to avoid matching sidebar text
    await expect(page.getByText(/ADMIN —/).first()).toBeVisible()
  })

  test('shows the Risk Rule Configuration card', async ({ page }) => {
    await expect(page.getByText('Risk Rule Configuration')).toBeVisible()
  })

  test('shows risk rule groups (Market, Client, Order, Fat Finger)', async ({ page }) => {
    await expect(page.getByText('Market Rules')).toBeVisible()
    await expect(page.getByText('Client Rules')).toBeVisible()
    await expect(page.getByText('Order Rules')).toBeVisible()
    await expect(page.getByText(/fat finger/i)).toBeVisible()
  })

  test('shows Expand All / Collapse All toggle button', async ({ page }) => {
    const toggleBtn = page.getByRole('button', { name: /collapse all|expand all/i })
    await expect(toggleBtn).toBeVisible()
  })

  test('Collapse All hides rule rows and Expand All restores them', async ({ page }) => {
    // Initially all groups expanded — rule codes (monospace) are visible
    const collapseBtn = page.getByRole('button', { name: /collapse all/i })
    await collapseBtn.click()

    // After collapse, MARKET_HOURS_CHECK should no longer be visible
    await expect(page.getByText('MARKET_HOURS_CHECK')).not.toBeVisible()

    // Expand All restores
    const expandBtn = page.getByRole('button', { name: /expand all/i })
    await expandBtn.click()
    await expect(page.getByText('MARKET_HOURS_CHECK')).toBeVisible()
  })

  test('clicking a group header collapses that group', async ({ page }) => {
    const marketHeader = page.getByRole('button', { name: undefined, exact: false })
      .filter({ hasText: 'Market Rules' })
    await marketHeader.click()
    await expect(page.getByText('MARKET_HOURS_CHECK')).not.toBeVisible()
  })

  test('each rule row has an enabled/disabled toggle switch', async ({ page }) => {
    // Toggle is a div with role="switch"
    const toggles = page.getByRole('switch')
    const count = await toggles.count()
    expect(count).toBeGreaterThan(0)
  })

  test('clicking a toggle opens the confirmation modal', async ({ page }) => {
    const firstToggle = page.getByRole('switch').first()
    await firstToggle.click()

    // Modal appears with a reason textarea
    await expect(page.getByPlaceholder(/describe why/i)).toBeVisible()
  })

  test('toggle modal confirm button is disabled when reason is empty', async ({ page }) => {
    await page.getByRole('switch').first().click()

    const confirmBtn = page.getByRole('button', { name: /enable rule|disable rule/i })
    await expect(confirmBtn).toBeDisabled()
  })

  test('toggle modal confirm button enables after entering reason >= 10 chars', async ({ page }) => {
    await page.getByRole('switch').first().click()

    await page.getByPlaceholder(/describe why/i).fill('Testing this rule change for QA')
    const confirmBtn = page.getByRole('button', { name: /enable rule|disable rule/i })
    await expect(confirmBtn).toBeEnabled()
  })

  test('toggle modal Cancel button closes the modal', async ({ page }) => {
    await page.getByRole('switch').first().click()
    await expect(page.getByPlaceholder(/describe why/i)).toBeVisible()

    await page.getByRole('button', { name: 'Cancel' }).click()
    await expect(page.getByPlaceholder(/describe why/i)).not.toBeVisible()
  })

  test('threshold value click opens threshold edit modal', async ({ page }) => {
    // Rules with thresholds show a value with ✎ — click to open modal
    const thresholdCell = page.getByText(/✎/).first()
    if (await thresholdCell.count() > 0) {
      await thresholdCell.click()
      await expect(page.getByText(/new threshold value/i)).toBeVisible()
    }
  })

  test('shows Risk Audit Log card', async ({ page }) => {
    await expect(page.getByText('Rule Change Audit Log')).toBeVisible()
  })

  test('shows User Management card', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'User Management' })).toBeVisible()
  })

  test('Create User button shows unavailable toast (stub)', async ({ page }) => {
    await page.getByRole('button', { name: '+ Create User' }).click()
    await expect(page.getByText(/user creation not available/i)).toBeVisible()
  })

  test('Pending Approvals section is visible', async ({ page }) => {
    // PendingApprovalsPanel renders a section — check for its heading
    await expect(page.getByText(/pending approval/i).first()).toBeVisible()
  })

})

// Separate describe — no admin beforeEach so auth injection is unambiguous
test.describe('Admin Panel — role guard', () => {
  test('trader role is redirected away from /admin', async ({ page }) => {
    await loginAsTrader(page)
    await page.goto('/admin')
    // AdminPanelPage useEffect: non-ADMIN role → navigate('/dashboard')
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 15000 })
  })
})
