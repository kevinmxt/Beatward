<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { resetPassword } from '../auth/session'
import { resetPasswordErrorMessage } from '../lib/authError'

const MIN_PASSWORD_LENGTH = 10
const MAX_PASSWORD_LENGTH = 32

const router = useRouter()
const resetCode = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const errorMessage = ref<string | null>(null)

function validate(): string | null {
  if (!resetCode.value.trim()) {
    return '请输入重置码'
  }
  if (newPassword.value.length < MIN_PASSWORD_LENGTH || newPassword.value.length > MAX_PASSWORD_LENGTH) {
    return `新密码长度需为 ${MIN_PASSWORD_LENGTH}–${MAX_PASSWORD_LENGTH} 个字符`
  }
  if (confirmPassword.value !== newPassword.value) {
    return '两次输入的新密码不一致'
  }
  return null
}

async function submit(): Promise<void> {
  errorMessage.value = null
  const validation = validate()
  if (validation) {
    errorMessage.value = validation
    return
  }
  submitting.value = true
  try {
    await resetPassword({
      resetCode: resetCode.value.trim(),
      newPassword: newPassword.value,
    })
  } catch (cause) {
    errorMessage.value = resetPasswordErrorMessage(cause)
    return
  } finally {
    submitting.value = false
  }
  await router.replace({ path: '/login', query: { reset: '1' } })
}
</script>

<template>
  <section class="auth-page">
    <div class="auth-panel">
      <h1>重置密码</h1>
      <p class="muted">请输入管理员提供的一次性重置码，并设置新密码。</p>

      <form class="auth-form" @submit.prevent="submit">
        <div class="form-field">
          <label for="reset-code">重置码</label>
          <input
            id="reset-code"
            v-model="resetCode"
            type="text"
            autocomplete="off"
            :disabled="submitting"
          />
        </div>
        <div class="form-field">
          <label for="reset-new-password">新密码</label>
          <input
            id="reset-new-password"
            v-model="newPassword"
            type="password"
            autocomplete="new-password"
            :disabled="submitting"
          />
          <span class="field-hint">{{ MIN_PASSWORD_LENGTH }}–{{ MAX_PASSWORD_LENGTH }} 个字符</span>
        </div>
        <div class="form-field">
          <label for="reset-confirm-password">确认新密码</label>
          <input
            id="reset-confirm-password"
            v-model="confirmPassword"
            type="password"
            autocomplete="new-password"
            :disabled="submitting"
          />
        </div>

        <p v-if="errorMessage" class="error-panel" role="alert">{{ errorMessage }}</p>

        <button class="button button-primary" type="submit" :disabled="submitting">
          {{ submitting ? '重置中…' : '重置密码' }}
        </button>
      </form>

      <p class="auth-links">
        <RouterLink to="/login">返回登录</RouterLink>
      </p>
    </div>
  </section>
</template>
