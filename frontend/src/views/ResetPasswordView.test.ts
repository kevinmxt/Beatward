import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { resetPassword } from '../auth/session'
import ResetPasswordView from './ResetPasswordView.vue'

vi.mock('../auth/session', () => ({
  resetPassword: vi.fn(),
}))

const resetPasswordMock = vi.mocked(resetPassword)
const EmptyView = { template: '<div />' }

async function mountReset() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/reset-password', component: ResetPasswordView },
      { path: '/login', component: EmptyView },
    ],
  })
  await router.push('/reset-password')
  await router.isReady()
  const wrapper = mount(ResetPasswordView, { global: { plugins: [router] } })
  return { router, wrapper }
}

async function fillAndSubmit(
  wrapper: Awaited<ReturnType<typeof mountReset>>['wrapper'],
  password = 'new-password-123',
) {
  await wrapper.find('#reset-code').setValue('reset-code')
  await wrapper.find('#reset-new-password').setValue(password)
  await wrapper.find('#reset-confirm-password').setValue(password)
  await wrapper.find('form').trigger('submit')
  await flushPromises()
}

describe('ResetPasswordView', () => {
  beforeEach(() => {
    resetPasswordMock.mockReset()
  })

  it('resets the password and returns to login with a confirmation', async () => {
    resetPasswordMock.mockResolvedValue(undefined)
    const { router, wrapper } = await mountReset()

    await fillAndSubmit(wrapper)

    expect(resetPasswordMock).toHaveBeenCalledWith({
      resetCode: 'reset-code',
      newPassword: 'new-password-123',
    })
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.reset).toBe('1')
  })

  it('shows a readable message for an invalid reset code', async () => {
    resetPasswordMock.mockRejectedValue(
      new ApiError('reset code is invalid', { code: 'RESET_TOKEN_INVALID', status: 400 }),
    )
    const { wrapper } = await mountReset()

    await fillAndSubmit(wrapper)

    expect(wrapper.text()).toContain('重置码无效、已过期或已使用')
  })

  it('validates the password confirmation before calling the API', async () => {
    const { wrapper } = await mountReset()

    await fillAndSubmit(wrapper, 'short')

    expect(wrapper.text()).toContain('新密码长度需为')
    expect(resetPasswordMock).not.toHaveBeenCalled()
  })
})
