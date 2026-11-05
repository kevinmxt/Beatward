import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  createNotificationChannel,
  deleteNotificationChannel,
  getUsage,
  listNotificationChannels,
  testNotificationChannel,
  updateNotificationChannel,
} from '../api/client'
import type {
  NotificationChannel,
  NotificationChannelTestResult,
  PageResponse,
} from '../api/types'
import NotificationSettingsView from './NotificationSettingsView.vue'

vi.mock('../api/client', async () => {
  const actual = await vi.importActual<typeof import('../api/client')>('../api/client')
  return {
    ...actual,
    createNotificationChannel: vi.fn(),
    deleteNotificationChannel: vi.fn(),
    getUsage: vi.fn(),
    listNotificationChannels: vi.fn(),
    testNotificationChannel: vi.fn(),
    updateNotificationChannel: vi.fn(),
  }
})

const listMock = vi.mocked(listNotificationChannels)
const createMock = vi.mocked(createNotificationChannel)
const updateMock = vi.mocked(updateNotificationChannel)
const deleteMock = vi.mocked(deleteNotificationChannel)
const testMock = vi.mocked(testNotificationChannel)
const usageMock = vi.mocked(getUsage)

function channel(overrides: Partial<NotificationChannel> = {}): NotificationChannel {
  return {
    id: 1,
    type: 'PUSHPLUS',
    name: '主微信',
    enabled: true,
    maskedConfig: '****1234',
    lastTestAt: null,
    lastTestStatus: null,
    lastTestError: null,
    createdAt: '2026-11-05T00:00:00Z',
    updatedAt: '2026-11-05T00:00:00Z',
    ...overrides,
  }
}

function page(items: NotificationChannel[]): PageResponse<NotificationChannel> {
  return { items, page: 0, size: 20, totalElements: items.length }
}

function mountView() {
  return mount(NotificationSettingsView, {
    global: {
      stubs: {
        RouterLink: {
          props: ['to'],
          template: '<a :href="to"><slot /></a>',
        },
      },
    },
  })
}

function buttonByText(wrapper: ReturnType<typeof mountView>, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text() === text)
  if (!button) {
    throw new Error(`button not found: ${text}`)
  }
  return button
}

describe('NotificationSettingsView', () => {
  beforeEach(() => {
    listMock.mockReset()
    createMock.mockReset()
    updateMock.mockReset()
    deleteMock.mockReset()
    testMock.mockReset()
    usageMock.mockReset()
    usageMock.mockResolvedValue({
      monitorCount: 0,
      monitorLimit: 20,
      enabledChannelCount: 1,
      channelLimit: 3,
      hasSuccessfulChannelTest: false,
    })
  })

  it('renders masked credentials and test state', async () => {
    listMock.mockResolvedValue(
      page([
        channel({ lastTestStatus: 'FAILED', lastTestError: 'HTTP 500' }),
        channel({ id: 2, type: 'EMAIL', name: '邮箱', maskedConfig: 'a***@example.com' }),
      ]),
    )

    const wrapper = mountView()
    await flushPromises()

    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('主微信')
    expect(rows[0].text()).toContain('推送加（微信）')
    expect(rows[0].text()).toContain('****1234')
    expect(rows[0].text()).toContain('失败')
    expect(rows[0].text()).toContain('HTTP 500')
    expect(rows[1].text()).toContain('邮件')
    expect(wrapper.text()).toContain('已启用 1 / 3')

    wrapper.unmount()
  })

  it('creates a PushPlus channel with the token', async () => {
    listMock.mockResolvedValue(page([]))
    createMock.mockResolvedValue(channel())

    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('#channel-name').setValue('主微信')
    await wrapper.find('#channel-credential').setValue('tok-1234')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(createMock).toHaveBeenCalledWith({
      type: 'PUSHPLUS',
      name: '主微信',
      enabled: true,
      config: { token: 'tok-1234' },
    })
    expect(listMock).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('creates an email channel with the address', async () => {
    listMock.mockResolvedValue(page([]))
    createMock.mockResolvedValue(channel({ type: 'EMAIL' }))

    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('#channel-type').setValue('EMAIL')
    await wrapper.find('#channel-name').setValue('邮箱')
    await wrapper.find('#channel-credential').setValue('me@example.com')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(createMock).toHaveBeenCalledWith({
      type: 'EMAIL',
      name: '邮箱',
      enabled: true,
      config: { email: 'me@example.com' },
    })

    wrapper.unmount()
  })

  it('toggles enabled state and reloads', async () => {
    listMock.mockResolvedValue(page([channel()]))
    updateMock.mockResolvedValue(channel({ enabled: false }))

    const wrapper = mountView()
    await flushPromises()
    await buttonByText(wrapper, '禁用').trigger('click')
    await flushPromises()

    expect(updateMock).toHaveBeenCalledWith(1, { name: '主微信', enabled: false })
    expect(listMock).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('tests a channel and shows the result', async () => {
    listMock.mockResolvedValue(page([channel()]))
    const result: NotificationChannelTestResult = {
      status: 'SUCCESS',
      message: null,
      testedAt: '2026-11-05T01:00:00Z',
    }
    testMock.mockResolvedValue(result)

    const wrapper = mountView()
    await flushPromises()
    await buttonByText(wrapper, '测试').trigger('click')
    await flushPromises()

    expect(testMock).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('「主微信」测试')
    expect(wrapper.text()).toContain('成功')

    wrapper.unmount()
  })

  it('deletes a channel after confirmation', async () => {
    listMock.mockResolvedValue(page([channel()]))
    deleteMock.mockResolvedValue(undefined)

    const wrapper = mountView()
    await flushPromises()
    await buttonByText(wrapper, '删除').trigger('click')
    expect(wrapper.text()).toContain('删除通知渠道')
    await buttonByText(wrapper, '确认删除').trigger('click')
    await flushPromises()

    expect(deleteMock).toHaveBeenCalledWith(1)
    expect(listMock).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('explains the enabled channel limit', async () => {
    listMock.mockResolvedValue(page([]))
    createMock.mockRejectedValue(
      new ApiError('at most 3 notification channels may be enabled', {
        code: 'CHANNEL_LIMIT_EXCEEDED',
        status: 409,
      }),
    )

    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('#channel-name').setValue('第四个')
    await wrapper.find('#channel-credential').setValue('tok-9999')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('最多只能启用 3 个通知渠道')

    wrapper.unmount()
  })
})
