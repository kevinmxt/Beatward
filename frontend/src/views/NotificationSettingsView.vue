<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
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
  NotificationChannelType,
  Usage,
} from '../api/types'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import PaginationControls from '../components/PaginationControls.vue'
import ServerErrorPanel from '../components/ServerErrorPanel.vue'
import { toErrorDetails, type ErrorDetails } from '../lib/apiError'
import { formatAbsoluteTime } from '../lib/format'

const PAGE_SIZE = 20

const TYPE_LABELS: Record<NotificationChannelType, string> = {
  PUSHPLUS: '推送加（微信）',
  XIZHI: '息知（微信备用）',
  EMAIL: '邮件',
}

const CREDENTIAL_FIELDS: Record<NotificationChannelType, string> = {
  PUSHPLUS: 'token',
  XIZHI: 'key',
  EMAIL: 'email',
}

const CREDENTIAL_LABELS: Record<NotificationChannelType, string> = {
  PUSHPLUS: 'PushPlus Token',
  XIZHI: '息知 Key',
  EMAIL: '收件邮箱',
}

const channels = ref<NotificationChannel[]>([])
const page = ref(0)
const totalElements = ref(0)
const loading = ref(true)
const error = ref<ErrorDetails | null>(null)
const actionBusyId = ref<number | null>(null)

const editingId = ref<number | null>(null)
const type = ref<NotificationChannelType>('PUSHPLUS')
const name = ref('')
const credential = ref('')
const enabled = ref(true)
const formError = ref<string | null>(null)
const saving = ref(false)

const testResult = ref<{
  channelName: string
  result: NotificationChannelTestResult
} | null>(null)
const pendingDelete = ref<NotificationChannel | null>(null)
const usage = ref<Usage | null>(null)

const credentialLabel = computed(() => CREDENTIAL_LABELS[type.value])
const credentialInputType = computed(() =>
  type.value === 'EMAIL' ? 'email' : 'password',
)

async function loadUsage(): Promise<void> {
  try {
    usage.value = await getUsage()
  } catch {
    // Keep the last known usage; the channel list remains usable.
  }
}

async function load(targetPage = page.value, initial = false): Promise<void> {
  if (initial || targetPage !== page.value) {
    loading.value = true
  }
  error.value = null
  try {
    const result = await listNotificationChannels(targetPage, PAGE_SIZE)
    channels.value = result.items
    page.value = result.page
    totalElements.value = result.totalElements
    void loadUsage()
  } catch (cause) {
    error.value = toErrorDetails(cause)
  } finally {
    loading.value = false
  }
}

function refresh(): void {
  void load(page.value)
}

function goToPage(target: number): void {
  void load(target)
}

function resetForm(): void {
  editingId.value = null
  type.value = 'PUSHPLUS'
  name.value = ''
  credential.value = ''
  enabled.value = true
  formError.value = null
}

function startEdit(channel: NotificationChannel): void {
  editingId.value = channel.id
  type.value = channel.type
  name.value = channel.name
  credential.value = ''
  enabled.value = channel.enabled
  formError.value = null
  testResult.value = null
}

function validate(): string | null {
  if (!name.value.trim()) {
    return '请输入渠道名称'
  }
  if (editingId.value === null && !credential.value.trim()) {
    return `请输入${credentialLabel.value}`
  }
  if (type.value === 'EMAIL' && credential.value.trim() && !credential.value.includes('@')) {
    return '请输入有效的邮箱地址'
  }
  return null
}

function configPayload(): Record<string, string> | undefined {
  const value = credential.value.trim()
  if (!value) {
    return undefined
  }
  return { [CREDENTIAL_FIELDS[type.value]]: value }
}

function friendlyError(cause: unknown): ErrorDetails {
  if (cause instanceof ApiError && cause.code === 'CHANNEL_LIMIT_EXCEEDED') {
    return {
      code: cause.code,
      message: '最多只能启用 3 个通知渠道，请先禁用或删除其他渠道',
      traceId: cause.traceId,
    }
  }
  if (cause instanceof ApiError && cause.status === 429) {
    return {
      code: cause.code,
      message: '操作过于频繁，请稍后再试',
      traceId: cause.traceId,
    }
  }
  return toErrorDetails(cause)
}

