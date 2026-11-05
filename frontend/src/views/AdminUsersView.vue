<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError, generateResetCode, listUsers, updateUserStatus } from '../api/client'
import type { AdminUser } from '../api/types'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import PaginationControls from '../components/PaginationControls.vue'
import ServerErrorPanel from '../components/ServerErrorPanel.vue'
import { toErrorDetails, type ErrorDetails } from '../lib/apiError'
import { formatAbsoluteTime } from '../lib/format'

const PAGE_SIZE = 20

const users = ref<AdminUser[]>([])
const page = ref(0)
const totalElements = ref(0)
const loading = ref(true)
const error = ref<ErrorDetails | null>(null)
const actionBusyId = ref<number | null>(null)

const pendingDisable = ref<AdminUser | null>(null)
const generatedCode = ref<{ username: string; code: string; expiresAt: string } | null>(null)

async function load(targetPage = page.value, initial = false): Promise<void> {
  if (initial || targetPage !== page.value) {
    loading.value = true
  }
  error.value = null
  try {
    const result = await listUsers(targetPage, PAGE_SIZE)
    users.value = result.items
    page.value = result.page
    totalElements.value = result.totalElements
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

function requestDisable(user: AdminUser): void {
  pendingDisable.value = user
}

async function confirmDisable(): Promise<void> {
  const user = pendingDisable.value
  if (!user || actionBusyId.value !== null) {
    return
  }
  actionBusyId.value = user.id
  error.value = null
  try {
    await updateUserStatus(user.id, 'DISABLED')
    await load(page.value)
  } catch (cause) {
    error.value = statusError(cause)
  } finally {
    pendingDisable.value = null
    actionBusyId.value = null
  }
}

async function enableUser(user: AdminUser): Promise<void> {
  actionBusyId.value = user.id
  error.value = null
  try {
    await updateUserStatus(user.id, 'ACTIVE')
    await load(page.value)
  } catch (cause) {
    error.value = statusError(cause)
  } finally {
    actionBusyId.value = null
  }
}

async function issueResetCode(user: AdminUser): Promise<void> {
  actionBusyId.value = user.id
  error.value = null
  try {
    const created = await generateResetCode(user.id)
    generatedCode.value = {
      username: user.username,
      code: created.code,
      expiresAt: created.expiresAt,
    }
  } catch (cause) {
    error.value = toErrorDetails(cause)
  } finally {
    actionBusyId.value = null
  }
}

function statusError(cause: unknown): ErrorDetails {
  if (cause instanceof ApiError && cause.code === 'LAST_ACTIVE_ADMIN') {
    return {
      code: cause.code,
      message: '至少需要保留一个启用的管理员',
      traceId: cause.traceId,
    }
  }
  return toErrorDetails(cause)
}

function roleLabel(role: AdminUser['role']): string {
  return role === 'ADMIN' ? '管理员' : '用户'
}

function statusLabel(status: AdminUser['status']): string {
  return status === 'ACTIVE' ? '启用' : '已禁用'
}

onMounted(() => {
  void load(0, true)
})
</script>

<template>
  <section>
    <header class="page-header">
      <div>
        <h1>用户管理</h1>
        <p class="muted">共 {{ totalElements }} 个账号</p>
      </div>
      <div class="page-actions">
        <button class="button" type="button" :disabled="loading" @click="refresh">
          {{ loading ? '加载中…' : '刷新' }}
        </button>
      </div>
    </header>

    <ServerErrorPanel v-if="error" :details="error" />

    <div v-if="generatedCode" class="notice" role="status">
      <p>
        {{ generatedCode.username }} 的一次性重置码（只显示一次，请立即转交）：
      </p>
      <p class="mono">{{ generatedCode.code }}</p>
      <p class="muted">有效期至 {{ formatAbsoluteTime(generatedCode.expiresAt) }}</p>
    </div>

    <p v-if="loading" class="muted">加载中…</p>
    <div v-else-if="users.length === 0 && !error" class="empty-state">暂无账号</div>

    <template v-else-if="users.length > 0">
      <table class="data-table">
        <thead>
          <tr>
            <th>用户名</th>
            <th>角色</th>
            <th>状态</th>
            <th>监控数量</th>
            <th>最后登录</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.id">
            <td>{{ user.username }}</td>
            <td>
              <span class="type-badge">{{ roleLabel(user.role) }}</span>
            </td>
            <td>
              <span
                class="status-badge"
                :class="user.status === 'ACTIVE' ? 'status-up' : 'status-disabled'"
              >
                {{ statusLabel(user.status) }}
              </span>
            </td>
            <td class="mono">{{ user.monitorCount }}</td>
            <td>{{ formatAbsoluteTime(user.lastLoginAt) }}</td>
            <td>{{ formatAbsoluteTime(user.createdAt) }}</td>
            <td class="row-actions">
              <button
                v-if="user.status === 'ACTIVE'"
                class="button button-danger-outline"
                type="button"
                :disabled="actionBusyId === user.id"
                @click="requestDisable(user)"
              >
                禁用
              </button>
              <button
                v-else
                class="button"
                type="button"
                :disabled="actionBusyId === user.id"
                @click="enableUser(user)"
              >
                启用
              </button>
              <button
                class="button"
                type="button"
                :disabled="actionBusyId === user.id"
                @click="issueResetCode(user)"
              >
                生成重置码
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
      :open="pendingDisable !== null"
      title="禁用账号"
      :message="`禁用「${pendingDisable?.username ?? ''}」后，该账号的全部会话会立即失效，且不再执行探测与告警。`"
      confirm-label="确认禁用"
      :busy="pendingDisable !== null && actionBusyId === pendingDisable.id"
      @confirm="confirmDisable"
      @cancel="pendingDisable = null"
    />
  </section>
</template>
