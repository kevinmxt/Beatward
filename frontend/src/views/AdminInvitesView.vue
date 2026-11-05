<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { createInviteCode, listInviteCodes, revokeInviteCode } from '../api/client'
import type { InviteCode } from '../api/types'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import PaginationControls from '../components/PaginationControls.vue'
import ServerErrorPanel from '../components/ServerErrorPanel.vue'
import { toErrorDetails, type ErrorDetails } from '../lib/apiError'
import { formatAbsoluteTime } from '../lib/format'

const PAGE_SIZE = 20
const DEFAULT_MAX_USES = '1'
const DEFAULT_EXPIRES_HOURS = '168'

const invites = ref<InviteCode[]>([])
const page = ref(0)
const totalElements = ref(0)
const loading = ref(true)
const error = ref<ErrorDetails | null>(null)

const label = ref('')
const maxUses = ref(DEFAULT_MAX_USES)
const expiresInHours = ref(DEFAULT_EXPIRES_HOURS)
const formError = ref<string | null>(null)
const creating = ref(false)
const createdCode = ref<string | null>(null)

const pendingRevoke = ref<InviteCode | null>(null)
const revoking = ref(false)

async function load(targetPage = page.value, initial = false): Promise<void> {
  if (initial || targetPage !== page.value) {
    loading.value = true
  }
  error.value = null
  try {
    const result = await listInviteCodes(targetPage, PAGE_SIZE)
    invites.value = result.items
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

function parseNumber(value: string, fallback: number): number {
  const parsed = Number(value)
  return Number.isInteger(parsed) ? parsed : fallback
}

function validate(): string | null {
  const uses = parseNumber(maxUses.value, 0)
  if (uses < 1 || uses > 100) {
    return '最大注册数需为 1–100'
  }
  const hours = parseNumber(expiresInHours.value, 0)
  if (hours < 1 || hours > 720) {
    return '有效期需为 1–720 小时'
  }
  if (label.value.trim().length > 200) {
    return '备注不能超过 200 个字符'
  }
  return null
}

async function submit(): Promise<void> {
  formError.value = null
  error.value = null
  const validation = validate()
  formError.value = validation
  if (validation) {
    return
  }

  creating.value = true
  try {
    const created = await createInviteCode({
      label: label.value.trim() || null,
      maxUses: parseNumber(maxUses.value, 1),
      expiresInHours: parseNumber(expiresInHours.value, 168),
    })
    createdCode.value = created.code
    label.value = ''
    maxUses.value = DEFAULT_MAX_USES
    expiresInHours.value = DEFAULT_EXPIRES_HOURS
    await load(0, true)
  } catch (cause) {
    error.value = toErrorDetails(cause)
  } finally {
    creating.value = false
  }
}

function requestRevoke(invite: InviteCode): void {
  pendingRevoke.value = invite
}

async function confirmRevoke(): Promise<void> {
  const invite = pendingRevoke.value
  if (!invite || revoking.value) {
    return
  }
  revoking.value = true
  error.value = null
  try {
    await revokeInviteCode(invite.id)
    pendingRevoke.value = null
    await load(page.value)
  } catch (cause) {
    error.value = toErrorDetails(cause)
  } finally {
    revoking.value = false
  }
}

type InviteStatus = 'available' | 'expired' | 'exhausted' | 'revoked'

function inviteStatus(invite: InviteCode): InviteStatus {
  if (invite.revokedAt) {
    return 'revoked'
  }
  if (new Date(invite.expiresAt).getTime() <= Date.now()) {
    return 'expired'
  }
  if (invite.usedCount >= invite.maxUses) {
    return 'exhausted'
  }
  return 'available'
}

function statusLabel(invite: InviteCode): string {
  switch (inviteStatus(invite)) {
    case 'revoked':
      return '已撤销'
    case 'expired':
      return '已过期'
    case 'exhausted':
      return '已用尽'
    default:
      return '可用'
  }
}

function statusClass(invite: InviteCode): string {
  switch (inviteStatus(invite)) {
    case 'available':
      return 'status-up'
    case 'expired':
    case 'exhausted':
      return 'status-unknown'
    default:
      return 'status-disabled'
  }
}

onMounted(() => {
  void load(0, true)
})
</script>

<template>
  <section>
    <header class="page-header">
      <div>
        <h1>邀请码</h1>
        <p class="muted">共 {{ totalElements }} 个邀请码</p>
      </div>
      <div class="page-actions">
        <button class="button" type="button" :disabled="loading" @click="refresh">
          {{ loading ? '加载中…' : '刷新' }}
        </button>
      </div>
    </header>

    <ServerErrorPanel v-if="error" :details="error" />

    <form class="form-panel" @submit.prevent="submit">
      <h2>创建邀请码</h2>
      <div class="form-grid">
        <div class="form-field">
          <label for="invite-label">备注</label>
          <input
            id="invite-label"
            v-model="label"
            type="text"
            maxlength="200"
            :disabled="creating"
          />
        </div>
        <div class="form-field">
          <label for="invite-max-uses">最大注册数</label>
          <input
            id="invite-max-uses"
            v-model="maxUses"
            type="number"
            min="1"
            max="100"
            :disabled="creating"
          />
        </div>
        <div class="form-field">
          <label for="invite-expires">有效期（小时）</label>
          <input
            id="invite-expires"
            v-model="expiresInHours"
            type="number"
            min="1"
            max="720"
            :disabled="creating"
          />
        </div>
      </div>
      <p v-if="formError" class="field-error" role="alert">{{ formError }}</p>
      <div class="form-actions">
        <button class="button button-primary" type="submit" :disabled="creating">
          {{ creating ? '创建中…' : '创建邀请码' }}
        </button>
      </div>
    </form>

    <div v-if="createdCode" class="notice" role="status">
      <p>新邀请码（只显示一次，请立即保存）：</p>
      <p class="mono">{{ createdCode }}</p>
    </div>

    <p v-if="loading" class="muted">加载中…</p>
    <div v-else-if="invites.length === 0 && !error" class="empty-state">暂无邀请码</div>

    <template v-else-if="invites.length > 0">
      <table class="data-table">
        <thead>
          <tr>
            <th>备注</th>
            <th>使用情况</th>
            <th>状态</th>
            <th>过期时间</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="invite in invites" :key="invite.id">
            <td>{{ invite.label ?? '—' }}</td>
            <td class="mono">{{ invite.usedCount }} / {{ invite.maxUses }}</td>
            <td>
              <span class="status-badge" :class="statusClass(invite)">
                {{ statusLabel(invite) }}
              </span>
            </td>
            <td>{{ formatAbsoluteTime(invite.expiresAt) }}</td>
            <td>{{ formatAbsoluteTime(invite.createdAt) }}</td>
            <td>
              <button
                v-if="!invite.revokedAt"
                class="button button-danger-outline"
                type="button"
                @click="requestRevoke(invite)"
              >
                撤销
              </button>
              <span v-else class="muted">—</span>
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
      :open="pendingRevoke !== null"
      title="撤销邀请码"
      :message="`撤销后「${pendingRevoke?.label ?? '该邀请码'}」将无法再用于注册。`"
      confirm-label="确认撤销"
      :busy="revoking"
      @confirm="confirmRevoke"
      @cancel="pendingRevoke = null"
    />
  </section>
</template>
