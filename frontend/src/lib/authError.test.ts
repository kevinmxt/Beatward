import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/client'
import {
  loginErrorMessage,
  registrationErrorMessage,
  resetPasswordErrorMessage,
} from './authError'

describe('loginErrorMessage', () => {
  it('uses a generic message for invalid credentials', () => {
    const error = new ApiError('invalid username or password', {
      code: 'INVALID_CREDENTIALS',
      status: 401,
    })

    expect(loginErrorMessage(error)).toBe('用户名或密码错误')
  })

  it('shows the retry delay when the server sends one', () => {
    const error = new ApiError('too many attempts', {
      code: 'RATE_LIMITED',
      status: 429,
      retryAfterSeconds: 42,
    })

    expect(loginErrorMessage(error)).toBe('尝试次数过多，请在 42 秒后重试')
  })

  it('falls back when no retry delay is available', () => {
    const error = new ApiError('too many attempts', {
      code: 'RATE_LIMITED',
      status: 429,
    })

    expect(loginErrorMessage(error)).toBe('尝试次数过多，请稍后重试')
  })

  it('explains that a disabled account must contact the administrator', () => {
    const error = new ApiError('account is disabled', {
      code: 'ACCOUNT_DISABLED',
      status: 403,
    })

    expect(loginErrorMessage(error)).toBe('账号已被禁用，请联系管理员')
  })

  it('preserves other server messages and hides unexpected errors', () => {
    expect(
      loginErrorMessage(
        new ApiError('服务器内部错误', { code: 'INTERNAL_ERROR', status: 500 }),
      ),
    ).toBe('服务器内部错误')
    expect(loginErrorMessage(new Error('boom'))).toBe('登录失败，请稍后重试')
  })
})

describe('registrationErrorMessage', () => {
  it('maps each invite failure to an actionable message', () => {
    expect(registrationErrorMessage(new ApiError('x', { code: 'INVITE_INVALID', status: 400 })))
      .toContain('邀请码无效')
    expect(registrationErrorMessage(new ApiError('x', { code: 'INVITE_EXPIRED', status: 400 })))
      .toContain('已过期')
    expect(registrationErrorMessage(new ApiError('x', { code: 'INVITE_REVOKED', status: 400 })))
      .toContain('已被撤销')
    expect(registrationErrorMessage(new ApiError('x', { code: 'INVITE_EXHAUSTED', status: 400 })))
      .toContain('名额已用完')
  })

  it('falls back for unknown failures', () => {
    expect(registrationErrorMessage(new Error('boom'))).toBe('注册失败，请稍后重试')
  })
})

describe('resetPasswordErrorMessage', () => {
  it('maps an invalid reset code to an actionable message', () => {
    expect(
      resetPasswordErrorMessage(
        new ApiError('x', { code: 'RESET_TOKEN_INVALID', status: 400 }),
      ),
    ).toContain('重置码无效、已过期或已使用')
  })

  it('falls back for unknown failures', () => {
    expect(resetPasswordErrorMessage(new Error('boom'))).toBe('重置密码失败，请稍后重试')
  })
})
