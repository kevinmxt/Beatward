import { applySession, resetAuthState } from '../auth/session'

export function authenticateForTest(
  username = 'test-admin',
  csrfToken = 'csrf-1',
  role: 'ADMIN' | 'USER' = 'ADMIN',
): void {
  resetAuthState()
  applySession({
    authenticated: true,
    username,
    role,
    csrfToken,
  })
}
