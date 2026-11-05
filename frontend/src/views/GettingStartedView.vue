<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getUsage } from '../api/client'
import type { Usage } from '../api/types'
import { authState } from '../auth/session'
import ServerErrorPanel from '../components/ServerErrorPanel.vue'
import { toErrorDetails, type ErrorDetails } from '../lib/apiError'

const router = useRouter()
const usage = ref<Usage | null>(null)
const loading = ref(true)
const error = ref<ErrorDetails | null>(null)

const channelConfigured = computed(() => (usage.value?.enabledChannelCount ?? 0) > 0)
const testCompleted = computed(() => usage.value?.hasSuccessfulChannelTest ?? false)
const monitorCreated = computed(() => (usage.value?.monitorCount ?? 0) > 0)
const finished = computed(
  () => channelConfigured.value && testCompleted.value && monitorCreated.value,
)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    usage.value = await getUsage()
  } catch (cause) {
    error.value = toErrorDetails(cause)
  } finally {
    loading.value = false
  }
}

function finish(): void {
  void router.push('/monitors')
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section>
    <header class="page-header">
      <div>
        <h1>开始使用</h1>
        <p class="muted">欢迎，{{ authState.username }}。按下面的顺序完成初始配置。</p>
        <p v-if="usage" class="getting-started-usage">
          监控 {{ usage.monitorCount }} / {{ usage.monitorLimit }} · 启用渠道
          {{ usage.enabledChannelCount }} / {{ usage.channelLimit }}
        </p>
      </div>
      <div class="page-actions">
        <button class="button button-primary" type="button" @click="finish">
          {{ finished ? '完成引导，进入监控列表' : '进入监控列表' }}
        </button>
      </div>
    </header>

    <p v-if="loading" class="muted">加载中…</p>
    <template v-else>
      <template v-if="error">
        <ServerErrorPanel :details="error" />
        <div class="page-actions">
          <button class="button" type="button" @click="load">重试</button>
        </div>
      </template>

      <p v-if="finished" class="notice" role="status">
        三个步骤都已完成，可以开始使用 Beatward 了。
      </p>

      <ol class="getting-started-steps">
        <li>
          <div class="step-heading">
            <h2>1. 添加通知渠道</h2>
            <span
              class="status-badge"
              :class="channelConfigured ? 'status-up' : 'status-unknown'"
            >
              {{ channelConfigured ? '已完成' : '待完成' }}
            </span>
          </div>
          <p class="muted">
            在 <RouterLink to="/settings/notifications">通知渠道</RouterLink>
            配置推送加、息知或邮箱，确保故障时能收到告警。
          </p>
        </li>
        <li>
          <div class="step-heading">
            <h2>2. 发送测试通知</h2>
            <span
              class="status-badge"
              :class="testCompleted ? 'status-up' : 'status-unknown'"
            >
              {{ testCompleted ? '已完成' : '待完成' }}
            </span>
          </div>
          <p class="muted">
            在 <RouterLink to="/settings/notifications">通知渠道</RouterLink>
            点击「测试」，确认微信或邮箱能收到消息。
          </p>
        </li>
        <li>
          <div class="step-heading">
            <h2>3. 添加首个监控</h2>
            <span
              class="status-badge"
              :class="monitorCreated ? 'status-up' : 'status-unknown'"
            >
              {{ monitorCreated ? '已完成' : '待完成' }}
            </span>
          </div>
          <p class="muted">
            前往 <RouterLink to="/monitors/new">新建监控项</RouterLink>
            ，创建 HTTP 或 Heartbeat 监控。
          </p>
        </li>
      </ol>
    </template>
  </section>
</template>