async function submit(): Promise<void> {
  formError.value = null
  error.value = null
  const validation = validate()
  formError.value = validation
  if (validation) {
    return
  }

  saving.value = true
  const config = configPayload()
  try {
    if (editingId.value === null) {
      await createNotificationChannel({
        type: type.value,
        name: name.value.trim(),
        enabled: enabled.value,
        config: config ?? {},
      })
    } else {
      await updateNotificationChannel(editingId.value, {
        name: name.value.trim(),
        enabled: enabled.value,
        ...(config ? { config } : {}),
      })
    }
    resetForm()
    testResult.value = null
    await load(0, true)
  } catch (cause) {
    error.value = friendlyError(cause)
  } finally {
    saving.value = false
  }
}

async function toggle(channel: NotificationChannel): Promise<void> {
  actionBusyId.value = channel.id
  error.value = null
  try {
    await updateNotificationChannel(channel.id, {
      name: channel.name,
      enabled: !channel.enabled,
    })
    await load(page.value)
  } catch (cause) {
    error.value = friendlyError(cause)
  } finally {
    actionBusyId.value = null
  }
}

async function test(channel: NotificationChannel): Promise<void> {
  actionBusyId.value = channel.id
  error.value = null
  testResult.value = null
  try {
    const result = await testNotificationChannel(channel.id)
    testResult.value = { channelName: channel.name, result }
    await load(page.value)
  } catch (cause) {
    error.value = friendlyError(cause)
  } finally {
    actionBusyId.value = null
  }
}

function requestDelete(channel: NotificationChannel): void {
  pendingDelete.value = channel
}

async function confirmDelete(): Promise<void> {
  const channel = pendingDelete.value
  if (!channel || actionBusyId.value !== null) {
    return
  }
  actionBusyId.value = channel.id
  error.value = null
  try {
    await deleteNotificationChannel(channel.id)
    pendingDelete.value = null
    testResult.value = null
    await load(page.value)
  } catch (cause) {
    error.value = toErrorDetails(cause)
  } finally {
    actionBusyId.value = null
  }
}

function typeLabel(channelType: NotificationChannelType): string {
  return TYPE_LABELS[channelType] ?? channelType
}

function testStatusLabel(channel: NotificationChannel): string {
  if (!channel.lastTestStatus) {
    return '未测试'
  }
  return channel.lastTestStatus === 'SUCCESS' ? '成功' : '失败'
}

onMounted(() => {
  void load(0, true)
})
</script>

