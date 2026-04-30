import axios from 'axios'

// Module-level token store — AuthContext calls setApiToken on login/logout.
// Axios interceptors cannot use React hooks, so this is the standard pattern.
let authToken: string | null = null

export function setApiToken(token: string | null): void {
  authToken = token
}

const apiClient = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
})

// Attach Bearer token to every outbound request
apiClient.interceptors.request.use((config) => {
  if (authToken) {
    config.headers.Authorization = `Bearer ${authToken}`
  }
  return config
})

// On 401 from any API call: clear token and signal session expiry to AuthContext
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      authToken = null
      window.dispatchEvent(new CustomEvent('auth:session-expired'))
    }
    return Promise.reject(error)
  }
)

export default apiClient
