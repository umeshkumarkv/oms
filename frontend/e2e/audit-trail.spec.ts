import { test, expect } from '@playwright/test'
import { loginAsOps } from './helpers/auth'

// AuditTrailPage is a stub ("coming soon") — tests reflect current implementation state.
// When the full feature is built, update these tests against SCRUM ticket for audit trail.
test.describe('Audit Trail', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsOps(page)
    await page.goto('/audit')
    await page.waitForLoadState('networkidle')
  })

  test('renders the Audit Trail stub page', async ({ page }) => {
    await expect(page.getByText('AuditTrailPage — coming soon')).toBeVisible()
  })

  test('Audit Trail nav link is active', async ({ page }) => {
    // Sidebar shows "Audit Trail" as the active nav item
    await expect(page.getByText('Audit Trail').first()).toBeVisible()
  })

  // The following tests are skipped pending full implementation of AuditTrailPage
  test.skip('shows audit log entries in a table', async ({ page }) => {
    const rows = page.locator('tbody tr')
    const count = await rows.count()
    expect(count).toBeGreaterThanOrEqual(22)
  })

  test.skip('shows expected column headers', async ({ page }) => {
    for (const header of ['Entity', 'Entity ID', 'Event', 'Actor', 'Timestamp']) {
      await expect(page.getByText(header, { exact: false }).first()).toBeVisible()
    }
  })

  test.skip('shows ORDER entity type entries', async ({ page }) => {
    await expect(page.getByText('ORDER').first()).toBeVisible()
  })

  test.skip('shows state transitions (NEW → FILLED chain is present)', async ({ page }) => {
    await expect(page.getByText('FILLED').first()).toBeVisible()
  })

  test.skip('filters by entity ID', async ({ page }) => {
    const searchInput = page.getByPlaceholder(/search|filter/i).first()
    if (await searchInput.isVisible()) {
      await searchInput.fill('00000000-0000-0000-0000-000000000108')
      await page.waitForTimeout(300)
      expect(await page.locator('tbody tr').count()).toBeGreaterThanOrEqual(1)
    }
  })
})