<template>
  <section>
    <header class="page-header">
      <div>
        <h1>通知渠道</h1>
        <p class="muted">
          共 {{ totalElements }} 个渠道<span v-if="usage"> · 已启用 {{ usage.enabledChannelCount }} / {{ usage.channelLimit }}</span>
          <RouterLink to="/settings">返回设置</RouterLink>
        </p>
      </div>
      <div class="page-actions">
        <button class="button" type="button" :disabled="loading" @click="refresh">
          {{ loading ? '加载中…' : '刷新' }}
        </button>
      </div>
    </header>

    <ServerErrorPanel v-if="error" :details="error" />

    <div v-if="testResult" class="notice" role="status">
      <p>
        「{{ testResult.channelName }}」测试
        {{ testResult.result.status === 'SUCCESS' ? '成功' : '失败' }}
      </p>
      <p v-if="testResult.result.message" class="muted">{{ testResult.result.message }}</p>
    </div>

    <form class="form-panel" @submit.prevent="submit">
      <h2>{{ editingId === null ? '添加通知渠道' : '编辑通知渠道' }}</h2>
      <div class="form-grid">
        <div class="form-field">
          <label for="channel-type">类型</label>
          <select id="channel-type" v-model="type" :disabled="saving || editingId !== null">
            <option value="PUSHPLUS">推送加（微信）</option>
            <option value="XIZHI">息知（微信备用）</option>
            <option value="EMAIL">邮件</option>
          </select>
        </div>
        <div class="form-field">
          <label for="channel-name">名称</label>
          <input
            id="channel-name"
            v-model="name"
            type="text"
            maxlength="100"
            :disabled="saving"
          />
        </div>
        <div class="form-field full">
          <label for="channel-credential">{{ credentialLabel }}</label>
          <input
            id="channel-credential"
            v-model="credential"
            :type="credentialInputType"
            autocomplete="off"
            :placeholder="editingId === null ? '' : '留空表示保持原凭据不变'"
            :disabled="saving"
          />
          <span class="field-hint">
            {{
              type === 'PUSHPLUS'
                ? '在推送加公众号获取 token，只保存加密密文'
                : type === 'XIZHI'
                  ? '在息知后台获取 Key，只保存加密密文'
                  : '只填写收件邮箱，发信由管理员配置的全局 SMTP 完成'
            }}
          </span>
        </div>
        <div class="form-field form-checkbox">
          <label>
            <input v-model="enabled" type="checkbox" :disabled="saving" />
            启用该渠道
          </label>
        </div>
      </div>

      <p v-if="formError" class="field-error" role="alert">{{ formError }}</p>
      <div class="form-actions">
        <button class="button button-primary" type="submit" :disabled="saving">
          {{ saving ? '保存中…' : editingId === null ? '添加渠道' : '保存修改' }}
        </button>
        <button
          v-if="editingId !== null"
          class="button"
          type="button"
          :disabled="saving"
          @click="resetForm"
        >
          取消
        </button>
      </div>
    </form>

    <p v-if="loading" class="muted">加载中…</p>
    <div v-else-if="channels.length === 0 && !error" class="empty-state">
      暂无通知渠道
    </div>

    <template v-else-if="channels.length > 0">
      <table class="data-table">
        <thead>
          <tr>
            <th>名称</th>
            <th>类型</th>
            <th>凭据</th>
            <th>状态</th>
            <th>最近测试</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="channel in channels" :key="channel.id">
            <td>{{ channel.name }}</td>
            <td>{{ typeLabel(channel.type) }}</td>
            <td class="mono">{{ channel.maskedConfig }}</td>
            <td>
              <span
                class="status-badge"
                :class="channel.enabled ? 'status-up' : 'status-disabled'"
              >
                {{ channel.enabled ? '启用' : '已禁用' }}
              </span>
            </td>
            <td>
              <span>{{ testStatusLabel(channel) }}</span>
              <span class="status-note muted">{{ formatAbsoluteTime(channel.lastTestAt) }}</span>
              <span v-if="channel.lastTestError" class="status-note field-error">
                {{ channel.lastTestError }}
              </span>
            </td>
            <td class="row-actions">
              <button
                class="button"
                type="button"
                :disabled="actionBusyId === channel.id"
                @click="test(channel)"
              >
                测试
              </button>
              <button
                class="button"
                type="button"
                :disabled="actionBusyId === channel.id"
                @click="startEdit(channel)"
              >
                编辑
              </button>
              <button
                class="button"
                type="button"
                :disabled="actionBusyId === channel.id"
                @click="toggle(channel)"
              >
                {{ channel.enabled ? '禁用' : '启用' }}
              </button>
              <button
                class="button button-danger-outline"
                type="button"
                :disabled="actionBusyId === channel.id"
                @click="requestDelete(channel)"
              >
                删除
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <PaginationControls
        :page="page"
        :size="PAGE_SIZE"
        :total-elements="totalElements"
        :disabled="loading"
        @change="goToPage"
      />
    </template>

    <ConfirmDialog
      :open="pendingDelete !== null"
      title="删除通知渠道"
      :message="`删除「${pendingDelete?.name ?? ''}」后，该渠道不再接收任何告警。`"
      confirm-label="确认删除"
      :busy="pendingDelete !== null && actionBusyId === pendingDelete.id"
      @confirm="confirmDelete"
      @cancel="pendingDelete = null"
    />
  </section>
</template>
