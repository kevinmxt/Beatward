import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { register } from '../auth/session'
import RegisterView from './RegisterView.vue'

vi.mock('../auth/session', () => ({
  register: vi.fn(),
}))

const registerMock = vi.mocked(register)
const EmptyView = { template: '<div />' }

async function mountRegister() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/register', component: RegisterView },
      { path: '/login', component: EmptyView },
      { path: '/getting-started', component: EmptyView },
    ],
  })
  await router.push('/register')
  await router.isReady()
  const wrapper = mount(RegisterView, { global: { plugins: [router] } })
  return { router, wrapper }
}

async function fillAndSubmit(
  wrapper: Awaited<ReturnType<typeof mountRegister>>['wrapper'],
  password = 'valid-password-123',
) {
  await wrapper.find('#register-invite').setValue('invite-code')
  await wrapper.find('#register-username').setValue('alice')
  await wrapper.find('#register-password').setValue(password)
  await wrapper.find('#register-confirm').setValue(password)
  await wrapper.find('form').trigger('submit')
  await flushPromises()
}

describe('RegisterView', () => {
  beforeEach(() => {
    registerMock.mockReset()
  })

  it('registers and continues to the getting-started page', async () => {
    registerMock.mockResolvedValue(undefined)
    const { router, wrapper } = await mountRegister()

    await fillAndSubmit(wrapper)

    expect(registerMock).toHaveBeenCalledWith({
      inviteCode: 'invite-code',
      username: 'alice',
      password: 'valid-password-123',
    })
    expect(router.currentRoute.value.path).toBe('/getting-started')
  })

  it('shows a readable message when the invite is exhausted', async () => {
    registerMock.mockRejectedValue(
      new ApiError('invite code has no remaining uses', {
        code: 'INVITE_EXHAUSTED',
        status: 400,
      }),
    )
    const { wrapper } = await mountRegister()

    await fillAndSubmit(wrapper)

    expect(wrapper.text()).toContain('邀请码名额已用完')
  })

  it('shows a readable message when the username is taken', async () => {
    registerMock.mockRejectedValue(
      new ApiError('username is already taken', { code: 'USERNAME_TAKEN', status: 400 }),
    )
    const { wrapper } = await mountRegister()

    await fillAndSubmit(wrapper)

    expect(wrapper.text()).toContain('用户名已被占用')
  })

  it('validates the password length and confirmation before calling the API', async () => {
    const { wrapper } = await mountRegister()

    await fillAndSubmit(wrapper, 'short')
    expect(wrapper.text()).toContain('密码长度需为')
    expect(registerMock).not.toHaveBeenCalled()

    await wrapper.find('#register-password').setValue('valid-password-123')
    await wrapper.find('#register-confirm').setValue('different-password-123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('两次输入的密码不一致')
    expect(registerMock).not.toHaveBeenCalled()
  })
})
