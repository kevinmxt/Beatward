import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, generateResetCode, listUsers, updateUserStatus } from '../api/client'
import type { AdminUser, PageResponse, ResetCodeResponse } from '../api/types'
import AdminUsersView from './AdminUsersView.vue'

vi.mock('../api/client', async () => {
  const actual = await vi.importActual<typeof import('../api/client')>('../api/client')
  return {
    ...actual,
    generateResetCode: vi.fn(),
    listUsers: vi.fn(),
    updateUserStatus: vi.fn(),
  }
})

const listMock = vi.mocked(listUsers)
const statusMock = vi.mocked(updateUserStatus)
const resetMock = vi.mocked(generateResetCode)

function user(overrides: Partial<AdminUser> = {}): AdminUser {
  return {
    id: 2,
    username: 'alice',
    role: 'USER',
    status: 'ACTIVE',
    disabledAt: null,
    lastLoginAt: '2026-11-04T10:00:00Z',
    createdAt: '2026-11-01T00:00:00Z',
    monitorCount: 3,
    ...overrides,
  }
}

function page(items: AdminUser[]): PageResponse<AdminUser> {
  return { items, page: 0, size: 20, totalElements: items.length }
}

function mountView() {
  return mount(AdminUsersView)
}

function buttonByText(wrapper: ReturnType<typeof mountView>, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text() === text)
  if (!button) {
    throw new Error(`button not found: ${text}`)
  }
  return button
}

describe('AdminUsersView', () => {
  beforeEach(() => {
    listMock.mockReset()
    statusMock.mockReset()
    resetMock.mockReset()
  })

  it('renders account metadata and monitor counts without monitor content', async () => {
    listMock.mockResolvedValue(
      page([
        user({ id: 1, username: 'root', role: 'ADMIN', monitorCount: 0 }),
        user({ id: 2, status: 'DISABLED', monitorCount: 3 }),
      ]),
    )

    const wrapper = mountView()
    await flushPromises()

    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('root')
    expect(rows[0].text()).toContain('管理员')
    expect(rows[1].text()).toContain('alice')
    expect(rows[1].text()).toContain('用户')
    expect(rows[1].text()).toContain('已禁用')
    expect(rows[1].text()).toContain('3')
    expect(wrapper.text()).not.toContain('targetUrl')

    wrapper.unmount()
  })

  it('disables a user after confirmation and reloads', async () => {
    listMock.mockResolvedValue(page([user()]))
    statusMock.mockResolvedValue(user({ status: 'DISABLED' }))

    const wrapper = mountView()
    await flushPromises()
    await buttonByText(wrapper, '禁用').trigger('click')
    expect(wrapper.text()).toContain('禁用账号')
    await buttonByText(wrapper, '确认禁用').trigger('click')
    await flushPromises()

    expect(statusMock).toHaveBeenCalledWith(2, 'DISABLED')
    expect(listMock).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('enables a disabled user', async () => {
    listMock.mockResolvedValue(page([user({ status: 'DISABLED' })]))
    statusMock.mockResolvedValue(user({ status: 'ACTIVE' }))

    const wrapper = mountView()
    await flushPromises()
    await buttonByText(wrapper, '启用').trigger('click')
    await flushPromises()

    expect(statusMock).toHaveBeenCalledWith(2, 'ACTIVE')

    wrapper.unmount()
  })

  it('generates a reset code and shows it once', async () => {
    listMock.mockResolvedValue(page([user()]))
    const created: ResetCodeResponse = {
      code: 'one-time-reset-code',
      expiresAt: '2026-11-06T00:00:00Z',
    }
    resetMock.mockResolvedValue(created)

    const wrapper = mountView()
    await flushPromises()
    await buttonByText(wrapper, '生成重置码').trigger('click')
    await flushPromises()

    expect(resetMock).toHaveBeenCalledWith(2)
    expect(wrapper.text()).toContain('one-time-reset-code')
    expect(wrapper.text()).toContain('只显示一次')

    wrapper.unmount()
  })

  it('explains why the last active admin cannot be disabled', async () => {
    listMock.mockResolvedValue(page([user({ id: 1, username: 'root', role: 'ADMIN' })]))
    statusMock.mockRejectedValue(
      new ApiError('at least one active administrator must remain', {
        code: 'LAST_ACTIVE_ADMIN',
        status: 409,
      }),
    )

    const wrapper = mountView()
    await flushPromises()
    await buttonByText(wrapper, '禁用').trigger('click')
    await buttonByText(wrapper, '确认禁用').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('至少需要保留一个启用的管理员')

    wrapper.unmount()
  })

  it('shows server errors with trace ids', async () => {
    listMock.mockRejectedValue(
      new ApiError('unexpected error', {
        code: 'INTERNAL_ERROR',
        status: 500,
        traceId: 'trace-users',
      }),
    )

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('INTERNAL_ERROR')
    expect(wrapper.text()).toContain('trace-users')

    wrapper.unmount()
  })
})
