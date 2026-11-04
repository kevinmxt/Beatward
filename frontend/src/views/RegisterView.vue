<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../auth/session'
import { registrationErrorMessage } from '../lib/authError'

const MIN_PASSWORD_LENGTH = 12
const MAX_PASSWORD_LENGTH = 128

const router = useRouter()
const inviteCode = ref('')
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const errorMessage = ref<string | null>(null)

function validate(): string | null {
  if (!inviteCode.value.trim()) {
    return '请输入邀请码'
  }
  if (!username.value.trim()) {
    return '请输入用户名'
  }
  if (password.value.length < MIN_PASSWORD_LENGTH || password.value.length > MAX_PASSWORD_LENGTH) {
    return `密码长度需为 ${MIN_PASSWORD_LENGTH}–${MAX_PASSWORD_LENGTH} 个字符`
  }
  if (confirmPassword.value !== password.value) {
    return '两次输入的密码不一致'
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
    await register({
      inviteCode: inviteCode.value.trim(),
      username: username.value.trim(),
      password: password.value,
    })
  } catch (cause) {
    errorMessage.value = registrationErrorMessage(cause)
    return
  } finally {
    submitting.value = false
  }
  await router.replace('/getting-started')
}
</script>

<template>
  <section class="auth-page">
    <div class="auth-panel">
      <h1>注册 Beatward</h1>
      <p class="muted">Beatward 目前为邀请制试用，请向管理员获取邀请码。</p>

      <form class="auth-form" @submit.prevent="submit">
        <div class="form-field">
          <label for="register-invite">邀请码</label>
          <input
            id="register-invite"
            v-model="inviteCode"
            type="text"
            autocomplete="off"
            :disabled="submitting"
          />
        </div>
        <div class="form-field">
          <label for="register-username">用户名</label>
          <input
            id="register-username"
            v-model="username"
            type="text"
            autocomplete="username"
            :disabled="submitting"
          />
        </div>
        <div class="form-field">
          <label for="register-password">密码</label>
          <input
            id="register-password"
            v-model="password"
            type="password"
            autocomplete="new-password"
            :disabled="submitting"
          />
          <span class="field-hint">{{ MIN_PASSWORD_LENGTH }}–{{ MAX_PASSWORD_LENGTH }} 个字符</span>
        </div>
        <div class="form-field">
          <label for="register-confirm">确认密码</label>
          <input
            id="register-confirm"
            v-model="confirmPassword"
            type="password"
            autocomplete="new-password"
            :disabled="submitting"
          />
        </div>

        <p v-if="errorMessage" class="error-panel" role="alert">{{ errorMessage }}</p>

        <button class="button button-primary" type="submit" :disabled="submitting">
          {{ submitting ? '注册中…' : '注册' }}
        </button>
      </form>

      <p class="auth-links">
        已有账号？<RouterLink to="/login">登录</RouterLink>
      </p>
      <p class="auth-links">
        忘记密码？<RouterLink to="/reset-password">使用重置码</RouterLink>
      </p>
    </div>
  </section>
</template>
