import { ApiError } from '../api/client'

export function loginErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return '登录失败，请稍后重试'
  }
  if (error.code === 'ACCOUNT_DISABLED') {
    return '账号已被禁用，请联系管理员'
  }
  if (error.status === 401 || error.code === 'INVALID_CREDENTIALS') {
    return '用户名或密码错误'
  }
  if (error.status === 429 || error.code === 'RATE_LIMITED') {
    return rateLimitMessage(error)
  }
  return error.message
}

function rateLimitMessage(error: ApiError): string {
  return error.retryAfterSeconds === null
    ? '尝试次数过多，请稍后重试'
    : `尝试次数过多，请在 ${error.retryAfterSeconds} 秒后重试`
}

export function registrationErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return '注册失败，请稍后重试'
  }
  switch (error.code) {
    case 'INVITE_INVALID':
      return '邀请码无效，请向管理员确认'
    case 'INVITE_EXPIRED':
      return '邀请码已过期，请向管理员申请新码'
    case 'INVITE_REVOKED':
      return '邀请码已被撤销，请向管理员申请新码'
    case 'INVITE_EXHAUSTED':
      return '邀请码名额已用完，请向管理员申请新码'
    case 'USERNAME_TAKEN':
      return '用户名已被占用'
    case 'VALIDATION_ERROR':
      return '用户名或密码不符合要求'
    case 'RATE_LIMITED':
      return rateLimitMessage(error)
    default:
      return error.message
  }
}

export function resetPasswordErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return '重置密码失败，请稍后重试'
  }
  switch (error.code) {
    case 'RESET_TOKEN_INVALID':
      return '重置码无效、已过期或已使用，请向管理员申请新码'
    case 'VALIDATION_ERROR':
      return '新密码不符合要求'
    case 'RATE_LIMITED':
      return rateLimitMessage(error)
    default:
      return error.message
  }
}
