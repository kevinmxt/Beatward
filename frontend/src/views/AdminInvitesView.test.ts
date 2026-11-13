import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, createInviteCode, listInviteCodes, revokeInviteCode } from '../api/client'
import type { InviteCode, InviteCodeCreateResponse, PageResponse } from '../api/types'
import AdminInvitesView from './AdminInvitesView.vue'

vi.mock('../api/client', async () => {
  const actual = await vi.importActual<typeof import('../api/client')>('../api/client')
  return {
    ...actual,
    createInviteCode: vi.fn(),
    listInviteCodes: vi.fn(),
    revokeInviteCode: vi.fn(),
  }
})

const listMock = vi.mocked(listInviteCodes)
const createMock = vi.mocked(createInviteCode)
const revokeMock = vi.mocked(revokeInviteCode)

function invite(overrides: Partial<InviteCode> = {}): InviteCode {
  return {
    id: 1,
    label: 'batch-1',
    maxUses: 3,
    usedCount: 1,
    expiresAt: '2099-01-01T00:00:00Z',
    revokedAt: null,
    createdAt: '2026-11-05T00:00:00Z',
    ...overrides,
  }
}

function page(items: InviteCode[]): PageResponse<InviteCode> {
  return { items, page: 0, size: 20, totalElements: items.length }
}

function mountView() {
  return mount(AdminInvitesView)
}

function buttonByText(wrapper: ReturnType<typeof mountView>, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text() === text)
  if (!button) {
    throw new Error(`button not found: ${text}`)
  }
  return button
}

describe('AdminInvitesView', () => {
  beforeEach(() => {
    listMock.mockReset()
    createMock.mockReset()
    revokeMock.mockReset()
  })

  it('renders invite usage and revocation state', async () => {
    listMock.mockResolvedValue(
      page([invite(), invite({ id: 2, label: 'revoked', revokedAt: '2026-11-05T01:00:00Z' })]),
    )

    const wrapper = mountView()
    await flushPromises()

    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('batch-1')
    expect(rows[0].text()).toContain('1 / 3')
    expect(rows[0].text()).toContain('可用')
    expect(rows[1].text()).toContain('已撤销')
    expect(wrapper.text()).toContain('共 2 个邀请码')

    wrapper.unmount()
  })

  it('creates an invite code and shows the plaintext once', async () => {
    listMock.mockResolvedValue(page([]))
    const created: InviteCodeCreateResponse = {
      id: 9,
      code: 'secret-invite-code',
      label: 'new-batch',
      maxUses: 2,
      expiresAt: '2026-11-06T00:00:00Z',
    }
    createMock.mockResolvedValue(created)

    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('#invite-label').setValue('new-batch')
    await wrapper.find('#invite-max-uses').setValue('2')
    await wrapper.find('#invite-expires').setValue('24')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(createMock).toHaveBeenCalledWith({
      label: 'new-batch',
      maxUses: 2,
      expiresInHours: 24,
    })
    expect(wrapper.text()).toContain('secret-invite-code')
    expect(wrapper.text()).toContain('只显示一次')
    expect(listMock).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('revokes an invite code after confirmation and reloads', async () => {
    listMock.mockResolvedValue(page([invite({ id: 4 })]))
    revokeMock.mockResolvedValue(undefined)

    const wrapper = mountView()
    await flushPromises()
    await buttonByText(wrapper, '撤销').trigger('click')
    expect(wrapper.text()).toContain('撤销邀请码')
    await buttonByText(wrapper, '确认撤销').trigger('click')
    await flushPromises()

    expect(revokeMock).toHaveBeenCalledWith(4)
    expect(listMock).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('shows server errors without dropping the page', async () => {
    listMock.mockResolvedValue(page([]))
    createMock.mockRejectedValue(
      new ApiError('label is too long', {
        code: 'VALIDATION_ERROR',
        status: 400,
        traceId: 'trace-invite',
      }),
    )

    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('#invite-label').setValue('too-long')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('VALIDATION_ERROR')
    expect(wrapper.text()).toContain('label is too long')
    expect(wrapper.text()).toContain('trace-invite')

    wrapper.unmount()
  })
})
