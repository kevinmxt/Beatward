import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, getUsage } from '../api/client'
import type { Usage } from '../api/types'
import { authenticateForTest } from '../test/auth'
import GettingStartedView from './GettingStartedView.vue'

vi.mock('../api/client', async () => {
  const actual = await vi.importActual<typeof import('../api/client')>('../api/client')
  return {
    ...actual,
    getUsage: vi.fn(),
  }
})

const usageMock = vi.mocked(getUsage)

const EmptyView = { template: '<div />' }

function usage(overrides: Partial<Usage> = {}): Usage {
  return {
    monitorCount: 0,
    monitorLimit: 20,
    enabledChannelCount: 0,
    channelLimit: 3,
    hasSuccessfulChannelTest: false,
    ...overrides,
  }
}

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/getting-started', component: GettingStartedView },
      { path: '/monitors', component: EmptyView },
      { path: '/monitors/new', component: EmptyView },
      { path: '/settings/notifications', component: EmptyView },
    ],
  })
  await router.push('/getting-started')
  await router.isReady()
  const wrapper = mount(GettingStartedView, { global: { plugins: [router] } })
  await flushPromises()
  return { router, wrapper }
}

describe('GettingStartedView', () => {
  beforeEach(() => {
    authenticateForTest('alice', 'csrf-1', 'USER')
    usageMock.mockReset()
  })

  it('shows usage and completed steps for an activated account', async () => {
    usageMock.mockResolvedValue(
      usage({ monitorCount: 2, enabledChannelCount: 1, hasSuccessfulChannelTest: true }),
    )

    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('监控 2 / 20')
    expect(wrapper.text()).toContain('启用渠道 1 / 3')
    expect(wrapper.text()).toContain('三个步骤都已完成')
    expect(wrapper.text()).toContain('完成引导，进入监控列表')

    wrapper.unmount()
  })

  it('shows pending steps and usage limits before activation', async () => {
    usageMock.mockResolvedValue(usage())

    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('监控 0 / 20')
    expect(wrapper.text()).toContain('启用渠道 0 / 3')
    expect(wrapper.findAll('.status-badge').filter((badge) => badge.text() === '待完成'))
      .toHaveLength(3)
    expect(wrapper.text()).not.toContain('三个步骤都已完成')

    wrapper.unmount()
  })

  it('treats a configured channel without a successful test as pending', async () => {
    usageMock.mockResolvedValue(
      usage({ enabledChannelCount: 1, hasSuccessfulChannelTest: false }),
    )

    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('启用渠道 1 / 3')
    expect(wrapper.text()).toContain('待完成')
    expect(wrapper.text()).not.toContain('三个步骤都已完成')

    wrapper.unmount()
  })

  it('keeps the steps and retries when usage cannot be loaded', async () => {
    usageMock
      .mockRejectedValueOnce(
        new ApiError('usage unavailable', {
          code: 'INTERNAL_ERROR',
          status: 500,
          traceId: 'trace-usage',
        }),
      )
      .mockResolvedValueOnce(usage({ monitorCount: 1 }))

    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('INTERNAL_ERROR')
    expect(wrapper.text()).toContain('trace-usage')
    expect(wrapper.findAll('.getting-started-steps li')).toHaveLength(3)

    const retry = wrapper.findAll('button').find((button) => button.text() === '重试')
    await retry?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('监控 1 / 20')
    expect(wrapper.text()).not.toContain('INTERNAL_ERROR')

    wrapper.unmount()
  })

  it('navigates to the monitor list', async () => {
    usageMock.mockResolvedValue(usage())

    const { router, wrapper } = await mountView()
    const button = wrapper.findAll('button').find((item) => item.text() === '进入监控列表')
    await button?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/monitors')

    wrapper.unmount()
  })
})
