import { Page } from '@playwright/test'

const STORAGE_KEY = 'oms-auth-user'

// Fetches a real JWT from the auth service and injects it into localStorage
// before the page loads, so ProtectedRoute allows access and API calls succeed.

async function injectAuth(page: Page, username: string, password = 'Demo@1234') {
  const res = await page.request.post('/api/v1/auth/login', {
    data: { username, password },
  })
  const body = await res.json()
  const d = body.data

  await page.addInitScript(({ key, user }: { key: string; user: object }) => {
    localStorage.setItem(key, JSON.stringify(user))
  }, {
    key: STORAGE_KEY,
    user: {
      token:       d.token,
      userId:      d.userId,
      username,
      role:        d.role,
      displayName: d.displayName,
    },
  })
}

export const loginAsTrader     = (page: Page) => injectAuth(page, 'trader.john')
export const loginAsAdmin      = (page: Page) => injectAuth(page, 'admin.user')
export const loginAsOps        = (page: Page) => injectAuth(page, 'ops.manager')
